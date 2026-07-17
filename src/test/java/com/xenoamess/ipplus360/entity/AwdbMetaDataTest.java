package com.xenoamess.ipplus360.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AwdbMetaData 构造与 accessor 测试。
 */
class AwdbMetaDataTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static AwdbMetaData meta() throws IOException {
        String json = "{"
                + "\"node_count\":4,"
                + "\"ip_version\":\"4\","
                + "\"decode_type\":1,"
                + "\"byte_len\":4,"
                + "\"languages\":\"CN\","
                + "\"file_name\":\"f.awdb\","
                + "\"create_time\":\"2026-07-17\","
                + "\"company_id\":\"test\","
                + "\"columns\":[\"country\",\"multiAreas\",[\"prov\",\"city\"]]"
                + "}";
        return new AwdbMetaData(MAPPER.readTree(json), 100);
    }

    @Test
    void constructorParsesFields() throws IOException {
        AwdbMetaData m = meta();
        assertEquals(4, m.getNodeCount());
        assertEquals("4", m.getIpVersion());
        assertEquals(1, m.getDecodeType());
        assertEquals(4, m.getByteLen());
        assertEquals("CN", m.getLanguages());
        assertEquals("f.awdb", m.getFileName());
        assertEquals("2026-07-17", m.getCreateTime());
        assertEquals("test", m.getCompanyId());
        assertEquals(100, m.getStartLength());
        // baseOffset = nodeCount * byteLen * 2 + startLength
        assertEquals(4 * 4 * 2 + 100, m.getBaseOffset());
        List<Object> columns = m.getColumns();
        assertEquals(3, columns.size());
        assertEquals("country", columns.get(0));
        assertEquals(Arrays.asList("prov", "city"), columns.get(2));
    }

    @Test
    void settersRoundTrip() throws IOException {
        AwdbMetaData m = meta();
        m.setNodeCount(9);
        m.setIpVersion("6");
        m.setDecodeType(2);
        m.setByteLen(5);
        m.setLanguages("EN");
        m.setFileName("g.awdb");
        m.setCreateTime("t");
        m.setCompanyId("c2");
        m.setStartLength(7);
        m.setBaseOffset(8);
        m.setColumns(Arrays.asList("a"));
        assertEquals(9, m.getNodeCount());
        assertEquals("6", m.getIpVersion());
        assertEquals(2, m.getDecodeType());
        assertEquals(5, m.getByteLen());
        assertEquals("EN", m.getLanguages());
        assertEquals("g.awdb", m.getFileName());
        assertEquals("t", m.getCreateTime());
        assertEquals("c2", m.getCompanyId());
        assertEquals(7, m.getStartLength());
        assertEquals(8, m.getBaseOffset());
        assertEquals(Arrays.asList("a"), m.getColumns());
    }
}
