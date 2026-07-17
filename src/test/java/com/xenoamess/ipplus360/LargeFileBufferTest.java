package com.xenoamess.ipplus360;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LargeFileBuffer 单元测试（不依赖 >2GB 文件，小文件即单 segment 场景）。
 */
class LargeFileBufferTest {

    @TempDir
    Path tempDir;

    private LargeFileBuffer bufferOf(byte[] bytes) throws IOException {
        Path p = tempDir.resolve("buf.bin");
        java.nio.file.Files.write(p, bytes);
        FileChannel channel = FileChannel.open(p, StandardOpenOption.READ);
        return new LargeFileBuffer(channel, bytes.length);
    }

    @Test
    void sequentialAndAbsoluteReads() throws IOException {
        LargeFileBuffer buf = bufferOf(new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        assertEquals(8, buf.capacity());
        assertEquals(8, buf.remaining());
        assertEquals(1, buf.get());
        assertEquals(2, buf.get());
        assertEquals(6, buf.remaining());

        // 绝对读不改变 position
        byte[] dst = new byte[3];
        buf.get(4, dst, 0, 3);
        assertArrayEquals(new byte[]{5, 6, 7}, dst);
        assertEquals(2, buf.position());

        assertTrue(buf.hasRemaining());
        buf.position(8);
        assertFalse(buf.hasRemaining());
        assertThrows(BufferUnderflowException.class, buf::get);
    }

    @Test
    void primitiveReadsAreBigEndian() throws IOException {
        LargeFileBuffer buf = bufferOf(new byte[]{
                0x12, 0x34,                       // char/short
                0x11, 0x22, 0x33, 0x44,           // int
                0x3F, (byte) 0xC0, 0x00, 0x00,    // float 1.5
                0x3F, (byte) 0xF8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // double 1.5
                0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08          // long
        });
        assertEquals(0x1234, buf.getChar());
        buf.position(0);
        assertEquals(0x1234, buf.getShort());
        buf.position(2);
        assertEquals(0x11223344, buf.getInt());
        assertEquals(Float.intBitsToFloat(0x3FC00000), buf.getFloat(), 0.0f);
        assertEquals(Double.longBitsToDouble(0x3FF8000000000000L), buf.getDouble(), 0.0d);
        assertEquals(0x0102030405060708L, buf.getLong());
    }

    @Test
    void duplicateHasIndependentPosition() throws IOException {
        LargeFileBuffer buf = bufferOf(new byte[]{9, 9, 9, 9});
        buf.position(2);
        LargeFileBuffer copy = buf.duplicate();
        assertEquals(2, copy.position());
        copy.position(0);
        assertEquals(2, buf.position());
        copy.limit(1);
        assertEquals(4, buf.limit());
    }

    @Test
    void outOfBoundsPositionsRejected() throws IOException {
        LargeFileBuffer buf = bufferOf(new byte[]{1, 2});
        assertThrows(IllegalArgumentException.class, () -> buf.position(3));
        assertThrows(IllegalArgumentException.class, () -> buf.limit(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> buf.get(5, new byte[1], 0, 1));
    }

    @Test
    void flipRewindClear() throws IOException {
        LargeFileBuffer buf = bufferOf(new byte[]{1, 2, 3});
        buf.position(2);
        buf.flip();
        assertEquals(2, buf.limit());
        assertEquals(0, buf.position());
        buf.rewind();
        assertEquals(0, buf.position());
        buf.clear();
        assertEquals(3, buf.limit());
        assertEquals(0, buf.position());
    }
}
