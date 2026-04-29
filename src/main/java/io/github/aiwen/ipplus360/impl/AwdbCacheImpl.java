package io.github.aiwen.ipplus360.impl;

import io.github.aiwen.ipplus360.AwdbNodeCache;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
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
        JsonNode value = cache.get(key);
        if (value == null) {
            value = loader.load(key);
            cache.put(key, value);
        }
        return value;
    }
}
