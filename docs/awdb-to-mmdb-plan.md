# Gen-B awdb → mmdb 完全自研转换方案

> 状态：已锁定（2026-07-17 用户拍板）。本文档是施工依据。

## 背景与约束

- AWDB 存在两代互不兼容格式（已验证）：
  - **Gen-A（mmdb 衍生）**：文件尾 metadata，marker `\xAB\xCD\xEFipplus360.com`。
    实现：dilfish/awdb-golang、sjzar/ips、qiniu/uip、Ro0tk1t/awdb-python、open-cmi/awdb-c。
  - **Gen-B（Java 官方 SDK 代）**：文件头 2 字节 meta 长度 + meta JSON，decode_type/byte_len/columns，
    叶子公式 `pointer = baseOffset + nodeIndex - nodeCount - 10`。
    实现：aiwen_home/awdb-java（上游）、本仓库。
- **官方现行真实文件 = Gen-B**（用户用真实文件跑 `ips pack` 报 `invalid aw DB file` 证实）。
  整个 Go/Python/C 第三方生态对现行官方文件已失效。
- 约束：
  1. 真实 .awdb 在内网，不可取出 —— 验证只能用合成数据；
  2. 禁止使用 ips 等第三方转换工具 —— 转换能力完全自研；
  3. 以本仓库当前对 Gen-B 的实现理解为 ground truth。

## 决策参数（用户已拍板）

| 项 | 决策 |
|---|---|
| 校验 oracle | test-scope 引入官方 `com.maxmind.db:maxmind-db` reader（Apache-2.0），仅作 writer 产物校验，转换能力 100% 自研 |
| record_size | 24/28/32 三档全做（28 位中间字节共享配金样） |
| mmdb writer 归属 | `com.xenoamess.mmdb` 公开 API，随 awdb-java jar 发布（Java 生态缺成熟 writer，本身即贡献）。已核实：MaxMind 官方 writer 仅有 Go/Perl，Maven Central 与 GitHub 均无可用 Java writer，故自研 |
| AwdbProbe | 本轮同做（meta 转储 / 点查 / 遍历统计 / 字符串去重率），fat jar 供内网跑真实文件 |

## 目标布局（mmdb 规范 v2.0 要点，施工依据）

- 三段式：search tree → 16 字节 0 分隔 → data section → 尾标 `\xAB\xCD\xEFMaxMind.com` + metadata MAP。
- tree：nodeCount 节点 × 2 record，record_size ∈ {24,28,32} bit（节点 6/7/8 字节），大端。
  - 子节点值 `< nodeCount` → 下一节点；`== nodeCount` → 未命中；`>= nodeCount+16` → 数据区，
    `offset = value - nodeCount - 16`（相对数据区起点）。`nodeCount+1..+15` 为非法保留。
- 控制字节：高 3 位类型（0 = 扩展类型，第二字节 = type-7）；低 5 位尺寸，
  29 → +1 字节，30 → +2 字节 +285，31 → +3 字节 +65821。
- 类型：1 pointer / 2 utf8 / 3 double / 4 bytes / 5 uint16 / 6 uint32 / 7 map / 8 int32 /
  9 uint64 / 10 uint128 / 11 array / 14 boolean / 15 float。
- pointer：`001SSVVV`，SS=0..3 → 11 位 / 19 位+2048 / 27 位+526336 / 32 位直读；禁止 pointer→pointer。
- metadata MAP 必需键：node_count, record_size, ip_version, database_type,
  binary_format_major_version(2), binary_format_minor_version(0), build_epoch；可选 languages, description。
- **IPv4 布局（最高风险点）**：awdb Gen-B 的 4_6 把 v4 挂在 `::ffff:0/96` 下；
  mmdb 把 v4 内容放在 **`::/96`**（96 个零）下，并在 `::ffff:0:0/96` 写**别名**指向 v4 子树根。
  4_6 转换必须重 rooting，不能搬树。ip_version=4 的 mmdb 允许 32 位树（reader 走 32 位路径）。

## 架构

```
com.xenoamess.mmdb               通用 mmdb writer（公开 API）
  MmdbWriter                     builder：insert(addr, prefixLen, record) → write(File)
  Trie                           插入 + 同值子节点合并压缩 + 节点编号
  DataEncoder                    控制字节 / 扩展尺寸 / 15 类型 / pointer 四档
  ValueDeduper                   值→偏移缓存，重复值写 pointer
  TreeSerializer                 24/28/32 位 record 打包
  MetadataEncoder                尾标 + metadata MAP
com.xenoamess.ipplus360.walk     awdb trie 全遍历（DFS，同值子树合并，叶子解码复用现有路径）
com.xenoamess.ipplus360.convert  AwdbToMmdbConverter + Main（CLI）+ AwdbProbe（CLI）
```

## 布局映射规则

| awdb ip_version | mmdb 输出 |
|---|---|
| `"4"` | ip_version=4，32 位树直搬 |
| `"6"` | ip_version=6，128 位树直搬 |
| `"4_6"` | ip_version=6；awdb 的 `::ffff:0/96` 子树重 rooting 到 `::/96`，`::ffff:0:0/96` 写别名指针（2002::/16 6to4 别名可选） |

JsonNode → mmdb 值：TextNode→utf8，LongNode→uint64，IntNode→int32，DoubleNode→double，
FloatNode→float，BooleanNode→boolean，ArrayNode→array，ObjectNode→map。

## 验证策略（全合成）

- spec 内嵌示例金样（80 字节字符串控制字节、pointer 四档、28 位打包）。
- oracle 对拍：我们的 writer 产物 → 官方 reader 读取 ↔ 预期值。
- 5 个现有 fixture 端到端：转换后 oracle 读取 ↔ AwdbReader 结果逐 IP 对拍
  （命中/未命中/v4/v6/multiAreas/数值类型），及全量 (prefix, record) 集合相等。

## 执行序列

1. Commit 1 — mmdb writer 核心 + 金样 + oracle 对拍
2. Commit 2 — awdb 遍历 API + 测试
3. Commit 3 — 转换器 + 5 fixture 端到端对拍（含 4_6 别名）
4. Commit 4 — CLI（converter + probe）+ fatjar profile + README/AGENTS.md

## 边界声明

- 全部验证基于合成 fixture；真实文件验证待内网跑 probe/converter 回执。
- ips 文本管线压平类型的问题不存在（全自研，类型按上表映射保留）。
