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
 * 数值类型（INT/FLOAT/DOUBLE/UINT）与 flat columns（mapKeyValue 的 == 分支）测试。
 */
class AwdbReaderTypesTest {

    private static JsonNode lookup() throws IOException {
        URL url = AwdbReaderTypesTest.class.getResource("/" + AwdbTestFixture.TYPES_FILE);
        assertNotNull(url);
        try {
            File file = new File(url.toURI());
            try (AwdbReader reader = AwdbReader.open(file, new AwdbCacheImpl(), FileOpenMode.MEMORY)) {
                return reader.findIpLocation("203.0.113.7");
            }
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void numericTypesParsedCorrectly() throws IOException {
        JsonNode node = lookup();
        assertEquals(-1, node.get("i").asInt());
        assertEquals(1.5f, node.get("f").floatValue(), 0.0001f);
        assertEquals(-2.5d, node.get("d").doubleValue(), 0.0000001d);
        assertEquals(65535L, node.get("u").asLong());
        assertEquals("x", node.get("s").asText());
    }
}
