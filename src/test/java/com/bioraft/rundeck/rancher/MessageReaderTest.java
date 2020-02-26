package com.bioraft.rundeck.rancher;

import static org.junit.Assert.*;

import com.dtolabs.rundeck.core.execution.workflow.steps.node.NodeStepException;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;

public class MessageReaderTest {
    @Test
    public void testNoHeader() throws IOException {
        String initialString = "text";
        InputStream targetStream = new ByteArrayInputStream(initialString.getBytes(StandardCharsets.UTF_8));
        MessageReader subject = new MessageReader(targetStream);
        LogMessage logMessage = subject.nextMessage();
        byte[] bytes = "aaaa".getBytes();
        logMessage.content().get(bytes, 0, 4);
        assertEquals(initialString, new String(bytes));
        assertEquals("STDOUT", logMessage.stream().toString());
    }

    @Test
    public void testEmpty() throws NodeStepException, IOException {
        byte[] input = "bbbbbbbb".getBytes();
        input[0] = 0x01;
        input[1] = 0x00;
        input[2] = 0x00;
        input[3] = 0x00;
        input[4] = 0x00;
        input[5] = 0x00;
        input[6] = 0x00;
        input[7] = 0x00;

        InputStream targetStream = new ByteArrayInputStream(input);
        MessageReader subject = new MessageReader(targetStream);

        LogMessage logMessage = subject.nextMessage();
        assertEquals("STDOUT", logMessage.stream().toString());
        assertEquals(0, logMessage.content().remaining());

        assertNull(logMessage = subject.nextMessage());
    }

    @Test
    public void testStdout() throws NodeStepException, IOException {
        byte[] input = "bbbbbbbbbbbb".getBytes();
        input[0] = 0x01;
        input[1] = 0x00;
        input[2] = 0x00;
        input[3] = 0x00;
        input[4] = 0x00;
        input[5] = 0x00;
        input[6] = 0x00;
        input[7] = 0x04;
        input[8] = 'a';
        input[9] = 'a';
        input[10] = 'a';
        input[11] = 'a';
        byte[] bytes = "....".getBytes();

        InputStream targetStream = new ByteArrayInputStream(input);
        MessageReader subject = new MessageReader(targetStream);

        LogMessage logMessage = subject.nextMessage();
        assertEquals("STDOUT", logMessage.stream().toString());
        assertEquals(4, logMessage.content().remaining());
        logMessage.content().get(bytes, 0, 2);
        assertEquals("aa..", new String(bytes));
        logMessage.content().get(bytes, 2, 2);
        assertEquals("aaaa", new String(bytes));
    }

    @Test
    public void testAll() throws NodeStepException, IOException {
        byte[] input = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb".getBytes();
        input[0] = 0x01;
        input[1] = 0x00;
        input[2] = 0x00;
        input[3] = 0x00;
        input[4] = 0x00;
        input[5] = 0x00;
        input[6] = 0x00;
        input[7] = 0x04;
        input[8] = 'a';
        input[9] = 'a';
        input[10] = 'a';
        input[11] = 'a';
        input[12] = 0x02;
        input[13] = 0x00;
        input[14] = 0x00;
        input[15] = 0x00;
        input[16] = 0x00;
        input[17] = 0x00;
        input[18] = 0x00;
        input[19] = 0x02;
        input[20] = 'x';
        input[21] = 'y';
        input[22] = 0x00;
        input[23] = 0x00;
        input[24] = 0x00;
        input[25] = 0x00;
        input[26] = 0x00;
        input[27] = 0x00;
        input[28] = 0x00;
        input[29] = 0x06;
        input[30] = 'c';
        input[31] = 'd';
        input[32] = 'e';
        input[33] = 'f';
        input[34] = 'g';
        input[35] = 'h';
        byte[] bytes = "......".getBytes();

        InputStream targetStream = new ByteArrayInputStream(input);
        MessageReader subject = new MessageReader(targetStream);

        LogMessage logMessage = subject.nextMessage();
        assertEquals("STDOUT", logMessage.stream().toString());
        assertEquals(4, logMessage.content().remaining());
        logMessage.content().get(bytes, 0, 4);
        assertEquals("aaaa..", new String(bytes));

        logMessage = subject.nextMessage();
        assertEquals("STDERR", logMessage.stream().toString());
        assertEquals(2, logMessage.content().remaining());
        logMessage.content().get(bytes, 0, 2);
        assertEquals("xyaa..", new String(bytes));

        logMessage = subject.nextMessage();
        assertEquals("STDIN", logMessage.stream().toString());
        assertEquals(6, logMessage.content().remaining());
        logMessage.content().get(bytes, 0, 6);
        assertEquals("cdefgh", new String(bytes));
    }

