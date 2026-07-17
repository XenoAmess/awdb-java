package com.xenoamess.ipplus360;

import com.fasterxml.jackson.databind.JsonNode;
import com.xenoamess.ipplus360.enumerate.FileOpenMode;
import com.xenoamess.ipplus360.fixture.AwdbTestFixture;
import com.xenoamess.ipplus360.impl.AwdbCacheImpl;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * ip_version=4_6 混合库测试：v4 查询经 ::ffff:0/96 预走后命中，
 * v6 查询直接遍历。覆盖 AwdbReader 的 IPv4-mapped 转换分支。
 */
class AwdbReaderMixedTest {

    private static File resource() {
        URL url = AwdbReaderMixedTest.class.getResource("/" + AwdbTestFixture.MIXED_FILE);
        assertNotNull(url);
        try {
            return new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JsonNode lookup(FileOpenMode mode, String ip) throws IOException {
        try (AwdbReader reader = AwdbReader.open(resource(), new AwdbCacheImpl(), mode)) {
            return reader.findIpLocation(ip);
        }
    }

    @Test
    void ipv4LookupHitsV4Record() throws IOException {
        JsonNode node = lookup(FileOpenMode.MEMORY, "202.96.128.86");
        assertEquals("中国", node.get("country").asText());
        assertEquals("广州", node.get("city").asText());
        assertEquals(2, node.get("multiAreas").size());
    }

    @Test
    void ipv4LookupHitsSecondV4Record() throws IOException {
        JsonNode node = lookup(FileOpenMode.MEMORY, "1.2.3.4");
        assertEquals("美国", node.get("country").asText());
        assertEquals("Comcast", node.get("isp").asText());
    }

    @Test
    void ipv4LookupMemoryMapped() throws IOException {
        assertEquals("中国", lookup(FileOpenMode.MEMORY_MAPPED, "202.96.128.86").get("country").asText());
    }

    @Test
    void ipv4NotFoundInMixedFile() throws IOException {
        assertEquals(0, lookup(FileOpenMode.MEMORY, "64.0.0.1").size());
        assertEquals(0, lookup(FileOpenMode.MEMORY, "128.0.0.1").size());
    }

    @Test
    void ipv6LookupHitsV6Record() throws IOException {
        JsonNode node = lookup(FileOpenMode.MEMORY, "8001::1");
        assertEquals("中国", node.get("country").asText());
        assertEquals("上海", node.get("city").asText());
        assertEquals("IPv6专线", node.get("isp").asText());
    }

    @Test
    void ipv6NotFoundInMixedFile() throws IOException {
        assertEquals(0, lookup(FileOpenMode.MEMORY, "::1").size());
    }

    @Test
    void textualIpv4MappedAddressNormalizedToV4Path() throws IOException {
        // "::ffff:1.2.3.4" 在多数 JDK 被归一化为 Inet4Address，应走 v4 预走分支命中 B
        JsonNode node = lookup(FileOpenMode.MEMORY, "::ffff:1.2.3.4");
        assertEquals("美国", node.get("country").asText());
    }
}
