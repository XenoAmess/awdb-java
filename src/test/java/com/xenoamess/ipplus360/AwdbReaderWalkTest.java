package com.xenoamess.ipplus360;

import com.fasterxml.jackson.databind.JsonNode;
import com.xenoamess.ipplus360.enumerate.FileOpenMode;
import com.xenoamess.ipplus360.fixture.AwdbTestFixture;
import com.xenoamess.ipplus360.impl.AwdbCacheImpl;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AwdbReader.walk 全量遍历测试。
 */
class AwdbReaderWalkTest {

    private static final class Entry {
        final String prefix;
        final JsonNode record;

        Entry(byte[] address, int prefixLength, JsonNode record) {
            this.prefix = toPrefixString(address, prefixLength);
            this.record = record;
        }
    }

    private static File resource(String name) {
        URL url = AwdbReaderWalkTest.class.getResource("/" + name);
        assertNotNull(url);
        try {
            return new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<Entry> walk(String fileName) throws IOException {
        List<Entry> entries = new ArrayList<>();
        try (AwdbReader reader = AwdbReader.open(resource(fileName), new AwdbCacheImpl(), FileOpenMode.MEMORY)) {
            reader.walk((address, prefixLength, record) -> entries.add(new Entry(address, prefixLength, record)));
        }
        return entries;
    }

    /** 前缀字节数组转 "hex/len" 可读形式（仅用于断言） */
    private static String toPrefixString(byte[] address, int prefixLength) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < prefixLength; i++) {
            sb.append((address[i / 8] >> (7 - i % 8)) & 1);
        }
        return sb + "/" + prefixLength;
    }

    private static JsonNode findByLongestMatch(List<Entry> entries, int[] bits) {
        Entry best = null;
        for (Entry e : entries) {
            int len = Integer.parseInt(e.prefix.substring(e.prefix.indexOf('/') + 1));
            if (len > bits.length) {
                continue;
            }
            String bitStr = e.prefix.substring(0, e.prefix.indexOf('/'));
            boolean match = true;
            for (int i = 0; i < len; i++) {
                if (bits[i] != bitStr.charAt(i) - '0') {
                    match = false;
                    break;
                }
            }
            if (match && (best == null || len > Integer.parseInt(best.prefix.substring(best.prefix.indexOf('/') + 1)))) {
                best = e;
            }
        }
        return best == null ? null : best.record;
    }

    private static int[] ipBits(String ip) {
        try {
            byte[] raw = InetAddress.getByName(ip).getAddress();
            int[] bits = new int[raw.length * 8];
            for (int i = 0; i < bits.length; i++) {
                bits[i] = (raw[i / 8] >> (7 - i % 8)) & 1;
            }
            return bits;
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Test
    void structuredWalkYieldsThreePrefixes() throws IOException {
        List<Entry> entries = walk(AwdbTestFixture.STRUCTURED_FILE);
        assertEquals(3, entries.size());
        assertTrue(entries.stream().anyMatch(e -> e.prefix.equals("00/2") && "美国".equals(e.record.get("country").asText())));
        assertTrue(entries.stream().anyMatch(e -> e.prefix.equals("110/3") && "中国".equals(e.record.get("country").asText())));
        assertTrue(entries.stream().anyMatch(e -> e.prefix.equals("111/3") && "类型样本".equals(e.record.get("country").asText())));
    }

    @Test
    void typesWalkMergesToRootPrefix() throws IOException {
        // node0 双子节点同值 → 合并到 /0
        List<Entry> entries = walk(AwdbTestFixture.TYPES_FILE);
        assertEquals(1, entries.size());
        assertEquals("/0", entries.get(0).prefix);
        assertEquals(-1, entries.get(0).record.get("i").asInt());
    }

    @Test
    void mixedWalkCoversV4AndV6() throws IOException {
        List<Entry> entries = walk(AwdbTestFixture.MIXED_FILE);
        assertEquals(3, entries.size());
        // v6 记录挂在 1/1
        assertTrue(entries.stream().anyMatch(e -> e.prefix.equals("1/1") && "IPv6专线".equals(e.record.get("isp").asText())));
        // v4 记录挂在 ::ffff:0/96 之下（96 位链 + 2 个 v4 位 → /98）
        assertTrue(entries.stream().anyMatch(e -> e.prefix.endsWith("/98") && "美国".equals(e.record.get("country").asText())));
        assertTrue(entries.stream().anyMatch(e -> e.prefix.endsWith("/98") && "中国".equals(e.record.get("country").asText())));
    }

    @Test
    void directWalkDecodesTabRecords() throws IOException {
        List<Entry> entries = walk(AwdbTestFixture.DIRECT_FILE);
        assertEquals(2, entries.size());
        assertTrue(entries.stream().anyMatch(e -> "中国".equals(e.record.get("country").asText())));
        assertTrue(entries.stream().anyMatch(e -> "美国".equals(e.record.get("country").asText())));
    }

    @Test
    void walkConsistentWithPointLookup() throws IOException {
        // 文件 -> 树位长：v4 文件 32 位树，v6/4_6 文件 128 位树
        String[][] cases = {
                {AwdbTestFixture.STRUCTURED_FILE, "32"},
                {AwdbTestFixture.DIRECT_FILE, "32"},
                {AwdbTestFixture.TYPES_FILE, "32"},
                {AwdbTestFixture.MIXED_FILE, "128"},
                {AwdbTestFixture.V6_FILE, "128"},
        };
        for (String[] c : cases) {
            String file = c[0];
            int treeBits = Integer.parseInt(c[1]);
            List<Entry> entries = walk(file);
            for (String ip : new String[]{"202.96.128.86", "1.2.3.4", "224.0.0.9", "64.0.0.1", "8001::1", "::1"}) {
                byte[] raw = InetAddress.getByName(ip).getAddress();
                // 族不匹配（v4 文件查 v6、v6 文件查 v4）不在一致性范围内
                if (treeBits == 32 && raw.length == 16) {
                    continue;
                }
                if (treeBits == 128 && raw.length == 4 && !file.equals(AwdbTestFixture.MIXED_FILE)) {
                    continue;
                }
                // 128 位树中的 v4 查询需展开为 ::ffff: mapped 形式再匹配
                int[] bits = (treeBits == 128 && raw.length == 4) ? mappedBits(raw) : ipBits(ip);
                JsonNode viaWalk = findByLongestMatch(entries, bits);
                try (AwdbReader reader = AwdbReader.open(resource(file), new AwdbCacheImpl(), FileOpenMode.MEMORY)) {
                    JsonNode viaLookup = reader.findIpLocation(ip);
                    if (viaLookup == null || viaLookup.size() == 0) {
                        assertTrue(viaWalk == null, file + " " + ip + " 应未命中, walk=" + viaWalk);
                    } else {
                        assertNotNull(viaWalk, file + " " + ip + " walk 未覆盖");
                        assertEquals(viaLookup, viaWalk, file + " " + ip);
                    }
                }
            }
        }
    }

    /** v4 地址展开为 ::ffff:0/96 mapped 的 128 位 */
    private static int[] mappedBits(byte[] v4) {
        int[] bits = new int[128];
        for (int i = 80; i < 96; i++) {
            bits[i] = 1;
        }
        for (int i = 0; i < 32; i++) {
            bits[96 + i] = (v4[i / 8] >> (7 - i % 8)) & 1;
        }
        return bits;
    }
}
