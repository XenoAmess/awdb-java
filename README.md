# awdb-java
www.ipplus360.com 官方支持的解析awdb格式的Java代码(Official support for parsing Java code in AWDB format )。

# maven 依赖引入示例方法（只是一个示例，需要自己获取代码后打包）
```xml
<dependency>
    <groupId>io.github.aiwen</groupId>
    <artifactId>awdb-java</artifactId>
    <version>2.0.0</version>
</dependency>
```

* 代码具体调用示例

```java
package io.github.aiwen.example;

import io.github.aiwen.ipplus360.AwdbReader;
import io.github.aiwen.ipplus360.impl.AwdbCacheImpl;
import io.github.aiwen.ipplus360.enumerate.FileOpenMode;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.File;
import java.io.IOException;

/**
 * 测试类
 */
public class AwdbApplicationTest {

    public static void main(String[] args) throws IOException {
        String ip = "202.96.128.86";
        // String ip = "2001:023a:0000:0000:0000:0000:0000:0000";
        String awdbFilePath = "test.awdb";
        File file = new File(awdbFilePath);
        try (AwdbReader reader = AwdbReader.open(file, new AwdbCacheImpl(), FileOpenMode.MEMORY_MAPPED)) {
            JsonNode record = reader.findIpLocation(ip);
            System.out.println(record);
        }
    }
}
```

