package com.xenoamess.ipplus360;

import com.fasterxml.jackson.databind.JsonNode;
import com.xenoamess.ipplus360.entity.AwdbMetaData;
import com.xenoamess.ipplus360.exception.InvalidAwdbException;
import com.xenoamess.ipplus360.fixture.AwdbTestFixture;
import com.xenoamess.ipplus360.impl.AwdbCacheImpl;
import com.xenoamess.ipplus360.impl.AwdbNoCacheImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AwdbDataParserLarge 直测：用小文件构造 LargeFileBuffer，绕过
 * AwdbBufferHolder 的 >2GB 门槛，直接锁定大文件解析路径的当前行为。
 */
class AwdbDataParserLargeTest {

    @TempDir
    Path tempDir;

    private LargeFileBuffer largeBufferOf(byte[] bytes) throws IOException {
        Path p = tempDir.resolve("large.awdb");
        Files.write(p, bytes);
        FileChannel channel = FileChannel.open(p, StandardOpenOption.READ);
        return new LargeFileBuffer(channel, bytes.length);
    }

    private static byte[] fixtureBytes() {
        URL url = AwdbDataParserLargeTest.class.getResource("/" + AwdbTestFixture.STRUCTURED_FILE);
        assertNotNull(url);
        try {
            return Files.readAllBytes(Paths.get(url.toURI()));
        } catch (URISyntaxException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void parseMetaAndStructuredRecord() throws IOException {
        LargeFileBuffer buf = largeBufferOf(fixtureBytes());
        buf.position(0);
        int metaLen = buf.getChar() & 0xFFFF;

        AwdbCacheImpl cache = new AwdbCacheImpl();
        AwdbDataParserLarge metaParser = new AwdbDataParserLarge(cache, buf);
        AwdbMetaData meta = metaParser.parseMeta(metaLen);
        assertEquals(4, meta.getNodeCount());
        assertEquals(1, meta.getDecodeType());

        // 记录 A 位于 baseOffset（leaf = nodeCount + 10 + 0）
        AwdbDataParserLarge parser = new AwdbDataParserLarge(cache, buf, meta.getBaseOffset());
        JsonNode recA = parser.parseData(meta.getBaseOffset());
        assertTrue(recA.isArray());
        assertEquals("中国", recA.get(0).asText());
        assertEquals("电信", recA.get(3).asText());
        JsonNode areas = recA.get(4);
        assertEquals(2, areas.size());
        assertEquals("天河", areas.get(0).get(2).asText());
    }

    @Test
    void pointerRecordResolves() throws IOException {
        LargeFileBuffer buf = largeBufferOf(fixtureBytes());
        buf.position(0);
        int metaLen = buf.getChar() & 0xFFFF;
        AwdbCacheImpl cache = new AwdbCacheImpl();
        AwdbMetaData meta = new AwdbDataParserLarge(cache, buf).parseMeta(metaLen);

        AwdbDataParserLarge parser = new AwdbDataParserLarge(cache, buf, meta.getBaseOffset());
        // 记录 C 紧随 A 之后（见 AwdbTestFixture.structuredV4 布局）
        JsonNode recC = parser.parseData(meta.getBaseOffset() + AwdbTestFixture.structuredRecALength());
        assertTrue(recC.isArray());
        assertEquals("类型样本", recC.get(0).asText());
        assertEquals(65535L, recC.get(2).asLong());
        // POINTER 指回记录 A
        assertTrue(recC.get(3).isArray());
        assertEquals("中国", recC.get(3).get(0).asText());
    }

    @Test
    void outOfBoundsOffsetThrows() throws IOException {
        LargeFileBuffer buf = largeBufferOf(fixtureBytes());
        AwdbDataParserLarge parser = new AwdbDataParserLarge(AwdbNoCacheImpl.getInstance(), buf);
        assertThrows(InvalidAwdbException.class, () -> parser.parseData(-1));
        assertThrows(InvalidAwdbException.class, () -> parser.parseData(buf.capacity() + 100));
    }

    @Test
    void unknownTypeByteThrows() throws IOException {
        LargeFileBuffer buf = largeBufferOf(new byte[]{0x7E, 0x00});
        AwdbDataParserLarge parser = new AwdbDataParserLarge(AwdbNoCacheImpl.getInstance(), buf);
        assertThrows(InvalidAwdbException.class, () -> parser.parseData(0));
    }

    @Test
    void outOfRangePointerReturnsNull() throws IOException {
        // type=POINTER(2), len=4, offset=0xFFFFFFFF 远超 capacity → 大文件实现返回 null（锁定漂移行为）
        LargeFileBuffer buf = largeBufferOf(new byte[]{0x02, 0x04, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
        AwdbDataParserLarge parser = new AwdbDataParserLarge(AwdbNoCacheImpl.getInstance(), buf);
        assertNull(parser.parseData(0));
    }

    @Test
    void truncatedStringReturnsNull() throws IOException {
        // type=STRING(3), len=10, 但只有 2 字节负载 → 大文件实现返回 null
        LargeFileBuffer buf = largeBufferOf(new byte[]{0x03, 0x0A, 'a', 'b'});
        AwdbDataParserLarge parser = new AwdbDataParserLarge(AwdbNoCacheImpl.getInstance(), buf);
        assertNull(parser.parseData(0));
    }
}
