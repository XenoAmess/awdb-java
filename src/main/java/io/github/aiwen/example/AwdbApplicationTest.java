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