    @Test
    public void testSplit() throws NodeStepException, IOException {
        byte[] input = "bbbbbbbbbbbbbbbbbbbbbb".getBytes();
        input[0] = 0x01;
        input[1] = 0x00;
        input[2] = 0x00;
        input[3] = 0x00;
        input[4] = 0x00;
        input[5] = 0x00;
        input[6] = 0x00;
        input[7] = 0x04;
        input[8] = 'a';
        input[9] = 'a';
        input[10] = 'a';
        input[11] = 'a';
        input[12] = 0x01;
        input[13] = 0x00;
        input[14] = 0x00;
        input[15] = 0x00;
        input[16] = 0x00;
        input[17] = 0x00;
        input[18] = 0x00;
        input[19] = 0x04;
        input[20] = 'x';
        input[21] = 'y';

        byte[] bytes = "......".getBytes();

        InputStream targetStream = new ByteArrayInputStream(input);
        MessageReader subject = new MessageReader(targetStream);

        LogMessage logMessage = subject.nextMessage();
        assertEquals("STDOUT", logMessage.stream().toString());
        assertEquals(4, logMessage.content().remaining());
        logMessage.content().get(bytes, 0, 4);
        assertEquals("aaaa..", new String(bytes));

        logMessage = subject.nextMessage();
        assertEquals("STDOUT", logMessage.stream().toString());
        assertEquals(2, logMessage.content().remaining());
        logMessage.content().get(bytes, 0, 2);
        assertEquals("xyaa..", new String(bytes));

        byte[] header = "........".getBytes();
        header[0] = 0x01;
        header[1] = 0x00;
        header[2] = 0x00;
        header[3] = 0x00;
        header[4] = 0x00;
        header[5] = 0x00;
        header[6] = 0x00;
        header[7] = 0x02;

        byte[] nextHeader = subject.nextHeader();
        assertEquals(ByteBuffer.wrap(header).getInt(0), ByteBuffer.wrap(nextHeader).getInt(0));
        assertEquals(ByteBuffer.wrap(header).getInt(1), ByteBuffer.wrap(nextHeader).getInt(1));
        assertEquals(ByteBuffer.wrap(header).getInt(2), ByteBuffer.wrap(nextHeader).getInt(2));
        assertEquals(ByteBuffer.wrap(header).getInt(3), ByteBuffer.wrap(nextHeader).getInt(3));
        assertEquals(ByteBuffer.wrap(header).getInt(4), ByteBuffer.wrap(nextHeader).getInt(4));

        byte[] input2 = "bbbbbbbbbbbbbbbb".getBytes();
        input2[0] = 'j';
        input2[1] = 'k';
        input2[2] = 0x00;
        input2[3] = 0x00;
        input2[4] = 0x00;
        input2[5] = 0x00;
        input2[6] = 0x00;
        input2[7] = 0x00;
        input2[8] = 0x00;
        input2[9] = 0x06;
        input2[10] = 'c';
        input2[11] = 'd';
        input2[12] = 'e';
        input2[13] = 'f';
        input2[14] = 'g';
        input2[15] = 'h';

        targetStream = new ByteArrayInputStream(input2);
        subject = new MessageReader(targetStream);

        logMessage = subject.nextMessage();
        assertEquals("STDOUT", logMessage.stream().toString());
        // There is more to work out here...
    }
}
