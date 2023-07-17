# awdb-java
www.ipplus360.com 官方支持的解析awdb格式的Java代码(Official support for parsing Java code in AWDB format )。

# maven 依赖引入示例方法
```xml
<dependency>
    <groupId>io.github.aiwen</groupId>
    <artifactId>awdb-java</artifactId>
    <version>1.0.2</version>
</dependency>
```

* 代码具体调用示例
  * 使用file方式

```java
import io.github.aiwen.ipplus360.AwdbReader;
import io.github.aiwen.ipplus360.impl.AwdbCacheImpl;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.File;
import java.io.IOException;

/**
 * 测试类
 */
public class AwdbApplicationTest {

    public static void main(String[] args) throws IOException {
        String ip = "211.112.21.0";
        // String ip = "2001:023a:0000:0000:0000:0000:0000:0000";
        String awdbFilePath = "test.awdb";
        try (AwdbReader reader = new AwdbReader(new File(awdbFilePath), new AwdbCacheImpl())) {
            JsonNode record = reader.findIpLocation(ip);
            System.out.println(record);
        }
    }
}
```

  * 使用stream方式

```java
import io.github.aiwen.ipplus360.AwdbReader;
import io.github.aiwen.ipplus360.impl.AwdbCacheImpl;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 测试类
 */
public class AwdbApplicationTest {

    public static void main(String[] args) throws IOException {
        String ip = "211.112.21.0";
        // String ip = "2001:023a:0000:0000:0000:0000:0000:0000";
        String awdbFilePath = "test.awdb";
        InputStream stream = new BufferedInputStream(Files.newInputStream(Paths.get(awdbFilePath)));
        try (AwdbReader reader = new AwdbReader(stream, new AwdbCacheImpl())) {
            JsonNode record = reader.findIpLocation(ip);
            System.out.println(record);
        }
    }
}
```
