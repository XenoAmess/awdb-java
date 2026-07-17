package com.xenoamess.ipplus360;

import com.fasterxml.jackson.databind.JsonNode;
import com.xenoamess.ipplus360.enumerate.FileOpenMode;
import com.xenoamess.ipplus360.fixture.AwdbTestFixture;
import com.xenoamess.ipplus360.impl.AwdbCacheImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * >2GB 大文件端到端测试：用稀疏文件（sparse file）把库填充到 2GB 以上，
 * 数据只在文件头，磁盘占用极低，CI 可跑。覆盖 AwdbBufferHolder 的大文件
 * 决策、LargeFileBuffer 多 segment、AwdbReader/AwdbDataParserLarge 大文件分支。
 */
class AwdbReaderSparseFileTest {

    @TempDir
    Path tempDir;

    private Path sparseDb() throws IOException {
        URL url = AwdbReaderSparseFileTest.class.getResource("/" + AwdbTestFixture.STRUCTURED_FILE);
        assertNotNull(url);
        byte[] fixture;
        try {
            fixture = Files.readAllBytes(Paths.get(url.toURI()));
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }

        Path p = tempDir.resolve("sparse.awdb");
        try (FileChannel channel = FileChannel.open(p,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(fixture));
            // 稀疏扩展：truncate 只能缩小不能扩大；在末尾定位写 1 字节制造稀疏大文件
            channel.write(ByteBuffer.wrap(new byte[]{0}), (long) Integer.MAX_VALUE + 1024 * 1024 - 1);
        }
        return p;
    }

    @Test
    void lookupInFileLargerThan2GB() throws IOException {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("linux"),
                "稀疏文件 mmap 依赖 Linux 文件系统");

        Path db = sparseDb();
        assertTrue(Files.size(db) > Integer.MAX_VALUE);

        try (AwdbReader reader = AwdbReader.open(db.toFile(), new AwdbCacheImpl(), FileOpenMode.MEMORY_MAPPED)) {
            assertTrue(reader.getAwdbMetaData().getNodeCount() > 0);

            JsonNode a = reader.findIpLocation("202.96.128.86");
            assertEquals("中国", a.get("country").asText());
            assertEquals("天河", a.get("multiAreas").get(0).get("district").asText());

            JsonNode b = reader.findIpLocation("1.2.3.4");
            assertEquals("美国", b.get("country").asText());

            JsonNode c = reader.findIpLocation("224.0.0.9");
            assertEquals("类型样本", c.get("country").asText());
            assertEquals(65535L, c.get("city").asLong());

            assertEquals(0, reader.findIpLocation("64.0.0.1").size());
        }
    }

    @Test
    void lookupInSparseMixedModeFile(@TempDir Path tempDir2) throws IOException {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("linux"),
                "稀疏文件 mmap 依赖 Linux 文件系统");

        Path p = sparseCopy(tempDir2, AwdbTestFixture.MIXED_FILE, "sparse_mixed.awdb",
                (long) Integer.MAX_VALUE + 2 * 1024 * 1024);

        try (AwdbReader reader = AwdbReader.open(p.toFile(), new AwdbCacheImpl(), FileOpenMode.MEMORY)) {
            // 4_6 混合库大文件分支：v4 经 96 位预走
            assertEquals("中国", reader.findIpLocation("202.96.128.86").get("country").asText());
            assertEquals("IPv6专线", reader.findIpLocation("8001::1").get("isp").asText());
        }
    }

    @Test
    void directDecodeInSparseFile(@TempDir Path tempDir3) throws IOException {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("linux"),
                "稀疏文件 mmap 依赖 Linux 文件系统");

        Path p = sparseCopy(tempDir3, AwdbTestFixture.DIRECT_FILE, "sparse_decode2.awdb",
                (long) Integer.MAX_VALUE + 3 * 1024 * 1024);

        try (AwdbReader reader = AwdbReader.open(p.toFile(), new AwdbCacheImpl(), FileOpenMode.MEMORY_MAPPED)) {
            // decodeType=2 大文件直解码分支
            JsonNode node = reader.findIpLocation("202.96.128.86");
            assertEquals("中国", node.get("country").asText());
            assertEquals("电信", node.get("isp").asText());
        }
    }

    private Path sparseCopy(Path dir, String resourceName, String outName, long extendTo) throws IOException {
        URL url = AwdbReaderSparseFileTest.class.getResource("/" + resourceName);
        assertNotNull(url);
        byte[] fixture;
        try {
            fixture = Files.readAllBytes(Paths.get(url.toURI()));
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        Path p = dir.resolve(outName);
        try (FileChannel channel = FileChannel.open(p,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(fixture));
            channel.write(ByteBuffer.wrap(new byte[]{0}), extendTo - 1);
        }
        return p;
    }
}
