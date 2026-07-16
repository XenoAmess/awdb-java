package com.xenoamess.ipplus360.impl;

import com.xenoamess.ipplus360.AwdbNodeCache;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 使用 {@link ConcurrentHashMap} 的简单缓存.
 */
public class AwdbCacheImpl implements AwdbNodeCache {
    private static final int DEFAULT_CAPACITY = 4096;
    private final ConcurrentHashMap<Long, JsonNode> cache;

    public AwdbCacheImpl() {
        this(DEFAULT_CAPACITY);
    }

    public AwdbCacheImpl(int capacity) {
        this.cache = new ConcurrentHashMap<>(capacity);
    }

    @Override
    public JsonNode get(Loader loader, long key) throws IOException {
        try {
            // loader 返回 null 时不缓存（ConcurrentHashMap 不允许 null 值）
            return cache.computeIfAbsent(key, k -> {
                try {
                    return loader.load(k);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
}
