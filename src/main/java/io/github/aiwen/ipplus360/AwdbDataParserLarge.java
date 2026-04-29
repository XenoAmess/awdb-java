package io.github.aiwen.ipplus360;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import io.github.aiwen.ipplus360.entity.AwdbMetaData;
import io.github.aiwen.ipplus360.enumerate.AwdbDataType;
import io.github.aiwen.ipplus360.exception.InvalidAwdbException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 支持大文件的 awdb 数据解析器
 */
class AwdbDataParserLarge {
    private static final Logger logger = LoggerFactory.getLogger(AwdbDataParserLarge.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final LargeFileBuffer buffer;

    private final AwdbNodeCache cache;

    private final CharsetDecoder strDecoder = StandardCharsets.UTF_8.newDecoder();

    private long basePointer = 0;

    public AwdbDataParserLarge(AwdbNodeCache cache, LargeFileBuffer buffer) {
        this.cache = cache;
        this.buffer = buffer;
    }

    public AwdbDataParserLarge(AwdbNodeCache cache, LargeFileBuffer buffer, long basePointer) {
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
    protected AwdbMetaData parseMeta(int length) throws CharacterCodingException {
        // 根据实际文件结构，元数据应该从偏移量2开始（因为前2字节是长度）
        long startLen = 2 + length;
        long oldLimit = buffer.limit();
        buffer.limit(startLen);
        // 跳过前2字节（长度）
        buffer.position(2);
        byte[] metaBytes = new byte[length];
        buffer.get(metaBytes);
        buffer.limit(oldLimit);

        
        String meta = new String(metaBytes, StandardCharsets.UTF_8);

        JSONObject metaJsonObj = JSONObject.parseObject(meta);
        return new AwdbMetaData(metaJsonObj, startLen);
    }

    /**
     * 解析数据
     *
     * @param offset 偏移量
     * @return JsonNode
     */
    protected JsonNode parseData(long offset) throws IOException {
        if (offset < 0 || offset >= buffer.capacity()) {
            throw new InvalidAwdbException(
                    "The AWDB file's data section contains bad data: pointer " + offset + " out of bounds [0, " + buffer.capacity() + "]");
        }

        // LargeFileBuffer每个线程使用独立副本，且底层读取已线程安全，无需同步
        buffer.position(offset);
        buffer.limit(buffer.capacity()); // 设置限制到文件末尾

        return parser();
    }

    /**
     * 解析器
     *
     * @return JsonNode
     */
    private JsonNode parser() throws IOException {
        if (buffer.remaining() < 2) {
            logger.warn("解析器：剩余数据不足，只有 {} 字节", buffer.remaining());
            throw new InvalidAwdbException("Not enough data for parser: only " + buffer.remaining() + " bytes remaining");
        }
        
        int typeByte = buffer.get() & 0xFF;
        AwdbDataType dataType = AwdbDataType.getDataType(typeByte);
        
        if (dataType == null) {
            return null;
        }
        
        int len = buffer.get() & 0xFF;

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
                return parseInt();
            case UINT:
                return parseUint(len);
            case FLOAT:
                return parseFloat();
            case DOUBLE:
                return parseDouble();
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
        long oldLimit = buffer.limit();
        try {
            // 指针长度至少为1字节
            if (len <= 0) {
                logger.warn("解析指针：无效的指针长度 {} 字节", len);
                return null;
            }

            buffer.limit(buffer.position() + len);

            // 检查剩余数据是否足够
            if (buffer.remaining() < len) {
                logger.warn("解析指针：剩余数据不足，预期 {} 字节，实际 {} 字节", len, buffer.remaining());
                return null;
            }
            
            long buf = buffer2Long(len); // 使用无符号解析，避免负数

            // 先校验偏移量的合理性，避免超大指针
            if (buf < 0 || buf > buffer.capacity()) {
                logger.warn("指针偏移量无效: {} (有效范围: [0, {}]), 跳过此数据",
                        buf, buffer.capacity() - 1);
                return null;
            }

            long pointer = basePointer + buf;

            // 验证指针的有效性，重点检查偏移计算是否合理
            if (pointer < 0 || pointer >= buffer.capacity()) {
                logger.warn("指针无效: {} (基指针: {}, 偏移: {}, 有效范围: [0, {}]), 跳过此数据",
                        pointer, basePointer, buf, buffer.capacity() - 1);
                return null;
            }
            
            long position = buffer.position();
            JsonNode jsonNode = cache.get(loader, pointer);
            buffer.position(position);
            return jsonNode;
        } catch (BufferUnderflowException e) {
            logger.warn("解析指针：缓冲器下溢，数据长度 {} 字节", len);
            return null;
        } finally {
            buffer.limit(oldLimit);
        }
    }

    /**
     * 解析字符串数据
     *
     * @param len 数据长度
     * @return JsonNode
     */
    private JsonNode parseString(int len) {
        long oldLimit = buffer.limit();
        try {
            // 验证长度的合理性
            if (len < 0 ) { // 限制最大长度
                logger.warn("解析字符串：无效的长度 {} 字节", len);
                return null;
            }
            
            // 检查剩余数据是否足够
            if (buffer.remaining() < len || buffer.position() + len > buffer.capacity()) {
                logger.warn("解析字符串：剩余数据不足，预期 {} 字节，实际剩余 {} 字节", len, buffer.remaining());
                return null;
            }
            
            buffer.limit(buffer.position() + len);
            byte[] bytes = new byte[len];
            buffer.get(bytes);
            String s = new String(bytes, StandardCharsets.UTF_8);
            buffer.limit(oldLimit);
            return new TextNode(s);
        } catch (BufferUnderflowException e) {
            logger.warn("解析字符串：缓冲器下溢，数据长度 {} 字节", len);
            buffer.limit(oldLimit); // 确保恢复原始限制
            return null;
        } catch (Exception e) {
            logger.warn("解析字符串：{}，数据长度 {} 字节", e.getMessage(), len);
            buffer.limit(oldLimit); // 确保恢复原始限制
            return null;
        }
    }

    /**
     * 解析长字符串数据
     *
     * @param len 数据长度
     * @return JsonNode
     */
    private JsonNode parseText(int len) {
        long oldLimit = buffer.limit();
        try {
            buffer.limit(buffer.position() + len);
            long dataLen = buffer2Long(len); // 使用无符号解析，接受更大范围
            buffer.limit(oldLimit);
            
            // 限制字符串最大长度，避免内存溢出
//            final int MAX_TEXT_LENGTH = 100000; // 限制为100KB
//            if (dataLen > MAX_TEXT_LENGTH) {
//                logger.warn("文本数据过长，限制为{}字节: {}", MAX_TEXT_LENGTH, dataLen);
//                dataLen = MAX_TEXT_LENGTH;
//            }
            
            // 确保剩余数据足够
            if (buffer.position() + dataLen > buffer.capacity() || dataLen < 0) {
                logger.warn("解析文本：无效长度或数据不足，预期 {} 字节，剩余 {} 字节", dataLen, buffer.capacity() - buffer.position());
                return null;
            }
            
            buffer.limit(buffer.position() + dataLen);
            byte[] bytes = new byte[(int) dataLen];
            buffer.get(bytes);
            String s = new String(bytes, StandardCharsets.UTF_8);
            buffer.limit(oldLimit);
            return new TextNode(s);
        } catch (BufferUnderflowException e) {
            logger.warn("解析文本：缓冲器下溢");
            buffer.limit(oldLimit); // 确保恢复原始限制
            return null;
        } catch (Exception e) {
            logger.warn("解析文本：{}", e.getMessage());
            buffer.limit(oldLimit); // 确保恢复原始限制
            return null;
        }
    }

    /**
     * 解析无符号整型数据
     *
     * @param len 数据长度
     * @return 无符号整型
     */
    private JsonNode parseUint(int len) {
        return new LongNode(buffer2Long(len));
    }

    /**
     * 解析有符号整型
     *
     * @return 有符号整型
     */
    private JsonNode parseInt() {
        buffer.position(buffer.position() - 1);
        return new IntNode(buffer2Integer(4));
    }

    /**
     * 解析double类型的数据
     *
     * @return double类型数据
     */
    private JsonNode parseDouble() {
        buffer.position(buffer.position() - 1);
        return new DoubleNode(buffer.getDouble());
    }

    /**
     * 解析float类型的数据
     *
     * @return float类型数据
     */
    private JsonNode parseFloat() {
        buffer.position(buffer.position() - 1);
        return new FloatNode(buffer.getFloat());
    }

    /**
     * 从指定偏移位置读取len个字节，解析为无符号整数
     *
     * @param offset 偏移位置
     * @param len 数据长度
     * @return 无符号整型
     */
    private long buffer2Long(long offset, int len) {
        try {
            long num = 0;
            // 验证 len 的合理性 - 允许更大的长度范围
            if (len <= 0 ) {
                logger.warn("解析无符号整数：无效长度 {} 字节", len);
                return -1;
            }

            // 读取指定长度的字节数据
            byte[] bytes = new byte[len];
            buffer.get(offset, bytes, 0, len);

            // 解析字节数据，处理字节序问题
            for (int i = 0; i < len; i++) {
                num = (num << 8) | (bytes[i] & 0xFF);
                // 超过8字节的数据截断，避免long类型溢出
                if (i >= 7) {
                    logger.warn("解析无符号整数：数据长度 {} 字节超过 long 类型存储范围，只取低8字节", len);
                    break;
                }
            }

            // 验证解析结果的合理性
            if (num < 0) {
                logger.warn("解析无符号整数：解析结果为负数 {}，长度 {} 字节", num, len);
                return -1;
            }

            return num;
        } catch (Exception e) {
            logger.warn("解析无符号整数异常: {}", e.getMessage());
            return -1;
        }
    }

    /**
     * 解析无符号整型
     *
     * @param len 数据长度
     * @return 无符号整型
     */
    private long buffer2Long(int len) {
        try {
            long num = 0;
            // 验证 len 的合理性 - 允许更大的长度范围
            if (len <= 0 ) {
                logger.warn("解析无符号整数：无效长度 {} 字节", len);
                return -1;
            }
            
            // 读取指定长度的字节数据
            byte[] bytes = new byte[len];
            buffer.get(bytes);
            
            // 解析字节数据，处理字节序问题
            for (int i = 0; i < len; i++) {
                num = (num << 8) | (bytes[i] & 0xFF);
                // 如果长度超过8字节，只取低8字节，避免long类型溢出
                if (i >= 7) {
                    logger.warn("解析无符号整数：数据长度 {} 字节超过 long 类型存储范围，只取低8字节", len);
                    break;
                }
            }
            
            // 验证解析结果的合理性
            if (num < 0) {
                logger.warn("解析无符号整数：解析结果为负数 {}，长度 {} 字节", num, len);
                return -1;
            }
            
            return num;
        } catch (BufferUnderflowException e) {
            logger.warn("解析无符号整数：缓冲器下溢，数据长度 {} 字节", len);
            // 重置位置到操作前的位置
            buffer.position(buffer.position() - len);
            return -1;
        }
    }

    /**
     * 解析有符号整型
     *
     * @param len 数据长度
     * @return 有符号整型
     */
    private int buffer2Integer(int len) {
        int num = 0;
        try {
            // 检查剩余数据是否足够
            if (buffer.remaining() < len || len <= 0 || len > 4) { // int最多4字节
              //  logger.warn("解析整数：无效长度或数据不足，预期 {} 字节，实际剩余 {} 字节", len, buffer.remaining());
                // 如果长度无效，尝试跳过处理
                buffer.position(buffer.position() + len);
                return 0;
            }
            
            for (int i = 0; i < len; i++) {
                num = (num << 8) | (buffer.get() & 0xFF);
            }
            return num;
        } catch (BufferUnderflowException e) {
            logger.warn("解析整数：缓冲器下溢，数据长度 {} 字节", len);
            return 0;
        }
    }
}
