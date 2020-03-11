/*-
 * -\-\-
 * docker-client
 * --
 * Copyright (C) 2016 Spotify AB
 * --
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -/-/-
 */

package com.bioraft.rundeck.rancher;

import java.nio.ByteBuffer;

public class LogMessage {

	private final Stream stream;
	private final ByteBuffer content;

	public LogMessage(final int streamId, final ByteBuffer content) {
		this.stream = Stream.of(streamId);
		this.content = content;
	}

	public LogMessage(final Stream stream, final ByteBuffer content) {
		this.stream = stream;
		this.content = content;
	}

	public Stream stream() {
		return stream;
	}

	public ByteBuffer content() {
		return content;
	}

	public enum Stream {
		STDIN(0), STDOUT(1), STDERR(2);

		private final int id;

		Stream(int id) {
			this.id = id;
		}

		public int id() {
			return id;
		}

		public static Stream of(final int id) {
			switch (id) {
			case 0:
				return STDIN;
			case 1:
				return STDOUT;
			case 2:
				return STDERR;
			default:
				throw new IllegalArgumentException();
			}
		}

	}

}