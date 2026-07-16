package com.xenoamess.ipplus360.fixture;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 合成 awdb 测试文件生成器。
 *
 * <p>布局（以 AwdbReader/AwdbDataParser 的实现为准）：
 * <pre>
 * [0..1]                meta 长度（大端 2 字节）
 * [2..2+metaLen)        meta JSON（UTF-8）
 * 树区                   nodeCount 个节点，每节点 byteLen*2 字节（左右子节点各 byteLen，大端无符号）
 * 数据区                 起点 baseOffset = 2 + metaLen + nodeCount*byteLen*2
 * </pre>
 * 叶子节点值 = nodeCount + 10 + 记录相对 baseOffset 的偏移（对应 AwdbReader 的
 * pointer = baseOffset + nodeIndex - nodeCount - 10 公式）。
 *
 * <p>main 方法将三个合成文件固化到 src/test/resources：
 * <ul>
 *   <li>test_20260717.awdb —— decode_type=1（结构化），IPv4，含 multiAreas/POINTER/TEXT/UINT</li>
 *   <li>test_20260717_decode2.awdb —— decode_type=2（直解码），IPv4</li>
 *   <li>test_20260717_v6.awdb —— decode_type=2，IPv6</li>
 * </ul>
 */
public final class AwdbTestFixture {

    public static final String STRUCTURED_FILE = "test_20260717.awdb";
    public static final String DIRECT_FILE = "test_20260717_decode2.awdb";
    public static final String V6_FILE = "test_20260717_v6.awdb";

    private static final int TYPE_ARRAY = 1;
    private static final int TYPE_POINTER = 2;
    private static final int TYPE_STRING = 3;
    private static final int TYPE_TEXT = 4;
    private static final int TYPE_UINT = 5;

    private AwdbTestFixture() {
    }

    /* ---------------- 数据记录编码（decode_type=1） ---------------- */

