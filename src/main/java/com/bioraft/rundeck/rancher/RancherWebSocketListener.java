/*
 * Copyright 2019 BioRAFT, Inc. (http://bioraft.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bioraft.rundeck.rancher;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.dtolabs.rundeck.core.Constants;
import com.dtolabs.rundeck.core.execution.ExecutionListener;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.ByteSource;
import com.google.common.primitives.Bytes;

import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * RancherWebSocketListener connects to Rancher containers.
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2019-12-11
 */
public final class RancherWebSocketListener extends WebSocketListener {

	// Try to use a single HTTP client across methods.
	private OkHttpClient client;

	// URL of the Rancher API end point.
	private String url;

	// The Rancher API access key.
	private String accessKey;

	// The rancher API secret key.
	private String secretKey;

	// The command for the job being run.
	private String[] commandList;

	// A buffer used to accumulate output from the Rancher message stream.
	private StringBuilder output;

	// Docker message frames do not necessarily coincide with Rancher. When a Docker
	// frame is continued in the next Rancher message, this header allows us to
	// decode
	// the rest of the Docker frame.
	private byte[] nextHeader;

	// Log listener from Rundeck.
	private ExecutionListener listener;

	// These are used to reconstruct STDERR since it is lost in the stream from
	// Rancher.
	private static final String STDERR_TOK = "STDERR_6v9ZvwThpU1FtyrlIBf4UIC8";
	private static final int STDERR_TOKLEN = STDERR_TOK.length() + 1;
	private int currentOutputChannel = -1;

	@Override
	public void onMessage(WebSocket webSocket, String text) {
		logDockerStream(webSocket, Bytes.concat(nextHeader, Base64.getDecoder().decode(text)));
	}

	@Override
	public void onMessage(WebSocket webSocket, ByteString bytes) {
		logDockerStream(webSocket, Bytes.concat(nextHeader, bytes.toByteArray()));
	}

	@Override
	public void onClosing(WebSocket webSocket, int code, String reason) {
		this.log(Constants.VERBOSE_LEVEL, reason);
		webSocket.close(code, reason);
	}

	@Override
	public void onFailure(WebSocket webSocket, Throwable t, Response response) {
		this.log(Constants.ERR_LEVEL, t.getMessage());
		t.printStackTrace();
	}

