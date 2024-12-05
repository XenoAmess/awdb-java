package io.github.aiwen.example;

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