    public static byte[] string(String s) {
        byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
        if (utf8.length > 255) {
            throw new IllegalArgumentException("string too long for 1-byte length: " + s);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(TYPE_STRING);
        out.write(utf8.length);
        out.write(utf8, 0, utf8.length);
        return out.toByteArray();
    }

    /** TEXT：type + len(长度字段的字节数) + len字节的大端长度 + UTF-8 内容 */
    public static byte[] text(String s, int lenFieldBytes) {
        byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(TYPE_TEXT);
        out.write(lenFieldBytes);
        writeUnsigned(out, utf8.length, lenFieldBytes);
        out.write(utf8, 0, utf8.length);
        return out.toByteArray();
    }

    public static byte[] uint(long value, int valueBytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(TYPE_UINT);
        out.write(valueBytes);
        writeUnsigned(out, value, valueBytes);
        return out.toByteArray();
    }

    /** POINTER：type + len(偏移字段字节数) + len字节大端偏移（相对 baseOffset） */
    public static byte[] pointer(long baseOffsetRelative, int offsetBytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(TYPE_POINTER);
        out.write(offsetBytes);
        writeUnsigned(out, baseOffsetRelative, offsetBytes);
        return out.toByteArray();
    }

    public static byte[] array(byte[]... elements) {
        if (elements.length > 255) {
            throw new IllegalArgumentException("too many elements");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(TYPE_ARRAY);
        out.write(elements.length);
        for (byte[] e : elements) {
            out.write(e, 0, e.length);
        }
        return out.toByteArray();
    }

    /* ---------------- 文件组装 ---------------- */

    /**
     * @param metaJson  meta JSON 文本（UTF-8）
     * @param nodeCount 节点数
     * @param byteLen   每个子节点值的字节数
     * @param children  树内容：nodeCount*2 个子节点值（大端无符号），叶子值须为 nodeCount+10+记录偏移
     * @param records   数据区记录，按顺序紧密排列
     */
    public static byte[] build(String metaJson, int nodeCount, int byteLen, long[] children, byte[]... records) {
        if (children.length != nodeCount * 2) {
            throw new IllegalArgumentException("children length must be nodeCount*2");
        }
        byte[] meta = metaJson.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeUnsigned(out, meta.length, 2);
        out.write(meta, 0, meta.length);
        for (long child : children) {
            writeUnsigned(out, child, byteLen);
        }
        for (byte[] record : records) {
            out.write(record, 0, record.length);
        }
        return out.toByteArray();
    }

    /** 叶子节点值：nodeIndex = nodeCount + 10 + 记录偏移（与 AwdbReader 的 pointer 公式互逆） */
    public static long leaf(int nodeCount, int recordOffset) {
        return nodeCount + 10L + recordOffset;
    }

    private static void writeUnsigned(ByteArrayOutputStream out, long value, int bytes) {
        for (int i = bytes - 1; i >= 0; i--) {
            out.write((int) (value >>> (i * 8)) & 0xFF);
        }
    }

    /* ---------------- 三个固化样本 ---------------- */

    public static byte[] structuredV4() {
        // 记录 A：结构化样本（202.96.128.86），含 multiAreas
        byte[] recA = array(
                string("中国"), string("广东"), string("广州"), string("电信"),
                array(
                        array(string("广东"), string("广州"), string("天河")),
                        array(string("广东"), string("深圳"), string("南山"))));
        // 记录 C：类型覆盖样本（224.0.0.9）：STRING / TEXT / UINT / POINTER(->记录A) / 空数组
        byte[] recC = array(
                string("类型样本"),
                text("这是一段用于覆盖 TEXT 类型的长文本，长度用两个字节的大端无符号数表示。", 2),
                uint(65535, 2),
                pointer(0, 3),
                array());
        // 记录 B：普通样本（1.2.3.4），multiAreas 为空数组
        byte[] recB = array(
                string("美国"), string("加利福尼亚"), string("洛杉矶"), string("Comcast"),
                array());

        int nodeCount = 4;
        int offA = 0;
        int offC = recA.length;
        int offB = recA.length + recC.length;
        long[] children = {
                1, 2,                            // node 0: 首位 0 -> node1, 1 -> node2
                leaf(nodeCount, offB), nodeCount, // node 1: 0 -> B, 1 -> 未命中
                nodeCount, 3,                    // node 2: 0 -> 未命中, 1 -> node3
                leaf(nodeCount, offA), leaf(nodeCount, offC) // node 3: 0 -> A, 1 -> C
        };

        String meta = "{"
                + "\"node_count\":" + nodeCount + ","
                + "\"ip_version\":\"4\","
                + "\"decode_type\":1,"
                + "\"byte_len\":4,"
                + "\"languages\":\"CN\","
                + "\"file_name\":\"" + STRUCTURED_FILE + "\","
                + "\"create_time\":\"2026-07-17\","
                + "\"company_id\":\"xenoamess-test\","
                + "\"columns\":[\"country\",\"province\",\"city\",\"isp\",\"multiAreas\",[\"prov\",\"city\",\"district\"]]"
                + "}";
        return build(meta, nodeCount, 4, children, recA, recC, recB);
    }

    public static byte[] directV4() {
        byte[] rec1 = directRecord("中国\t广东\t广州\t电信");
        byte[] rec2 = directRecord("美国\t加州\t山景城\tGoogle");

        int nodeCount = 2;
        long[] children = {
                1, leaf(nodeCount, 0),              // node 0: 0 -> node1, 1 -> rec1
                leaf(nodeCount, rec1.length), nodeCount // node 1: 0 -> rec2, 1 -> 未命中
        };

        String meta = "{"
                + "\"node_count\":" + nodeCount + ","
                + "\"ip_version\":\"4\","
                + "\"decode_type\":2,"
                + "\"byte_len\":4,"
                + "\"languages\":\"CN\","
                + "\"file_name\":\"" + DIRECT_FILE + "\","
                + "\"create_time\":\"2026-07-17\","
                + "\"company_id\":\"xenoamess-test\","
                + "\"columns\":[\"country\",\"province\",\"city\",\"isp\"]"
                + "}";
        return build(meta, nodeCount, 4, children, rec1, rec2);
    }

    public static byte[] directV6() {
        byte[] rec1 = directRecord("中国\t上海\t上海\tIPv6专线");

        int nodeCount = 2;
        long[] children = {
                1, nodeCount,              // node 0: 0 -> node1, 1 -> 未命中
                leaf(nodeCount, 0), nodeCount // node 1: 0 -> rec1, 1 -> 未命中
        };

        String meta = "{"
                + "\"node_count\":" + nodeCount + ","
                + "\"ip_version\":\"6\","
                + "\"decode_type\":2,"
                + "\"byte_len\":4,"
                + "\"languages\":\"CN\","
                + "\"file_name\":\"" + V6_FILE + "\","
                + "\"create_time\":\"2026-07-17\","
                + "\"company_id\":\"xenoamess-test\","
                + "\"columns\":[\"country\",\"province\",\"city\",\"isp\"]"
                + "}";
        return build(meta, nodeCount, 4, children, rec1);
    }

    /** decode_type=2 记录：4 字节大端长度 + tab 分隔 UTF-8 */
    private static byte[] directRecord(String tabJoinedValues) {
        byte[] utf8 = tabJoinedValues.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeUnsigned(out, utf8.length, 4);
        out.write(utf8, 0, utf8.length);
        return out.toByteArray();
    }

    public static void main(String[] args) throws IOException {
        Path dir = Paths.get(args.length > 0 ? args[0] : "src/test/resources");
        Files.createDirectories(dir);
        write(dir.resolve(STRUCTURED_FILE), structuredV4());
        write(dir.resolve(DIRECT_FILE), directV4());
        write(dir.resolve(V6_FILE), directV6());
    }

    private static void write(Path path, byte[] bytes) throws IOException {
        try {
            Files.write(path, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        System.out.println("written " + path + " (" + bytes.length + " bytes)");
    }
}
