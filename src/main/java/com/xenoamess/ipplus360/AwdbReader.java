package com.xenoamess.ipplus360;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.xenoamess.ipplus360.entity.AwdbMetaData;
import com.xenoamess.ipplus360.enumerate.FileOpenMode;
import com.xenoamess.ipplus360.exception.InvalidAwdbException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger logger = LoggerFactory.getLogger(AwdbReader.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AwdbBufferHolder bufferRef;
    private final AwdbNodeCache cache;
    private final AwdbMetaData awdbMetaData;
    /**
     * 4_6 混合库 ::ffff:0/96 前缀的预走结果（构造期计算一次）；
     * 非 4_6 库为 -1。避免每次 v4 查询重走 96 位前缀。
     */
    private final long ipv4StartNode;

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
            ByteBuffer buffer = holder.getByteBuffer();
            int metaLen = metaLength(buffer);
            AwdbDataParser awdbDataParser = new AwdbDataParser(this.cache, buffer.duplicate());
            this.awdbMetaData = awdbDataParser.parseMeta(metaLen);
        }

        if ("4_6".equals(this.awdbMetaData.getIpVersion())) {
            this.ipv4StartNode = prewalkIpv4Start();
        } else {
            this.ipv4StartNode = -1;
        }
    }

    /**
     * 预走 ::ffff:0/96 前缀（前 80 位 0 + 接下来 16 位 1），返回链尾节点索引
     */
    private long prewalkIpv4Start() throws IOException {
        AwdbBufferHolder holder = getBuffer();
        long nodeCount = awdbMetaData.getNodeCount();
        long nodeIndex = 0;
        if (holder.isLargeFile()) {
            LargeFileBuffer buffer = holder.getLargeFileBuffer();
            for (int i = 0; i < 80 && nodeIndex < nodeCount; i++) {
                nodeIndex = readNodeIndexLarge(buffer, nodeIndex, 0);
            }
            for (int i = 0; i < 16 && nodeIndex < nodeCount; i++) {
                nodeIndex = readNodeIndexLarge(buffer, nodeIndex, 1);
            }
        } else {
            ByteBuffer buffer = holder.getByteBuffer().duplicate();
            for (int i = 0; i < 80 && nodeIndex < nodeCount; i++) {
                nodeIndex = readNodeIndex(buffer, (int) nodeIndex, 0);
            }
            for (int i = 0; i < 16 && nodeIndex < nodeCount; i++) {
                nodeIndex = readNodeIndex(buffer, (int) nodeIndex, 1);
            }
        }
        return nodeIndex;
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
            return findIpLocation(InetAddress.getByName(ipStr.trim()));
        } catch (UnknownHostException e) {
            logger.warn("无法解析的IP地址: {}", ipStr, e);
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
        byte[] rawAddr = ipAddr.getAddress();
        if (mix.equals(ipVersion)) {
            if (rawAddr.length == 4) {
                // 处理IPv4地址转换：直接构造 ::ffff:a.b.c.d 的16字节形式。
                // 不能经 InetAddress 往返——现代 JDK 会把 mapped 地址归一化回
                // Inet4Address（getByName 和 getByAddress 均如此），导致位序错乱。
                byte[] v6mapped = new byte[16];
                v6mapped[10] = (byte) 0xFF;
                v6mapped[11] = (byte) 0xFF;
                System.arraycopy(rawAddr, 0, v6mapped, 12, 4);
                rawAddr = v6mapped;
                // 前96位前缀已在构造期预走（ipv4StartNode），只搜索后面32位
                nodeIndex = ipv4StartNode;
                startBit = 96;
            }
        }


        nodeIndex = findTreeIndex(rawAddr, nodeIndex, startBit);
        if (nodeIndex <= 0) {
            return OBJECT_MAPPER.createObjectNode();
        }

        long pointer = awdbMetaData.getBaseOffset() + nodeIndex - awdbMetaData.getNodeCount() - 10;
        switch (awdbMetaData.getDecodeType()) {
            case 1:
                JsonNode structureResult = decodeContentStructure(pointer);
                if (structureResult == null) {
                    return OBJECT_MAPPER.createObjectNode();
                }
                // 原始数组记录在此做 key-value 映射，两个入口 API 返回结构一致
                if (structureResult.isArray()) {
                    return mapKeyValue(awdbMetaData.getColumns(), structureResult);
                }
                return structureResult;
            case 2:
                try {
                    return decodeContentDirect(pointer);
                } catch (Exception e) {
                    logger.warn("解码失败，offset={}", pointer, e);
                    return OBJECT_MAPPER.createObjectNode();
                }
            default:
                return new TextNode("Invalid decode type: " + awdbMetaData.getDecodeType());
        }
    }

    /**
     * 查找树形索引
     */
    private long findTreeIndex(byte[] rawAddr, long nodeIndex, int startBit) throws IOException {
        AwdbBufferHolder holder = getBuffer();

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
            ByteBuffer buffer = holder.getByteBuffer().duplicate();
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
    private int readNodeIndex(ByteBuffer buffer, int nodeIndex, int bit) throws IOException {
        // 用 long 计算偏移：文件接近 2GB 时 nodeIndex*byteLen*2 按 int 会溢出为负
        long offset = (long) nodeIndex * awdbMetaData.getByteLen() * 2
                + (long) bit * awdbMetaData.getByteLen()
                + awdbMetaData.getStartLength();
        if (offset < 0 || offset + awdbMetaData.getByteLen() > buffer.capacity()) {
            throw new InvalidAwdbException("Node offset " + offset + " with length "
                    + awdbMetaData.getByteLen() + " out of bounds [0, " + buffer.capacity() + "]");
        }
        // 创建buffer副本，设置offset，不修改原buffer的position，线程安全
        ByteBuffer bufferCopy = buffer.duplicate();
        bufferCopy.position((int) offset);
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
                ByteBuffer buffer = holder.getByteBuffer().duplicate();
                AwdbDataParser parser = new AwdbDataParser(cache, buffer, awdbMetaData.getBaseOffset());
                return parser.parseData((int) offset);
            }
        } catch (Exception e) {
            logger.warn("解码失败，offset={}", offset, e);
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
            return decodeContentDirectSmall(offset, holder.getByteBuffer());
        }
    }

    /**
     * 直接解码（小文件）
     */
    private JsonNode decodeContentDirectSmall(long offset, ByteBuffer buffer) throws IOException {
        // 使用副本做顺序读取，兼容无底层数组的 MappedByteBuffer
        ByteBuffer bufferCopy = buffer.duplicate();
        bufferCopy.position((int) offset);

        int dataLen = AwdbDataParser.buffer2Integer(bufferCopy, 0, 4);
        if (dataLen < 0 || dataLen > bufferCopy.remaining()) {
            throw new InvalidAwdbException("Invalid data length " + dataLen + " at offset " + offset);
        }
        byte[] data = new byte[dataLen];
        bufferCopy.get(data);
        String[] values = new String(data, "UTF-8").split("\t");

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
        try {
            buffer.position(offset);

            byte[] lenBytes = new byte[4];
            buffer.get(lenBytes);
            int dataLen = (lenBytes[0] << 24) | ((lenBytes[1] & 0xFF) << 16) | ((lenBytes[2] & 0xFF) << 8) | (lenBytes[3] & 0xFF);
            if (dataLen < 0 || dataLen > buffer.remaining()) {
                throw new InvalidAwdbException("Invalid data length " + dataLen + " at offset " + offset);
            }

            byte[] dataBytes = new byte[dataLen];
            buffer.get(dataBytes);
            String[] values = new String(dataBytes, "UTF-8").split("\t");

            Map<String, JsonNode> result = new HashMap<>(awdbMetaData.getColumns().size());
            for (int i = 0; i < awdbMetaData.getColumns().size(); i++) {
                String value = values.length - i > 0 ? values[i] : "";
                result.put((String) awdbMetaData.getColumns().get(i), new TextNode(value));
            }

            return new ObjectNode(OBJECT_MAPPER.getNodeFactory(), Collections.unmodifiableMap(result));
        } finally {
            buffer.position(position);
        }
    }

    /**
     * 获取缓冲区
     */
    private AwdbBufferHolder getBuffer() {
        return bufferRef;
    }

    /**
     * key和value映射
     *
     * @param keys   key列表
     * @param values value列表
     * @return JsonNode对象
     */
    private JsonNode mapKeyValue(List<Object> keys, JsonNode values) {
        Map<String, JsonNode> resultDict = new HashMap<>(keys.size());

        if (keys.size() == values.size()) {
            for (int i = 0; i < keys.size(); i++) {
                resultDict.put((String) keys.get(i), values.get(i));
            }
        } else {
            for (int i = 0; i < values.size() - 1; i++) {
                resultDict.put((String) keys.get(i), values.get(i));
            }

            String multiAreasName = (String) keys.get(keys.size() - 2);
            List<?> keysList = (List<?>) keys.get(keys.size() - 1);
            JsonNode valuesJsonNode = values.get(values.size() - 1);

            ArrayNode nodes = new ArrayNode(OBJECT_MAPPER.getNodeFactory());
            for (JsonNode value : valuesJsonNode) {
                Map<String, JsonNode> tempDic = new HashMap<>(keysList.size());

                for (int i = 0; i < keysList.size(); i++) {
                    tempDic.put((String) keysList.get(i), value.get(i));
                }
                nodes.add(new ObjectNode(OBJECT_MAPPER.getNodeFactory(), Collections.unmodifiableMap(tempDic)));
            }
            resultDict.put(multiAreasName, nodes);
        }

        return new ObjectNode(OBJECT_MAPPER.getNodeFactory(), Collections.unmodifiableMap(resultDict));
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
}