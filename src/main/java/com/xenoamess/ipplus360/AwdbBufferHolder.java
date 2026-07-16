package com.xenoamess.ipplus360;

import com.xenoamess.ipplus360.enumerate.FileOpenMode;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * 构造一个缓冲器实例
 */
class AwdbBufferHolder {
    private final ByteBuffer byteBuffer;
    private final LargeFileBuffer largeFileBuffer;
    private final RandomAccessFile randomAccessFile;
    private final FileChannel fileChannel;
    private final long fileSize;
    private final boolean useLargeFile;

    public AwdbBufferHolder(File file, FileOpenMode mode) throws IOException {
        this.randomAccessFile = new RandomAccessFile(file, "r");
        this.fileChannel = randomAccessFile.getChannel();
        this.fileSize = fileChannel.size();

        if (fileSize > Integer.MAX_VALUE) {
            this.useLargeFile = true;
            this.largeFileBuffer = new LargeFileBuffer(fileChannel, fileSize);
            this.byteBuffer = null;
        } else {
            this.useLargeFile = false;
            this.largeFileBuffer = null;
            if (mode == FileOpenMode.MEMORY) {
                this.byteBuffer = ByteBuffer.wrap(new byte[(int) fileSize]);
                if (fileChannel.read(this.byteBuffer) != this.byteBuffer.capacity()) {
                    throw new IOException("Unable to read " + file.getName() + " into memory. Unexpected end of stream.");
                }
                this.byteBuffer.position(0);
            } else {
                this.byteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);
            }
        }
    }

    /**
     * 返回是否使用大文件模式
     */
    public boolean isLargeFile() {
        return useLargeFile;
    }

    /**
     * 返回 ByteBuffer 的独立副本（小文件模式），调用方各自维护 position/limit
     */
    public ByteBuffer getByteBuffer() {
        if (useLargeFile) {
            throw new UnsupportedOperationException("Large file mode, use getLargeFileBuffer() instead");
        }
        return byteBuffer.duplicate();
    }

    /**
     * 返回 LargeFileBuffer 的独立副本（大文件模式），避免多线程竞争
     */
    public LargeFileBuffer getLargeFileBuffer() {
        if (!useLargeFile) {
            throw new UnsupportedOperationException("Small file mode, use getByteBuffer() instead");
        }
        return largeFileBuffer.duplicate();
    }

    public void close() throws IOException {
        if (randomAccessFile != null) {
            randomAccessFile.close();
        }
    }
}