	/**
	 * Runs the overall job step: sends output to a listener; saves PID and exit
	 * status to a temporary file.
	 *
	 * @param url
	 * @param accessKey
	 * @param secretKey
	 * @param command
	 * @param listener
	 * @param temp
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static void runJob(String url, String accessKey, String secretKey, String[] command,
			ExecutionListener listener, String temp, int timeout) throws IOException, InterruptedException {
		String file = " >>" + temp + ".pid; ";
		// Prefix STDERR lines with STDERR_TOK to decode in logging step.
		String job = "( " + String.join(" ", command) + ") 2> >(while read line;do echo \"" + STDERR_TOK
				+ " $line\";done) ;";
		String remote = "printf $$" + file + job + "printf ' %s' $?" + file;
		// Note that bash is required to support adding a prefix token to STDERR.
		String[] cmd = { "bash", "-c", remote };
		new RancherWebSocketListener().runJob(url, accessKey, secretKey, listener, cmd, timeout);
	}

	/**
	 * Get contents of a file from server.
	 *
	 * @param url
	 * @param accessKey
	 * @param secretKey
	 * @param logger
	 * @param file
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static void getFile(String url, String accessKey, String secretKey, StringBuilder logger, String file)
			throws IOException, InterruptedException {
		String[] command = { "cat", file };
		new RancherWebSocketListener().run(url, accessKey, secretKey, logger, command);
	}

	/**
	 * Put text onto a container as the specified file.
	 *
	 * @param url
	 * @param accessKey
	 * @param secretKey
	 * @param logger
	 * @param file
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static void putFile(String url, String accessKey, String secretKey, File file, String destination)
			throws IOException, InterruptedException {
		new RancherWebSocketListener().put(url, accessKey, secretKey, file, destination);
	}

	/**
	 * Runs a command and passes output back to an external listener.
	 * 
	 * Exit status is read after completion from the job's PID file in /tmp.
	 *
	 * @param url
	 * @param accessKey
	 * @param secretKey
	 * @param listener
	 * @param command
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private void runJob(String url, String accessKey, String secretKey, ExecutionListener listener, String[] command,
			int timeout) throws IOException, InterruptedException {
		client = new OkHttpClient.Builder().pingInterval(50, TimeUnit.SECONDS).callTimeout(0, TimeUnit.HOURS).build();

		this.url = url;
		this.accessKey = accessKey;
		this.secretKey = secretKey;
		this.commandList = command;
		this.listener = listener;
		this.nextHeader = new byte[0];

		// Even though we are passing data back to an external listener, we need to
		// buffer
		// the message stream so we can pick out lines that are part of STDERR.
		output = new StringBuilder();

		client.newWebSocket(this.buildRequest(false, true), this);

		// Trigger shutdown of the dispatcher's executor so process exits cleanly.
		client.dispatcher().executorService().shutdown();
		// Any job will terminate after this time. Should be configurable?
		client.dispatcher().executorService().awaitTermination(timeout, TimeUnit.SECONDS);
	}

	/**
	 * Runs a command, capturing output in a StringBuffer injected on invocation.
	 * 
	 * This is used to get the contents of the PID file when the job ends and
	 * determine the exit status.
	 * 
	 * @param url
	 * @param accessKey
	 * @param secretKey
	 * @param output
	 * @param command
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private void run(String url, String accessKey, String secretKey, StringBuilder output, String[] command)
			throws IOException, InterruptedException {
		client = new OkHttpClient.Builder().build();

		this.url = url;
		this.accessKey = accessKey;
		this.secretKey = secretKey;
		this.commandList = command;
		this.output = output;
		this.nextHeader = new byte[0];

		client.newWebSocket(this.buildRequest(false, true), this);

		// Trigger shutdown of the dispatcher's executor so process exits cleanly.
		client.dispatcher().executorService().shutdown();
		client.dispatcher().executorService().awaitTermination(30, TimeUnit.SECONDS);
	}

	/**
	 * Put a file onto the server.
	 * 
	 * Neither STDIN or STDOUT are attached. The file is sent as a payload with the
	 * post command.
	 * 
	 * @param url
	 * @param accessKey
	 * @param secretKey
	 * @param input
	 * @param file
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private void put(String url, String accessKey, String secretKey, File input, String file)
			throws IOException, InterruptedException {
		// Create a random UUID to use as a marker for a HEREDOC and as a temporary file name.
		String marker = UUID.randomUUID().toString();

		Base64.Encoder encoder = Base64.getEncoder();
		String encoded = encoder.encodeToString(Files.readAllBytes(input.toPath()));
		output = new StringBuilder();

		this.url = url;
		this.accessKey = accessKey;
		this.secretKey = secretKey;

		int encodedSize = encoded.length();
		int chunkSize = 5000;
		String redirector = ">";
		for (int i = 0; i < encodedSize; i = i + chunkSize) {
			String text = encoded.substring(i, Math.min(i + chunkSize, encodedSize));
			// The command cats a HEREDOC to the desired file. Note the quote that ensures
			// the contents are not interpreted as shell variables.
			String[] command = { "sh", "-c",
					"cat <<'" + marker + "'" + redirector + "/tmp/" + marker + "\n" + text + "\n" + marker };
			this.runCommand(command, 50);
			redirector = ">>";
		}

		String[] command = { "sh", "-c", "base64 -d /tmp/" + marker + " > " + file + "; rm /tmp/" + marker };
		this.runCommand(command, 1);
	}

	private void runCommand(String[] command, int timeout) throws IOException, InterruptedException {
		client = new OkHttpClient.Builder().build();
		this.commandList = command;
		client.newWebSocket(this.buildRequest(false, false), this);
		client.dispatcher().executorService().shutdown();
		if (timeout > 0) {
			client.dispatcher().executorService().awaitTermination(timeout, TimeUnit.MILLISECONDS);
		}
	}

	/**
	 * Builds the web socket request.
	 *
	 * @return
	 * @throws IOException
	 */
	private Request buildRequest(boolean attachStdin, boolean attachStdout) throws IOException {
		JsonNode token = this.getToken(attachStdin, attachStdout);
		String path = token.path("url").asText() + "?token=" + token.path("token").asText();
		return new Request.Builder().url(path).build();
	}

