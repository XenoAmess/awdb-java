# awdb-java

[![Coverage](https://img.shields.io/endpoint?url=https://xenoamess.github.io/awdb-java/coverage.json)](https://xenoamess.github.io/awdb-java/report/coverage.html)

> 本项目 fork 自 https://gitee.com/aiwen_home/awdb-java ，尊重原版版权 & License。
> This project is forked from https://gitee.com/aiwen_home/awdb-java . All rights of the original project are reserved to the original author, and the original copyright & license are respected.

www.ipplus360.com 官方支持的解析awdb格式的Java代码(Official support for parsing Java code in AWDB format )。

# maven 依赖引入示例方法（只是一个示例，需要自己获取代码后打包）
```xml
<dependency>
    <groupId>com.xenoamess</groupId>
    <artifactId>awdb-java</artifactId>
    <version>3.0.0</version>
</dependency>
```

* 代码具体调用示例

```java
package com.xenoamess.example;

import com.xenoamess.ipplus360.AwdbReader;
import com.xenoamess.ipplus360.impl.AwdbCacheImpl;
import com.xenoamess.ipplus360.enumerate.FileOpenMode;
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

