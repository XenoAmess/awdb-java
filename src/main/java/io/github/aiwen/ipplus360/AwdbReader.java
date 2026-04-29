package io.github.aiwen.ipplus360;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.aiwen.ipplus360.entity.AwdbMetaData;
import io.github.aiwen.ipplus360.enumerate.FileOpenMode;
import io.github.aiwen.ipplus360.exception.AwdbCloseException;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * AWDB 文件读取器
 */
public class AwdbReader implements AutoCloseable {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AwdbBufferHolder bufferRef;
    private final AwdbNodeCache cache;
    private final AwdbMetaData awdbMetaData;

    /**
     * 私有构造函数，供内部使用
     */
    private AwdbReader(AwdbBufferHolder holder, AwdbNodeCache cache) throws IOException {
        this.bufferRef = holder;
        this.cache = cache;

        if (holder.isLargeFile()) {
            // 大文件解析逻辑
            LargeFileBuffer buffer = holder.getLargeFileBuffer();
            int metaLen = metaLengthLarge(buffer);
            AwdbDataParserLarge awdbDataParser = new AwdbDataParserLarge(this.cache, buffer);
            this.awdbMetaData = awdbDataParser.parseMeta(metaLen);
        } else {
            // 小文件解析逻辑 - 使用副本避免共享访问
            ByteBuffer buffer = holder.getCurrBuffer();
            int metaLen = metaLength(buffer);
            AwdbDataParser awdbDataParser = new AwdbDataParser(this.cache, buffer.duplicate());
            this.awdbMetaData = awdbDataParser.parseMeta(metaLen);
        }
    }


    /**
     * 创建一个 AwdbReader 实例（完整参数）
     *
     * @param file  文件
     * @param cache 缓存实现
     * @param mode  文件打开模式
     * @return AwdbReader
     * @throws IOException IO异常
     */
    public static AwdbReader open(File file, AwdbNodeCache cache, FileOpenMode mode) throws IOException {
        return new AwdbReader(new AwdbBufferHolder(file, mode), cache);
    }

    /**
     * 元数据长度
     */
    private int metaLength(ByteBuffer buffer) {
        buffer.position(0);
        return buffer.getChar() & 0xFFFF; // 读取2字节作为长度
    }

    /**
     * 元数据长度（大文件）
     */
    private int metaLengthLarge(LargeFileBuffer buffer) {
        buffer.position(0);
        return buffer.getChar() & 0xFFFF; // 读取2字节作为长度
    }

