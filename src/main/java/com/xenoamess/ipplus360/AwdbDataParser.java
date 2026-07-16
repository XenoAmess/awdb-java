package com.xenoamess.ipplus360;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.xenoamess.ipplus360.entity.AwdbMetaData;
import com.xenoamess.ipplus360.enumerate.AwdbDataType;
import com.xenoamess.ipplus360.exception.InvalidAwdbException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * awdb文件数据解析器
 */
class AwdbDataParser {
    private static final Logger logger = LoggerFactory.getLogger(AwdbDataParser.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ByteBuffer buffer;

    private final AwdbNodeCache cache;

    private final CharsetDecoder strDecoder = StandardCharsets.UTF_8.newDecoder();

    private long basePointer = 0;

    public AwdbDataParser(AwdbNodeCache cache, ByteBuffer buffer) {
        this.cache = cache;
        this.buffer = buffer;
    }

    public AwdbDataParser(AwdbNodeCache cache, ByteBuffer buffer, long basePointer) {
        this(cache, buffer);
        this.basePointer = basePointer;
    }

    private final AwdbNodeCache.Loader loader = this::parseData;

    /**
     * 解析元数据
     *
     * @param length 元数据长度
     * @return 元数据
     */
    protected AwdbMetaData parseMeta(int length) throws IOException {
        int startLen = 2 + length;
        buffer.limit(startLen);
     //   buffer.position(2); // 跳过前2字节（长度）
        String meta = strDecoder.decode(buffer).toString();
        JsonNode metaJsonObj = OBJECT_MAPPER.readTree(meta);
        return new AwdbMetaData(metaJsonObj, startLen);
    }

    /**
     * 解析数据
     *
     * @param offset 偏移量
     * @return JsonNode
     */
    protected JsonNode parseData(long offset) throws IOException {
        if (offset < 0 || offset >= this.buffer.capacity()) {
            throw new InvalidAwdbException(
                    "The AWDB file's data section contains bad data: pointer " + offset + " out of bounds [0, " + buffer.capacity() + "]");
        }

        buffer.position((int) offset);

        return parser();
    }

    /**
     * 解析器
     *
     * @return JsonNode
     */
    private JsonNode parser() throws IOException {
        // 获取控制字节
        int typeByte = 0xFF & buffer.get();

        // awdb数据类型
        AwdbDataType dataType = AwdbDataType.getDataType(typeByte);
        if (dataType == null) {
            throw new InvalidAwdbException("Unknown data type byte: " + typeByte);
        }

        // 获取列表长度
        int len = 0xFF & buffer.get();

        // len由buffer.get() & 0xFF得到，范围0-255，不会为负数
        // 空值（len == 0）是合法的，比如空字符串、空数组，不需要拦截

        return parseDataType(dataType, len);
    }

    /**
     * 根据数据类型来解析数据
     *
     * @param type 数据类型
     * @param len  数据长度
     * @return JsonNode
     */
    private JsonNode parseDataType(AwdbDataType type, int len) throws IOException {
        switch (type) {
            case ARRAY:
                return parseArray(len);
            case POINTER:
                return parsePointer(len);
            case STRING:
                return parseString(len);
            case TEXT:
                return parseText(len);
            case INT:
                return parseInt(len);
            case UINT:
                return parseUint(len);
            case FLOAT:
                return parseFloat(len);
            case DOUBLE:
                return parseDouble(len);
            default:
                throw new InvalidAwdbException("Unknown or unexpected type: " + type.name());
        }
    }

    /**
     * 解析数组数据（列表）
     *
     * @param len 数据长度
     * @return JsonNode
     */
    private JsonNode parseArray(int len) throws IOException {
        List<JsonNode> list = new ArrayList<>(len);
        for (int i = 0; i < len; i++) {
            JsonNode node = parser();
            list.add(node);
        }

        ArrayNode nodes = new ArrayNode(OBJECT_MAPPER.getNodeFactory());
        return nodes.addAll(Collections.unmodifiableList(list));
    }

    /**
     * 解析指针数据
     *
     * @param len 数据长度
     * @return JsonNode
     */
    private JsonNode parsePointer(int len) throws IOException {
        // 指针长度至少为1字节
        if (len <= 0) {
            logger.warn("解析指针：无效的指针长度 {} 字节", len);
            return null;
        }

        int oldLimit = buffer.limit();
        buffer.limit(buffer.position() + len);
        long buf = AwdbDataParser.buffer2Integer(buffer, 0, len);
        buffer.limit(oldLimit);

        // 先校验偏移量的合理性，避免超大指针
        if (buf < 0 || buf > buffer.capacity()) {
            logger.warn("指针偏移量无效: {} (有效范围: [0, {}]), 跳过此数据",
                    buf, buffer.capacity() - 1);
            return null;
        }

        long pointer = basePointer + buf;

        // 验证指针的有效性
        if (pointer < 0 || pointer >= buffer.capacity()) {
            logger.warn("指针无效: {} (基指针: {}, 偏移: {}, 有效范围: [0, {}]), 跳过此数据",
                    pointer, basePointer, buf, buffer.capacity() - 1);
            return null;
        }

        int position = buffer.position();
        JsonNode jsonNode = cache.get(loader, pointer);
        buffer.position(position);
        return jsonNode;
    }

    /**
     * 解析字符串数据
     *
     * @param len 数据长度
     * @return JsonNode
     */
    private JsonNode parseString(int len) throws CharacterCodingException {
        int oldLimit = buffer.limit();
        buffer.limit(buffer.position() + len);
        String s = strDecoder.decode(buffer).toString();
        buffer.limit(oldLimit);
        return new TextNode(s);
    }

    /**
     * 解析长字符串数据
     *
     * @param len 数据长度
     * @return JsonNode
     */
    private JsonNode parseText(int len) throws CharacterCodingException {
        int oldLimit = buffer.limit();
        buffer.limit(buffer.position() + len);
        int dataLen = AwdbDataParser.buffer2Integer(buffer, 0, len);
        buffer.limit(oldLimit);
        
        // 限制字符串最大长度，避免内存溢出
        final int MAX_TEXT_LENGTH = 100000; // 限制为100KB
        if (dataLen > MAX_TEXT_LENGTH) {
            logger.warn("   文本数据过长，限制为{}字节: {}", MAX_TEXT_LENGTH, dataLen);
            dataLen = MAX_TEXT_LENGTH;
        }
        
        // 确保剩余数据足够
        if (buffer.position() + dataLen > buffer.limit()) {
            logger.warn("数据不足，只读取剩余字节");
            dataLen = buffer.limit() - buffer.position();
        }
        
        buffer.limit(buffer.position() + dataLen);
        String s = strDecoder.decode(buffer).toString();
        buffer.limit(oldLimit);
        return new TextNode(s);
    }

    /**
     * 解析无符号整型数据
     *
     * @param len 数据长度
     * @return JsonNode
     */
    private JsonNode parseUint(int len) {
        return new LongNode(buffer2Long(len));
    }

    /**
     * 解析有符号整型。INT/FLOAT/DOUBLE 的 len 字节实际是首个数据字节，
     * 其后再读 3 字节，共 4 字节大端。
     *
     * @param firstByte 首个数据字节（即控制区的 len 字节）
     * @return JsonNode
     */
    private JsonNode parseInt(int firstByte) {
        int value = ((firstByte & 0xFF) << 24)
                | ((buffer.get() & 0xFF) << 16)
                | ((buffer.get() & 0xFF) << 8)
                | (buffer.get() & 0xFF);
        return new IntNode(value);
    }

    /**
     * 解析double类型的数据。len 字节是 8 字节大端位模式的首字节。
     *
     * @param firstByte 首个数据字节（即控制区的 len 字节）
     * @return JsonNode
     */
    private JsonNode parseDouble(int firstByte) {
        long bits = ((long) (firstByte & 0xFF) << 56) | buffer2Long(7);
        return new DoubleNode(Double.longBitsToDouble(bits));
    }

    /**
     * 解析float类型的数据。len 字节是 4 字节大端位模式的首字节。
     *
     * @param firstByte 首个数据字节（即控制区的 len 字节）
     * @return JsonNode
     */
    private JsonNode parseFloat(int firstByte) {
        int bits = ((firstByte & 0xFF) << 24)
                | ((buffer.get() & 0xFF) << 16)
                | ((buffer.get() & 0xFF) << 8)
                | (buffer.get() & 0xFF);
        return new FloatNode(Float.intBitsToFloat(bits));
    }

    /**
     * 解析无符号整型
     *
     * @param len 数据长度
     * @return 无符号整型
     */
    private long buffer2Long(int len) {
        long num = 0;
        for (int i = 0; i < len; i++) {
            num = (num << 8) | (buffer.get() & 0xFF);
        }
        return num;
    }

    /**
     * 解析有符号整型
     *
     * @param buffer 缓存器
     * @param base   基准位置
     * @param len    数据长度
     * @return 有符号整型
     */
    protected static int buffer2Integer(ByteBuffer buffer, int base, int len) {
        int num = base;
        for (int i = 0; i < len; i++) {
            num = (num << 8) | (buffer.get() & 0xFF);
        }
        return num;
    }
}