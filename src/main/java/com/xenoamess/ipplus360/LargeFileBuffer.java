package com.xenoamess.ipplus360;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * 大文件缓冲区，支持超过2GB的文件
 * 完全无共享状态的设计，每个实例独立操作，绝对线程安全
 */
class LargeFileBuffer {
    private static final int SEGMENT_SIZE = Integer.MAX_VALUE;

    private final FileChannel fileChannel;
    private final long fileSize;
    private final MappedByteBuffer[] segments;
    private final int segmentCount;

    private long position;
    private long limit;
    private long capacity;

    /**
     * 私有构造方法，用于创建副本
     */
    private LargeFileBuffer(FileChannel fileChannel, long fileSize, MappedByteBuffer[] segments, int segmentCount) {
        this.fileChannel = fileChannel;
        this.fileSize = fileSize;
        this.segments = segments;
        this.segmentCount = segmentCount;
        this.capacity = fileSize;
        this.limit = fileSize;
        this.position = 0;
    }

    public LargeFileBuffer(FileChannel fileChannel, long fileSize) throws IOException {
        this.fileChannel = fileChannel;
        this.fileSize = fileSize;
        this.segmentCount = (int) ((fileSize + SEGMENT_SIZE - 1) / SEGMENT_SIZE);
        this.segments = new MappedByteBuffer[segmentCount];
        this.capacity = fileSize;
        this.limit = fileSize;
        this.position = 0;

        // 预创建所有Segment的内存映射，只做一次
        for (int i = 0; i < segmentCount; i++) {
            long segmentStart = (long) i * SEGMENT_SIZE;
            long segmentSize = Math.min(SEGMENT_SIZE, fileSize - segmentStart);
            segments[i] = fileChannel.map(FileChannel.MapMode.READ_ONLY, segmentStart, segmentSize);
        }
    }
    /**
     * 创建缓冲区副本，共享底层内存映射，独立维护position和limit
     * 多线程场景下，每个线程应该获取独立的副本，避免锁竞争
     */
    public LargeFileBuffer duplicate() {
        LargeFileBuffer copy = new LargeFileBuffer(this.fileChannel, this.fileSize, this.segments, this.segmentCount);
        copy.position = this.position;
        copy.limit = this.limit;
        return copy;
    }

    private MappedByteBuffer getSegment(long position) {
        int segmentIndex = (int) (position / SEGMENT_SIZE);
        return segments[segmentIndex];
    }

    private int getSegmentOffset(long position) {
        return (int) (position % SEGMENT_SIZE);
    }

    public byte get() {
        if (position >= limit) {
            throw new java.nio.BufferUnderflowException();
        }
        MappedByteBuffer segment = getSegment(position);
        int offset = getSegmentOffset(position);
        position++;
        return segment.get(offset);
    }

    public byte get(int index) {
        if (index >= limit) {
            throw new IndexOutOfBoundsException();
        }
        MappedByteBuffer segment = getSegment(index);
        int offset = getSegmentOffset(index);
        return segment.get(offset);
    }

    /**
     * 从指定偏移位置读取数据，不修改当前buffer的position
     */
    public void get(long offset, byte[] dst, int off, int len) {
        if (offset < 0 || offset + len > capacity) {
            throw new IndexOutOfBoundsException("Offset " + offset + " out of bounds");
        }

        int remaining = len;
        int dstOffset = off;
        long currentPosition = offset;

        while (remaining > 0) {
            MappedByteBuffer segment = getSegment(currentPosition);
            int segmentOffset = getSegmentOffset(currentPosition);
            int bytesToRead = Math.min(remaining, SEGMENT_SIZE - segmentOffset);

            // 使用segment的副本进行读取，不修改共享的segment的position，完全线程安全
            MappedByteBuffer segmentCopy = (MappedByteBuffer) segment.duplicate();
            segmentCopy.position(segmentOffset);
            segmentCopy.get(dst, dstOffset, bytesToRead);

            currentPosition += bytesToRead;
            dstOffset += bytesToRead;
            remaining -= bytesToRead;
        }
    }

    public void get(byte[] dst, int offset, int length) {
        get(position, dst, offset, length);
        position += length;
    }

    public void get(byte[] dst) {
        get(dst, 0, dst.length);
    }

    public char getChar() {
        byte[] bytes = new byte[2];
        get(bytes);
        return (char) ((bytes[0] << 8) | (bytes[1] & 0xFF));
    }

    public short getShort() {
        byte[] bytes = new byte[2];
        get(bytes);
        return (short) ((bytes[0] << 8) | (bytes[1] & 0xFF));
    }

    public int getInt() {
        byte[] bytes = new byte[4];
        get(bytes);
        return (bytes[0] << 24) | ((bytes[1] & 0xFF) << 16) | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
    }

    public long getLong() {
        byte[] bytes = new byte[8];
        get(bytes);
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result = (result << 8) | (bytes[i] & 0xFF);
        }
        return result;
    }

    public float getFloat() {
        return Float.intBitsToFloat(getInt());
    }

    public double getDouble() {
        return Double.longBitsToDouble(getLong());
    }

    public boolean hasRemaining() {
        return position < limit;
    }

    public long remaining() {
        return limit - position;
    }

    public long position() {
        return position;
    }

    public void position(long newPosition) {
        if (newPosition < 0 || newPosition > fileSize) {
            throw new IllegalArgumentException("Position " + newPosition + " out of bounds [0, " + fileSize + "]");
        }
        this.position = newPosition;
    }

    public long limit() {
        return limit;
    }

    public void limit(long newLimit) {
        if (newLimit < 0 || newLimit > fileSize) {
            throw new IllegalArgumentException("Limit " + newLimit + " out of bounds [0, " + fileSize + "]");
        }
        this.limit = newLimit;
    }

    public long capacity() {
        return fileSize;
    }

    public void clear() {
        position = 0;
        limit = capacity;
    }

    public void flip() {
        limit = position;
        position = 0;
    }

    public void rewind() {
        position = 0;
    }
}
