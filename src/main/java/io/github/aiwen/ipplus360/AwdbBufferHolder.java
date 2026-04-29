package io.github.aiwen.ipplus360;

import io.github.aiwen.ipplus360.enumerate.FileOpenMode;

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
     * buffer 的输入流构造器
     *
     * @param stream 输入流
     * @param mode   模式
     */
    public AwdbBufferHolder(InputStream stream, FileOpenMode mode) throws IOException {
        this.randomAccessFile = null;
        this.fileChannel = null;
        this.useLargeFile = false;
        this.largeFileBuffer = null;
        
        if (null == stream) {
            throw new NullPointerException("Unable to use a NULL InputStream");
        }
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final byte[] bytes = new byte[16 * 1024];
        int br;
        while (-1 != (br = stream.read(bytes))) {
            baos.write(bytes, 0, br);
        }

        byte[] byteArray = baos.toByteArray();
        this.fileSize = byteArray.length;
        
        if (mode == FileOpenMode.MEMORY) {
            this.byteBuffer = ByteBuffer.wrap(byteArray);
        } else {
            this.byteBuffer = ByteBuffer.allocateDirect(byteArray.length).put(byteArray);
            this.byteBuffer.position(0);
        }
    }

    /**
     * 返回是否使用大文件模式
     */
    public boolean isLargeFile() {
        return useLargeFile;
    }

    /**
     * 返回 ByteBuffer（小文件模式）
     */
    public synchronized ByteBuffer getByteBuffer() {
        if (useLargeFile) {
            throw new UnsupportedOperationException("Large file mode, use getLargeFileBuffer() instead");
        }
        return byteBuffer.duplicate();
    }

    /**
     * 返回 LargeFileBuffer（大文件模式），自动创建独立副本，避免多线程竞争
     */
    public synchronized LargeFileBuffer getLargeFileBuffer() {
        if (!useLargeFile) {
            throw new UnsupportedOperationException("Small file mode, use getByteBuffer() instead");
        }
        return largeFileBuffer.duplicate();
    }

    /**
     * 返回 ByteBuffer 的副本（兼容旧代码）
     */
    public synchronized ByteBuffer getCurrBuffer() {
        return getByteBuffer();
    }

    public void close() throws IOException {
        if (randomAccessFile != null) {
            randomAccessFile.close();
        }
    }
}
