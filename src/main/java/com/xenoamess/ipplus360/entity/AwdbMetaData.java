package com.xenoamess.ipplus360.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.Serializable;
import java.util.List;

/**
 * awdb 文件元数据
 */
public class AwdbMetaData implements Serializable {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 节点总数
     */
    private long nodeCount;

    /**
     * IP版本（4,6,4_6）
     */
    private String ipVersion;

    /**
     * 解析类型（1,2...）
     */
    private int decodeType;

    /**
     * 字节长度（4,5...）
     */
    private int byteLen;

    /**
     * 语言
     */
    private String languages;

    /**
     * 文件名称
     */
    private String fileName;

    /**
     * 生成时间
     */
    private String createTime;

    /**
     * 公司标识
     */
    private String companyId;

    /**
     * 开始长度
     */
    private long startLength;

    /**
     * 开始偏移量
     */
    private long baseOffset;

    /**
     * 字段列表（元素为 String；multiAreas 场景下最后一项为子字段名 List&lt;String&gt;）
     */
    private List<Object> columns;

    public AwdbMetaData(JsonNode metaJson, long startLen) {
        this.nodeCount = metaJson.path("node_count").asLong();
        this.ipVersion = metaJson.path("ip_version").asText();
        this.decodeType = metaJson.path("decode_type").asInt();
        this.byteLen = metaJson.path("byte_len").asInt();
        this.languages = metaJson.path("languages").asText();
        this.fileName = metaJson.path("file_name").asText();
        this.createTime = metaJson.path("create_time").asText();
        this.companyId = metaJson.path("company_id").asText();
        this.startLength = startLen;
        this.baseOffset = nodeCount * byteLen * 2 + startLen;
        this.columns = OBJECT_MAPPER.convertValue(metaJson.required("columns"), new TypeReference<List<Object>>() {
        });
    }

    public long getNodeCount() {
        return nodeCount;
    }

    public void setNodeCount(long nodeCount) {
        this.nodeCount = nodeCount;
    }

    public String getIpVersion() {
        return ipVersion;
    }

    public void setIpVersion(String ipVersion) {
        this.ipVersion = ipVersion;
    }

    public int getDecodeType() {
        return decodeType;
    }

    public void setDecodeType(int decodeType) {
        this.decodeType = decodeType;
    }

    public int getByteLen() {
        return byteLen;
    }

    public void setByteLen(int byteLen) {
        this.byteLen = byteLen;
    }

    public String getLanguages() {
        return languages;
    }

    public void setLanguages(String languages) {
        this.languages = languages;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getCompanyId() {
        return companyId;
    }

    public void setCompanyId(String companyId) {
        this.companyId = companyId;
    }

    public long getStartLength() {
        return startLength;
    }

    public void setStartLength(long startLength) {
        this.startLength = startLength;
    }

    public long getBaseOffset() {
        return baseOffset;
    }

    public void setBaseOffset(long baseOffset) {
        this.baseOffset = baseOffset;
    }

    public List<Object> getColumns() {
        return columns;
    }

    public void setColumns(List<Object> columns) {
        this.columns = columns;
    }
}
