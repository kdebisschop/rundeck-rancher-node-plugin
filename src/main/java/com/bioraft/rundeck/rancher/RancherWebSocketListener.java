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
import okhttp3.*;
import okio.ByteString;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.dtolabs.rundeck.core.Constants.ERR_LEVEL;

/**
 * RancherWebSocketListener connects to Rancher containers.
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2019-12-11
 */
public class RancherWebSocketListener extends WebSocketListener {

	// These are used to reconstruct STDERR since it is lost in the stream from
	// Rancher.
	private static final String STDERR_TOK = "STDERR_6v9ZvwThpU1FtyrlIBf4UIC8";
	private static final int STDERR_TOKLEN = STDERR_TOK.length() + 1;
	// Log listener from Rundeck.
	private ExecutionListener listener;
	// A buffer used to accumulate output from the Rancher message stream.
	private StringBuilder output;
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

	// Docker message frames do not necessarily coincide with Rancher. When a Docker
	// frame is continued in the next Rancher message, this header allows us to
	// decode
	// the rest of the Docker frame.
	private byte[] nextHeader;

	private int currentOutputChannel = -1;

	public RancherWebSocketListener() {
	}

	public RancherWebSocketListener(OkHttpClient client) {
		this.client = client;
	}

	public RancherWebSocketListener(ExecutionListener listener, StringBuilder output) {
		this.listener = listener;
		this.output = output;
	}

	/**
	 * Runs the overall job step: sends output to a listener; saves PID and exit
	 * status to a temporary file.
	 *
	 * @param url The URL the listener should use to launch the job.
	 * @param accessKey Rancher credentials AccessKey.
	 * @param secretKey Rancher credentials SecretKey.
	 * @param command The command to run.
	 * @param listener Log listener from Rundeck.
	 * @param temp A unique temporary file for this job (".pid" will be appended to the file name)
	 * @throws IOException When job fails.
	 * @throws InterruptedException When job is interrupted.
	 */
	public static void runJob(String url, String accessKey, String secretKey, String[] command,
			ExecutionListener listener, String temp, int timeout) throws IOException, InterruptedException {
		String[] cmd = remoteCommand(command, temp);
		(new RancherWebSocketListener()).runJob(url, accessKey, secretKey, listener, cmd, timeout);
	}

	/**
	 * Constructs the command that will actually be invoked on the remote server to execute the submitted job.
	 *
	 * @param command The command to run.
	 * @param temp A unique temporary file for this job (".pid" will be appended to the file name)
	 * @return The command vector to be sent to the remote server.
	 */
	private static String[] remoteCommand(String[] command, String temp) {
		String file = " >>" + temp + ".pid; ";
		// Prefix STDERR lines with STDERR_TOK to decode in logging step.
		String job = "( " + String.join(" ", command) + ") 2> >(while read line;do echo \"" + STDERR_TOK
				+ " $line\";done) ;";
		// Note that bash is required to support adding a prefix token to STDERR.
		return new String[]{ "bash", "-c", "printf $$" + file + job + "printf ' %s' $?" + file };
	}

	@Override
	public void onMessage(WebSocket webSocket, String text) {
		logDockerStream(Bytes.concat(nextHeader, Base64.getDecoder().decode(text)));
	}

	@Override
	public void onMessage(WebSocket webSocket, ByteString bytes) {
		logDockerStream(Bytes.concat(nextHeader, bytes.toByteArray()));
	}

	@Override
	public void onClosing(WebSocket webSocket, int code, String reason) {
		this.log(Constants.VERBOSE_LEVEL, reason);
		webSocket.close(code, reason);
	}

	@Override
	public void onFailure(WebSocket webSocket, Throwable t, Response response) {
		this.log(Constants.ERR_LEVEL, t.getMessage());
	}

	public void thisRunJob(String url, String accessKey, String secretKey, String[] command,
						   ExecutionListener listener, String temp, int timeout) throws IOException, InterruptedException {
		String[] cmd = remoteCommand(command, temp);
		this.runJob(url, accessKey, secretKey, listener, cmd, timeout);
	}

	/**
	 * Get contents of a file from server.
	 *
	 * @param url The URL the listener should use to launch the job.
	 * @param accessKey Rancher credentials AccessKey.
	 * @param secretKey Rancher credentials SecretKey.
	 * @param file The file to fetch/cat from the remote container.
	 * @throws IOException When job fails.
	 * @throws InterruptedException When job is interrupted.
	 */
	public String thisGetFile(String url, String accessKey, String secretKey, String file)
			throws IOException, InterruptedException {
		String[] command = { "cat", file };
		StringBuilder stringBuilder = new StringBuilder();
		this.run(url, accessKey, secretKey, stringBuilder, command);
		return stringBuilder.toString();
	}

	/**
	 * Put text onto a container as the specified file.
	 *
	 * @param url The URL the listener should use to launch the job.
	 * @param accessKey Rancher credentials AccessKey.
	 * @param secretKey Rancher credentials SecretKey.
	 * @param file The file to copy.
	 * @param destination Location of target file on specified container.
	 * @throws IOException When job fails.
	 * @throws InterruptedException When job is interrupted.
	 */
	public void putFile(String url, String accessKey, String secretKey, File file, String destination)
			throws IOException, InterruptedException {
		(new RancherWebSocketListener()).put(url, accessKey, secretKey, file, destination);
	}