	/**
	 * Gets the web socket end point and connection token for an execute request.
	 *
	 * @return
	 * @throws IOException
	 */
	private JsonNode getToken(boolean attachStdin, boolean attachStdout) throws IOException {
		HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
		String path = urlBuilder.build().toString();
		String content = this.apiData(attachStdin, attachStdout);
		try {
			RequestBody body = RequestBody.create(MediaType.parse("application/json"), content);
			Request request = new Request.Builder().url(path).post(body)
					.addHeader("Authorization", Credentials.basic(accessKey, secretKey)).build();
			Response response = client.newCall(request).execute();
			ObjectMapper mapper = new ObjectMapper();
			return mapper.readTree(response.body().string());
		} catch (IOException e) {
			System.out.println(e.getMessage());
			throw e;
		}
	}

	/**
	 * Builds JSON string of API data.
	 *
	 * @return
	 * @throws JsonMappingException
	 * @throws JsonProcessingException
	 */
	private String apiData(boolean attachStdin, boolean attachStdout)
			throws JsonMappingException, JsonProcessingException {
		ObjectMapper mapper = new ObjectMapper();
		JsonNode root = mapper.readTree("{}");
		((ObjectNode) root).put("tty", false);
		((ObjectNode) root).put("attachStdin", attachStdin);
		((ObjectNode) root).put("attachStdout", attachStdout);
		ArrayNode command = ((ObjectNode) root).putArray("command");
		for (String atom : commandList) {
			command.add(atom);
		}
		return root.toString();
	}

	/**
	 * Logs a Docker stream passed through Rancher.
	 * 
	 * @param webSocket
	 * @param bytes
	 */
	public void logDockerStream(WebSocket webSocket, byte[] bytes) {
		LogMessage message;
		BufferedReader stringReader;
		try {
			InputStream stream = ByteSource.wrap(bytes).openStream();
			MessageReader reader = new MessageReader(stream);
			while ((message = reader.nextMessage()) != null) {
				// If logging to RunDeck, we send lines beginning with STRDERR_TOK to ERR_LEVEL.
				// To do that, we make a BufferedReader and process it line-by-line in log
				// function.
				if (listener != null) {
					stringReader = new BufferedReader(new StringReader(new String(message.content.array())));
					log(currentOutputChannel, stringReader);
				} else {
					output.append(new String(message.content.array()));
				}
				nextHeader = reader.nextHeader();
			}
			reader.close();
		} catch (IOException e) {
			log(Constants.ERR_LEVEL, e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Read a Buffer line by line and send lines prefixed by STDERR_TOK to the
	 * WARN_LEVEL channel of RunDeck's console.
	 *
	 * @param level
	 * @param message
	 * @throws IOException
	 */
	private void log(int level, BufferedReader stringReader) throws IOException {
		String line;
		while ((line = stringReader.readLine()) != null) {
			if (line.startsWith(STDERR_TOK)) {
				this.log(Constants.WARN_LEVEL, line.substring(STDERR_TOKLEN) + "\n");
			} else {
				this.log(Constants.INFO_LEVEL, line + "\n");
			}
		}
		if (output.length() > 0) {
			listener.log(currentOutputChannel, output.toString());
		}
		output = new StringBuilder();
	}

	/**
	 * Buffer lines sent to RunDeck's logger so they are sent together and not
	 * line-by-line.
	 *
	 * @param level
	 * @param message
	 */
	private void log(int level, String message) {
		if (listener != null) {
			if (currentOutputChannel == -1) {
				currentOutputChannel = level;
			} else if (currentOutputChannel != level) {
				if (output.length() > 0) {
					listener.log(currentOutputChannel, output.toString());
				}
				currentOutputChannel = level;
				output = new StringBuilder();
			}
		}
		output.append(message);
	}

}