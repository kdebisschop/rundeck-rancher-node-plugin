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
 * 
 * Much of this file is copied from com.spotify.docker.client.LogReader
 * @link https://github.com/spotify/docker-client/blob/master/src/main/java/com/spotify/docker/client/LogReader.java
 */

package com.bioraft.rundeck.rancher;

import static com.google.common.io.ByteStreams.copy;
import static com.google.common.io.ByteStreams.nullOutputStream;

import com.google.common.io.ByteStreams;
import com.bioraft.rundeck.rancher.LogMessage.Stream;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Reads a message from Rancher transmitting a portion of a Docker multiplexed
 * stream.
 * 
 * To use this class, instantiate with an InputStream, read nextMessage() until
 * the stream is exhausted, then prepend nextHeader to the next message from
 * Rancher.
 * 
 * @code
 * MessageReader reader = new MessageReader(stream);
 * while ((message = reader.nextMessage()) != null) {
 *   log(message.stream.channel(), new String(message.content.array()));
 * }
 * theRest = reader.nextHeader(); // Prepend this to next Rancher message. 
 * @code
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2019-12-12
 */
public class MessageReader implements Closeable {

	private final InputStream stream;
	
	// Size of header in bytes.
	public static final int HEADER_SIZE = 8;
	
	// Beginning of frame size (bytes after start of header).
	public static final int FRAME_SIZE_OFFSET = 4;

	private byte[] expected = new byte[0];

	public MessageReader(final InputStream stream) {
		this.stream = stream;
	}

	/**
	 * Looks for frame headers in stream and sends a message on each successive call
	 * until the buffer is exhausted.
	 * 
	 * @return Tne next message from the reader.
	 * @throws IOException if streams are not readable.
	 */
	public LogMessage nextMessage() throws IOException {
		stream.mark(HEADER_SIZE);

		// Read header
		final byte[] headerBytes = new byte[HEADER_SIZE];
		final int n = ByteStreams.read(stream, headerBytes, 0, HEADER_SIZE);
		if (n == 0) {
			return null;
		}
		final ByteBuffer header = ByteBuffer.wrap(headerBytes);

		// Read frame
		int streamId;
		final byte[] frame;
		final int idZ = header.getInt(0);
		// Header format is // STREAM_TYPE, 0, 0, 0, SIZE1, SIZE2, SIZE3, SIZE4 //
		// So idZ is // STREAM_TYPE, 0, 0, 0 //
		if (idZ == 0x00000000 || idZ == 0x01000000 || idZ == 0x02000000) {
			streamId = idZ >> 24;
			final int frameSize = header.getInt(FRAME_SIZE_OFFSET);
			
			// If the Docker frame extends into the next Rancher message, the log
			// message will consist of the rest of the buffer and we need to
			// calculate how much of the Docker frame is in the next Rancher
			// message. Otherwise, just send the frame (which moves the buffer 
			// pointer forward to prepare for the next call of nextMessage()).
			if (stream.available() < frameSize) {
				frame = new byte[stream.available()];
				this.calculateNextHeader(idZ, frameSize);
			} else { // Just send the next frame.
				frame = new byte[frameSize];
			}

		} else { // No header, so send everything on STDOUT.
			stream.reset();
			streamId = Stream.STDOUT.id();
			frame = new byte[stream.available()];
		}
		
		ByteStreams.readFully(stream, frame);
		return new LogMessage(streamId, ByteBuffer.wrap(frame));
	}

	/**
	 * Build a header for the rest of the Docker frame, which should arrive in the next
	 * message from Rancher.
	 * 
	 * @param idZ The stream ID from the frame we are working on.
	 * @param frameSize The size of the Docker frame we are creating.
	 * @throws IOException if streams are not readable.
	 */
	private void calculateNextHeader(int idZ, int frameSize) throws IOException {
		// Calculate the number of bytes expected before next Docker frame.
		int size = frameSize - stream.available();
		// Build header with same stream ID (STDIN/STDOUT/STDERR) and expected size.
		expected = ByteBuffer.allocate(HEADER_SIZE).putInt(idZ).putInt(size).array();		
	}
	
	/**
	 * Returns a header for the rest of the Docker frame, which should arrive in the next
	 * message from Rancher.
	 * 
	 * @return An 8-byte header.
	 */
	public byte[] nextHeader() {
		return expected;
	}

	@Override
	public void close() throws IOException {
		// RancherWebSocketListener will close the stream and release the connection
		// after we read all the data.
		// We cannot call the stream's close method because it an instance of
		// UncloseableInputStream, where close is a no-op.
		copy(stream, nullOutputStream());
	}
}