    /**
     * 查询IP位置信息
     *
     * @param ipStr IP地址字符串
     * @return JSON结果
     */
    public JsonNode findIpLocation(String ipStr) throws IOException {
        if (ipStr == null || ipStr.isEmpty()) {
            return null;
        }

        try {
            InetAddress ipAddr = InetAddress.getByName(ipStr.trim());
            return findIpLocation(ipAddr);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 查询IP位置信息
     *
     * @param ipAddr InetAddress
     * @return JSON结果
     */
    public JsonNode findIpLocation(InetAddress ipAddr) throws IOException {
        String ipVersion = awdbMetaData.getIpVersion();
        String v4 = "4";
        String v6 = "6";
        String mix = "4_6";

        if (v4.equals(ipVersion) && ipAddr.getAddress().length == 16) {
            return OBJECT_MAPPER.createObjectNode();
        }

        if (v6.equals(ipVersion) && ipAddr.getAddress().length == 4) {
            return OBJECT_MAPPER.createObjectNode();
        }

        long nodeIndex = 0;
        int startBit = 0;
        if (mix.equals(ipVersion)) {
            if (ipAddr.getAddress().length == 4) {
                // 处理IPv4地址转换
                String ipv6Str = String.format("::ffff:%s", ipAddr.getHostAddress());
                try {
                    ipAddr = InetAddress.getByName(ipv6Str);
                    // 前96位是固定的，预先计算出对应的节点索引，只需要搜索后面32位
                    AwdbBufferHolder holder = getBuffer();
                    long nodeCount = awdbMetaData.getNodeCount();
                    if (holder.isLargeFile()) {
                        LargeFileBuffer buffer = holder.getLargeFileBuffer();
                        // 前80位都是0
                        for (int i = 0; i < 80 && nodeIndex < nodeCount; i++) {
                            nodeIndex = readNodeIndexLarge(buffer, nodeIndex, 0);
                        }
                        // 接下来16位都是1
                        for (int i = 0; i < 16 && nodeIndex < nodeCount; i++) {
                            nodeIndex = readNodeIndexLarge(buffer, nodeIndex, 1);
                        }
                    } else {
                        ByteBuffer buffer = holder.getCurrBuffer().duplicate();
                        // 前80位都是0
                        for (int i = 0; i < 80 && nodeIndex < nodeCount; i++) {
                            nodeIndex = readNodeIndex(buffer, (int) nodeIndex, 0);
                        }
                        // 接下来16位都是1
                        for (int i = 0; i < 16 && nodeIndex < nodeCount; i++) {
                            nodeIndex = readNodeIndex(buffer, (int) nodeIndex, 1);
                        }
                    }
                    // 从第96位开始搜索后面的32位
                    startBit = 96;
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                    return OBJECT_MAPPER.createObjectNode();
                } catch (IOException e) {
                    e.printStackTrace();
                    return OBJECT_MAPPER.createObjectNode();
                }
            }
        }


        nodeIndex = findTreeIndex(ipAddr, nodeIndex, startBit);
        if (nodeIndex <= 0) {
            return OBJECT_MAPPER.createObjectNode();
        }

        long pointer = awdbMetaData.getBaseOffset() + nodeIndex - awdbMetaData.getNodeCount() - 10;
        switch (awdbMetaData.getDecodeType()) {
            case 1:
                JsonNode structureResult = decodeContentStructure(pointer);
                if (structureResult != null) {
                    return structureResult;
                }
                return OBJECT_MAPPER.createObjectNode();
            case 2:
                try {
                    return decodeContentDirect(pointer);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.err.println("解码失败，offset=" + pointer + ", 错误: " + e.getMessage());
                    return OBJECT_MAPPER.createObjectNode();
                }
            default:
                return new TextNode("Invalid decode type: " + awdbMetaData.getDecodeType());
        }
    }

    /**
     * 查找树形索引
     */
    private long findTreeIndex(InetAddress ipAddr, long nodeIndex, int startBit) throws IOException {
        AwdbBufferHolder holder = getBuffer();
        byte[] rawAddr = ipAddr.getAddress();

        int bitLength = rawAddr.length * 8;
        long nodeCount = awdbMetaData.getNodeCount();

        if (holder.isLargeFile()) {
            LargeFileBuffer buffer = holder.getLargeFileBuffer(); // getLargeFileBuffer() 已自动返回独立副本
            for (int pl = startBit; pl < bitLength && nodeIndex < nodeCount; pl++) {
                int b = 0xFF & rawAddr[pl / 8];
                int bit = 1 & (b >> 7 - (pl % 8));
                nodeIndex = readNodeIndexLarge(buffer, nodeIndex, bit);
            }
        } else {
            // 使用 ByteBuffer 副本避免共享访问
            ByteBuffer buffer = holder.getCurrBuffer().duplicate();
            for (int pl = startBit; pl < bitLength && nodeIndex < nodeCount; pl++) {
                int b = 0xFF & rawAddr[pl / 8];
                int bit = 1 & (b >> 7 - (pl % 8));
                nodeIndex = readNodeIndex(buffer, (int) nodeIndex, bit);
            }
        }

        if (nodeIndex == nodeCount) {
            return 0;
        } else if (nodeIndex > nodeCount) {
            return nodeIndex;
        } else {
            return 0;
        }
    }

    /**
     * 读取节点索引（小文件）
     */
    private int readNodeIndex(ByteBuffer buffer, int nodeIndex, int bit) {
        int offset = nodeIndex * awdbMetaData.getByteLen() * 2 + bit * awdbMetaData.getByteLen() + (int) awdbMetaData.getStartLength();
        // 创建buffer副本，设置offset，不修改原buffer的position，线程安全
        ByteBuffer bufferCopy = buffer.duplicate();
        bufferCopy.position(offset);
        return AwdbDataParser.buffer2Integer(bufferCopy, 0, awdbMetaData.getByteLen());
    }

    /**
     * 读取节点索引（大文件）
     */
    private long readNodeIndexLarge(LargeFileBuffer buffer, long nodeIndex, int bit) throws IOException {
        int byteLen = awdbMetaData.getByteLen();
        long startLength = awdbMetaData.getStartLength();
        long offset = nodeIndex * byteLen * 2L + bit * byteLen + startLength;
        if (offset < 0 || offset + byteLen > buffer.capacity()) {
            throw new IOException("Offset " + offset + " with length " + byteLen + " exceeds buffer capacity " + buffer.capacity());
        }
        byte[] bytes = new byte[byteLen];
        // 绝对位置读取，不需要设置buffer的position，完全线程安全
        buffer.get(offset, bytes, 0, byteLen);

        long unsignedResult = 0;
        for (int i = 0; i < bytes.length; i++) {
            unsignedResult = (unsignedResult << 8) | (bytes[i] & 0xFF);
        }
        
        return unsignedResult;
    }

    /**
     * 结构解码
     */
    private JsonNode decodeContentStructure(long offset) throws IOException {
        AwdbBufferHolder holder = getBuffer();
        try {
            if (holder.isLargeFile()) {
                LargeFileBuffer buffer = holder.getLargeFileBuffer(); // getLargeFileBuffer() 已自动返回独立副本
                AwdbDataParserLarge parser = new AwdbDataParserLarge(cache, buffer, awdbMetaData.getBaseOffset());
                return parser.parseData(offset);
            } else {
                // 使用 ByteBuffer 副本避免共享访问
                ByteBuffer buffer = holder.getCurrBuffer().duplicate();
                AwdbDataParser parser = new AwdbDataParser(cache, buffer, awdbMetaData.getBaseOffset());
                return parser.parseData((int) offset);
            }
        } catch (Exception e) {
            e.printStackTrace();
            // 捕获并处理解码过程中的异常，避免程序终止
            System.err.println("解码失败，offset=" + offset + ", 错误: " + e.getMessage());
            return null;
        }
    }

    /**
     * 直接解码
     */
    private JsonNode decodeContentDirect(long offset) throws IOException {
        AwdbBufferHolder holder = getBuffer();
        if (holder.isLargeFile()) {
            return decodeContentDirectLarge(offset, holder.getLargeFileBuffer());
        } else {
            return decodeContentDirectSmall(offset, holder.getCurrBuffer());
        }
    }

    /**
     * 直接解码（小文件）
     */
    private JsonNode decodeContentDirectSmall(long offset, ByteBuffer buffer) throws IOException {
        // 使用副本避免共享访问
        ByteBuffer bufferCopy = buffer.duplicate();
        bufferCopy.position((int) offset);

        int dataLen = AwdbDataParser.buffer2Integer(bufferCopy, 0, 4);
        bufferCopy.limit((int) (offset + 4 + dataLen));
        String[] values = new String(bufferCopy.array(), bufferCopy.position(), dataLen, "UTF-8").split("\t");

        Map<String, JsonNode> result = new HashMap<>(awdbMetaData.getColumns().size());
        for (int i = 0; i < awdbMetaData.getColumns().size(); i++) {
            String value = values.length - i > 0 ? values[i] : "";
            result.put((String) awdbMetaData.getColumns().get(i), new TextNode(value));
        }

        return new ObjectNode(OBJECT_MAPPER.getNodeFactory(), Collections.unmodifiableMap(result));
    }

    /**
     * 直接解码（大文件）
     */
    private JsonNode decodeContentDirectLarge(long offset, LargeFileBuffer buffer) throws IOException {
        long position = buffer.position();
        buffer.position(offset);

        byte[] lenBytes = new byte[4];
        buffer.get(lenBytes);
        int dataLen = (lenBytes[0] << 24) | ((lenBytes[1] & 0xFF) << 16) | ((lenBytes[2] & 0xFF) << 8) | (lenBytes[3] & 0xFF);

        byte[] dataBytes = new byte[dataLen];
        buffer.get(dataBytes);
        String[] values = new String(dataBytes, "UTF-8").split("\t");
        buffer.position(position);

        Map<String, JsonNode> result = new HashMap<>(awdbMetaData.getColumns().size());
        for (int i = 0; i < awdbMetaData.getColumns().size(); i++) {
            String value = values.length - i > 0 ? values[i] : "";
            result.put((String) awdbMetaData.getColumns().get(i), new TextNode(value));
        }

        return new ObjectNode(OBJECT_MAPPER.getNodeFactory(), Collections.unmodifiableMap(result));
    }

    /**
     * 获取缓冲区
     */
    private AwdbBufferHolder getBuffer() throws AwdbCloseException {
        if (bufferRef == null) {
            throw new AwdbCloseException();
        }
        return bufferRef;
    }

    @Override
    public void close() throws IOException {
        if (bufferRef != null) {
            bufferRef.close();
        }
    }

    /**
     * 获取AWDB文件的元数据
     */
    public AwdbMetaData getAwdbMetaData() {
        return awdbMetaData;
    }

    @Override
    protected void finalize() throws Throwable {
        close();
        super.finalize();
    }
}