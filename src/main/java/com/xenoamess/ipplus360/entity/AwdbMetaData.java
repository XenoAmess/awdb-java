package com.xenoamess.ipplus360.entity;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.Serializable;

/**
 * awdb 文件元数据
 */
public class AwdbMetaData implements Serializable {
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
     * 字段列表
     */
    private JSONArray columns;

    public AwdbMetaData(JSONObject metaJson, long startLen) {
        this.nodeCount = metaJson.getLong("node_count");
        this.ipVersion = metaJson.getString("ip_version");
        this.decodeType = metaJson.getInteger("decode_type");
        this.byteLen = metaJson.getInteger("byte_len");
        this.languages = metaJson.getString("languages");
        this.fileName = metaJson.getString("file_name");
        this.createTime = metaJson.getString("create_time");
        this.companyId = metaJson.getString("company_id");
        this.startLength = startLen;
        this.baseOffset = nodeCount * byteLen * 2 + startLen;
        this.columns = metaJson.getJSONArray("columns");
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

    public JSONArray getColumns() {
        return columns;
    }

    public void setColumns(JSONArray columns) {
        this.columns = columns;
    }
}
