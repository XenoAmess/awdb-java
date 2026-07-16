package com.xenoamess.ipplus360;

import com.fasterxml.jackson.databind.JsonNode;
import com.xenoamess.ipplus360.entity.AwdbMetaData;
import com.xenoamess.ipplus360.enumerate.FileOpenMode;
import com.xenoamess.ipplus360.fixture.AwdbTestFixture;
import com.xenoamess.ipplus360.impl.AwdbCacheImpl;
import com.xenoamess.ipplus360.impl.AwdbNoCacheImpl;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AwdbReader 端到端测试，样本为 AwdbTestFixture 合成的固化文件。
 * 合成样本以代码自身为 ground truth，仅保证回归，不代表与官方真实库兼容。
 */
class AwdbReaderTest {

    private static File resource(String name) {
        URL url = AwdbReaderTest.class.getResource("/" + name);
        assertNotNull(url, "测试资源不存在: " + name);
        try {
            return new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JsonNode lookup(String fileName, FileOpenMode mode, String ip) throws IOException {
        try (AwdbReader reader = AwdbReader.open(resource(fileName), new AwdbCacheImpl(), mode)) {
            return reader.findIpLocation(ip);
        }
    }

    /* ---------------- decode_type=1 结构化 ---------------- */

    private static void assertStructuredSample(JsonNode node) {
        assertEquals("中国", node.get("country").asText());
        assertEquals("广东", node.get("province").asText());
        assertEquals("广州", node.get("city").asText());
        assertEquals("电信", node.get("isp").asText());
        JsonNode areas = node.get("multiAreas");
        assertTrue(areas.isArray());
        assertEquals(2, areas.size());
        assertEquals("广东", areas.get(0).get("prov").asText());
        assertEquals("广州", areas.get(0).get("city").asText());
        assertEquals("天河", areas.get(0).get("district").asText());
        assertEquals("深圳", areas.get(1).get("city").asText());
        assertEquals("南山", areas.get(1).get("district").asText());
    }

    @Test
    void structuredLookupMemory() throws IOException {
        assertStructuredSample(lookup(AwdbTestFixture.STRUCTURED_FILE, FileOpenMode.MEMORY, "202.96.128.86"));
    }

    @Test
    void structuredLookupMemoryMapped() throws IOException {
        assertStructuredSample(lookup(AwdbTestFixture.STRUCTURED_FILE, FileOpenMode.MEMORY_MAPPED, "202.96.128.86"));
    }

    @Test
    void structuredLookupWithEmptyMultiAreas() throws IOException {
        JsonNode node = lookup(AwdbTestFixture.STRUCTURED_FILE, FileOpenMode.MEMORY, "1.2.3.4");
        assertEquals("美国", node.get("country").asText());
        assertEquals("加利福尼亚", node.get("province").asText());
        assertEquals("Comcast", node.get("isp").asText());
        assertTrue(node.get("multiAreas").isArray());
        assertEquals(0, node.get("multiAreas").size());
    }

    @Test
    void structuredTypeCoverage() throws IOException {
        JsonNode node = lookup(AwdbTestFixture.STRUCTURED_FILE, FileOpenMode.MEMORY, "224.0.0.9");
        assertEquals("类型样本", node.get("country").asText());
        assertTrue(node.get("province").asText().startsWith("这是一段用于覆盖 TEXT 类型的长文本"));
        assertEquals(65535L, node.get("city").asLong());
        // POINTER 指向记录 A（整个数组）
        JsonNode isp = node.get("isp");
        assertTrue(isp.isArray());
        assertEquals("中国", isp.get(0).asText());
        assertEquals("电信", isp.get(3).asText());
        assertTrue(node.get("multiAreas").isArray());
        assertEquals(0, node.get("multiAreas").size());
    }

    @Test
    @Disabled("Bug A: findIpLocation(String) 对未命中结果无条件 mapKeyValue 导致 CCE/NPE，待 P1 修复")
    void structuredNotFoundReturnsEmptyNode() throws IOException {
        // 64.x -> node1 bit1=1 -> nodeCount; 128.x -> node2 bit1=0 -> nodeCount
        assertEquals(0, lookup(AwdbTestFixture.STRUCTURED_FILE, FileOpenMode.MEMORY, "64.0.0.1").size());
        assertEquals(0, lookup(AwdbTestFixture.STRUCTURED_FILE, FileOpenMode.MEMORY, "128.0.0.1").size());
    }

    @Test
    void stringAndInetAddressApiShapes() throws IOException {
        try (AwdbReader reader = AwdbReader.open(resource(AwdbTestFixture.STRUCTURED_FILE),
                AwdbNoCacheImpl.getInstance(), FileOpenMode.MEMORY)) {
            // String API 返回 key-value 映射对象；InetAddress API 返回原始记录数组
            JsonNode byString = reader.findIpLocation("202.96.128.86");
            JsonNode byAddr = reader.findIpLocation(InetAddress.getByName("202.96.128.86"));
            assertEquals("中国", byString.get("country").asText());
            assertTrue(byAddr.isArray());
            assertEquals("中国", byAddr.get(0).asText());
            assertEquals("电信", byAddr.get(3).asText());
        }
    }

    @Test
    void metaDataExposed() throws IOException {
        try (AwdbReader reader = AwdbReader.open(resource(AwdbTestFixture.STRUCTURED_FILE),
                new AwdbCacheImpl(), FileOpenMode.MEMORY)) {
            AwdbMetaData meta = reader.getAwdbMetaData();
            assertEquals(4, meta.getNodeCount());
            assertEquals("4", meta.getIpVersion());
            assertEquals(1, meta.getDecodeType());
            assertEquals(4, meta.getByteLen());
            assertEquals("xenoamess-test", meta.getCompanyId());
            assertEquals(6, meta.getColumns().size());
        }
    }

    /* ---------------- decode_type=2 直解码 ---------------- */

    private static void assertDirectSample(JsonNode node) {
        assertEquals("中国", node.get("country").asText());
        assertEquals("广东", node.get("province").asText());
        assertEquals("广州", node.get("city").asText());
        assertEquals("电信", node.get("isp").asText());
    }

    @Test
    @Disabled("Bug B: decodeType=2 结果被 decodeContentDirect 映射后又被 String API 二次 mapKeyValue 成全 null，待 P1 修复")
    void directLookupMemory() throws IOException {
        assertDirectSample(lookup(AwdbTestFixture.DIRECT_FILE, FileOpenMode.MEMORY, "202.96.128.86"));
        JsonNode node = lookup(AwdbTestFixture.DIRECT_FILE, FileOpenMode.MEMORY, "1.2.3.4");
        assertEquals("美国", node.get("country").asText());
        assertEquals("Google", node.get("isp").asText());
    }

    @Test
    @Disabled("MEMORY_MAPPED 下 decodeContentDirectSmall 调用 MappedByteBuffer.array() 必抛异常，待 P1 修复后启用")
    void directLookupMemoryMapped() throws IOException {
        assertDirectSample(lookup(AwdbTestFixture.DIRECT_FILE, FileOpenMode.MEMORY_MAPPED, "202.96.128.86"));
    }

    /* ---------------- IPv6 ---------------- */

    @Test
    @Disabled("Bug B: decodeType=2 结果被 decodeContentDirect 映射后又被 String API 二次 mapKeyValue 成全 null，待 P1 修复")
    void ipv6Lookup() throws IOException {
        JsonNode node = lookup(AwdbTestFixture.V6_FILE, FileOpenMode.MEMORY, "::1");
        assertEquals("中国", node.get("country").asText());
        assertEquals("上海", node.get("city").asText());
        assertEquals("IPv6专线", node.get("isp").asText());
    }

    @Test
    @Disabled("Bug A: findIpLocation(String) 对未命中结果无条件 mapKeyValue 导致 CCE/NPE，待 P1 修复")
    void ipv6NotFoundReturnsEmptyNode() throws IOException {
        assertEquals(0, lookup(AwdbTestFixture.V6_FILE, FileOpenMode.MEMORY, "8001::1").size());
    }

    @Test
    @Disabled("Bug A: findIpLocation(String) 对版本不匹配返回的空节点仍走 mapKeyValue 导致 CCE，待 P1 修复")
    void ipv4RejectedByV6File() throws IOException {
        // ip_version=6 的文件查询 IPv4 应返回空节点
        assertEquals(0, lookup(AwdbTestFixture.V6_FILE, FileOpenMode.MEMORY, "1.2.3.4").size());
    }
}