	/**
	 * Runs a command and passes output back to an external listener.
	 * 
	 * Exit status is read after completion from the job's PID file in /tmp.
	 *
	 * @param url The URL the listener should use to launch the job.
	 * @param accessKey Rancher credentials AccessKey.
	 * @param secretKey Rancher credentials SecretKey.
	 * @param listener Log listener from Rundeck.
	 * @param command The command to run.
	 * @throws IOException When job fails.
	 * @throws InterruptedException When job is interrupted.
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

		client.newWebSocket(this.buildRequest(true), this);

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
	 * @param url The URL the listener should use to launch the job.
	 * @param accessKey Rancher credentials AccessKey.
	 * @param secretKey Rancher credentials SecretKey.
	 * @param output An output buffer used to accumulate results of the command.
	 * @param command The command to run.
	 * @throws IOException When job fails.
	 * @throws InterruptedException When job is interrupted.
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

		client.newWebSocket(this.buildRequest(true), this);

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
	 * @param url The URL the listener should use to launch the job.
	 * @param accessKey Rancher credentials AccessKey.
	 * @param secretKey Rancher credentials SecretKey.
	 * @param input The file to put on the target container.
	 * @param file The name of the destination file on the target container.
	 * @throws IOException When job fails.
	 * @throws InterruptedException When job is interrupted.
	 */
	private void put(String url, String accessKey, String secretKey, File input, String file)
			throws IOException, InterruptedException {
		// Create a random UUID to use as a marker for a HEREDOC and as a temporary file
		// name.
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

	/**
	 * Runs a command on a remote container.
	 *
	 * @param command The command to run.
	 * @param timeout Time to wait before closing unresponsive connections.
	 * @throws IOException When job fails.
	 * @throws InterruptedException When job is interrupted.
	 */
	private void runCommand(String[] command, int timeout) throws IOException, InterruptedException {
		client = new OkHttpClient.Builder().build();
		this.commandList = command;
		client.newWebSocket(this.buildRequest(false), this);
		client.dispatcher().executorService().shutdown();
		if (timeout > 0) {
			client.dispatcher().executorService().awaitTermination(timeout, TimeUnit.MILLISECONDS);
		}
	}

	/**
	 * Builds the web socket request.
	 *
	 * @param attachStdout Should Rancher attach a TTY to StdOut?
	 * @return HTTP Request Object
	 * @throws IOException When connection to the container fails.
	 */
	private Request buildRequest(boolean attachStdout) throws IOException {
		JsonNode token = this.getToken(attachStdout);
		String path = token.path("url").asText() + "?token=" + token.path("token").asText();
		return new Request.Builder().url(path).build();
	}

	/**
	 * Gets the web socket end point and connection token for an execute request.
	 *
	 * @param attachStdout Should Rancher attach a TTY to StdOut?
	 * @return WebSocket connection token.
	 * @throws IOException When connection to the container fails.
	 */
	private JsonNode getToken(boolean attachStdout) throws IOException {
		HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(url)).newBuilder();
		String path = urlBuilder.build().toString();
		String content = this.apiData(attachStdout);
		try {
			RequestBody body = RequestBody.create(MediaType.parse("application/json"), content);
			Request request = new Request.Builder().url(path).post(body)
					.addHeader("Authorization", Credentials.basic(accessKey, secretKey)).build();
			Response response = client.newCall(request).execute();
			ObjectMapper mapper = new ObjectMapper();
			if (response.body() != null) {
				return mapper.readTree(response.body().string());
			} else {
				throw new IOException("WebSocket response was null");
			}
		} catch (IOException e) {
			log(ERR_LEVEL, e.getMessage());
			throw e;
		}
	}

	/**
	 * Builds JSON string of API data.
	 *
	 * @param attachStdout Should Rancher attach a TTY to StdOut?
	 * @return API Token to use in rancher connections.
	 * @throws JsonMappingException When JSON is invalid.
	 * @throws JsonProcessingException When JSON is invalid.
	 */
	private String apiData(boolean attachStdout) throws JsonProcessingException {
		ObjectMapper mapper = new ObjectMapper();
		JsonNode root = mapper.readTree("{}");
		((ObjectNode) root).put("tty", false);
		((ObjectNode) root).put("attachStdin", false);
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
	 * @param bytes A byte array to send to RunDeck with its log level.
	 */
	public void logDockerStream(byte[] bytes) {
		LogMessage message;
		BufferedReader stringReader;
		try (MessageReader reader = new MessageReader(ByteSource.wrap(bytes).openStream())) {
			while ((message = reader.nextMessage()) != null) {
				// If logging to RunDeck, we send lines beginning with STRDERR_TOK to ERR_LEVEL.
				// To do that, we make a BufferedReader and process it line-by-line in log
				// function.
				if (listener != null) {
					stringReader = new BufferedReader(new StringReader(new String(message.content.array())));
					log(stringReader);
				} else {
					output.append(new String(message.content.array()));
				}
				nextHeader = reader.nextHeader();
			}
		} catch (IOException e) {
			log(ERR_LEVEL, e.getMessage());
		}
	}

	/**
	 * Read a Buffer line by line and send lines prefixed by STDERR_TOK to the
	 * WARN_LEVEL channel of RunDeck's console.
	 *
	 * @param stringReader A buffer of text to be sent to the logger.
	 * @throws IOException when reading buffer fails.
	 */
	private void log(BufferedReader stringReader) throws IOException {
		String line;
		while ((line = stringReader.readLine()) != null) {
			if (line.startsWith(STDERR_TOK)) {
				this.log(Constants.WARN_LEVEL, line.substring(STDERR_TOKLEN - 1) + "\n");
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
	 * @param level Log level.
	 * @param message The message to log.
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