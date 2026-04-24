package com.hmdp.utils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 多级缓存工具类
 * L1: Caffeine本地缓存（热点数据，极快）
 * L2: Redis分布式缓存（全量数据，快）
 * L3: MySQL数据库（持久化数据，慢）
 */
@Slf4j
@Component
public class MultiLevelCache {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * L1缓存：Caffeine本地缓存
     * 配置：最大10000个条目，写入后5分钟过期，LRU淘汰策略
     */
    private final Cache<String, String> l1Cache = Caffeine.newBuilder()
            .maximumSize(10000)  // 最大缓存条目数
            .expireAfterWrite(5, TimeUnit.MINUTES)  // 写入后5分钟过期
            .recordStats()  // 开启统计
            .build();

    /**
     * 多级缓存查询（带加载函数）
     * 
     * @param key 缓存键
     * @param l2Key Redis键
     * @param l2TTL Redis过期时间（分钟）
     * @param dbLoader 数据库加载函数
     * @return 缓存值
     */
    public String get(String key, String l2Key, Long l2TTL, Function<String, String> dbLoader) {
        // 1. 查询L1缓存（Caffeine）
        String l1Value = l1Cache.getIfPresent(key);
        if (l1Value != null) {
            log.debug("L1缓存命中，key: {}", key);
            return l1Value;
        }

        // 2. 查询L2缓存（Redis）
        String l2Value = stringRedisTemplate.opsForValue().get(l2Key);
        if (l2Value != null) {
            log.debug("L2缓存命中，key: {}", l2Key);
            // 写入L1缓存
            l1Cache.put(key, l2Value);
            return l2Value;
        }

        // 3. 查询数据库
        String dbValue = dbLoader.apply(key);
        if (dbValue != null) {
            log.debug("数据库查询成功，key: {}", key);
            // 写入L2缓存
            if (l2TTL != null && l2TTL > 0) {
                stringRedisTemplate.opsForValue().set(l2Key, dbValue, l2TTL, TimeUnit.MINUTES);
            }
            // 写入L1缓存
            l1Cache.put(key, dbValue);
        } else {
            // 数据库也没有，缓存空值防止穿透（L2缓存2分钟）
            stringRedisTemplate.opsForValue().set(l2Key, "", 2, TimeUnit.MINUTES);
        }

        return dbValue;
    }

    /**
     * 更新多级缓存
     * 
     * @param key L1缓存键
     * @param l2Key L2缓存键
     * @param value 新值
     * @param l2TTL Redis过期时间（分钟）
     */
    public void put(String key, String l2Key, String value, Long l2TTL) {
        // 更新L1缓存
        l1Cache.put(key, value);
        // 更新L2缓存
        if (l2TTL != null && l2TTL > 0) {
            stringRedisTemplate.opsForValue().set(l2Key, value, l2TTL, TimeUnit.MINUTES);
        }
    }

    /**
     * 删除多级缓存
     * 
     * @param key L1缓存键
     * @param l2Key L2缓存键
     */
    public void evict(String key, String l2Key) {
        // 删除L1缓存
        l1Cache.invalidate(key);
        // 删除L2缓存
        stringRedisTemplate.delete(l2Key);
        log.debug("多级缓存已删除，key: {}, l2Key: {}", key, l2Key);
    }

    /**
     * 获取L1缓存统计信息
     */
    public String getStats() {
        return l1Cache.stats().toString();
    }

    /**
     * 清空L1缓存
     */
    public void invalidateAll() {
        l1Cache.invalidateAll();
        log.info("L1缓存已清空");
    }
}
