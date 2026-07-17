package com.xenoamess.ipplus360;

import com.fasterxml.jackson.databind.JsonNode;
import com.xenoamess.ipplus360.enumerate.FileOpenMode;
import com.xenoamess.ipplus360.fixture.AwdbTestFixture;
import com.xenoamess.ipplus360.impl.AwdbCacheImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 畸形输入与边界场景的鲁棒性测试。
 */
class AwdbReaderErrorHandlingTest {

    private static File resource(String name) {
        URL url = AwdbReaderErrorHandlingTest.class.getResource("/" + name);
        assertNotNull(url);
        try {
            return new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void invalidIpStringReturnsNull() throws IOException {
        try (AwdbReader reader = AwdbReader.open(resource(AwdbTestFixture.STRUCTURED_FILE),
                new AwdbCacheImpl(), FileOpenMode.MEMORY)) {
            assertNull(reader.findIpLocation("not-an-ip"));
            assertNull(reader.findIpLocation("999.999.999.999"));
        }
    }

    @Test
    void nullAndEmptyIpReturnNull() throws IOException {
        try (AwdbReader reader = AwdbReader.open(resource(AwdbTestFixture.STRUCTURED_FILE),
                new AwdbCacheImpl(), FileOpenMode.MEMORY)) {
            assertNull(reader.findIpLocation((String) null));
            assertNull(reader.findIpLocation(""));
        }
    }

    @Test
    void missingFileThrowsIOException(@TempDir Path tempDir) {
        File missing = tempDir.resolve("no-such.awdb").toFile();
        assertThrows(IOException.class,
                () -> AwdbReader.open(missing, new AwdbCacheImpl(), FileOpenMode.MEMORY));
    }

    @Test
    void truncatedFileFailsToOpen(@TempDir Path tempDir) throws IOException {
        byte[] full = Files.readAllBytes(resource(AwdbTestFixture.STRUCTURED_FILE).toPath());
        Path truncated = tempDir.resolve("truncated.awdb");
        Files.write(truncated, Arrays.copyOf(full, 50));
        // meta JSON 不完整，打开即应失败（解析异常），而不是静默给出错误结果
        assertThrows(Exception.class,
                () -> AwdbReader.open(truncated.toFile(), new AwdbCacheImpl(), FileOpenMode.MEMORY));
    }

    @Test
    void ipv6RejectedByV4File() throws IOException {
        try (AwdbReader reader = AwdbReader.open(resource(AwdbTestFixture.STRUCTURED_FILE),
                new AwdbCacheImpl(), FileOpenMode.MEMORY)) {
            assertEquals(0, reader.findIpLocation("2001:db8::1").size());
        }
    }

    @Test
    void cachePropagatesLoaderIOException() throws IOException {
        AwdbCacheImpl cache = new AwdbCacheImpl();
        IOException boom = new IOException("boom");
        IOException thrown = assertThrows(IOException.class,
                () -> cache.get(k -> {
                    throw boom;
                }, 42L));
        assertEquals("boom", thrown.getMessage());
        // 失败结果不缓存：下次应重新调用 loader
        JsonNode value = cache.get(k -> null, 42L);
        assertNull(value);
    }
}
