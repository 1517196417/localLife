package com.hmdp.factory.impl;

import com.hmdp.factory.DistributedLock;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * 基于Redisson的分布式锁实现
 */
@Slf4j
public class RedissonDistributedLock implements DistributedLock {

    @Resource
    private RedissonClient redissonClient;

    @Override
    public boolean tryLock(String lockName, long timeout) {
        RLock lock = redissonClient.getLock(lockName);
        try {
            // 尝试获取锁，等待时间为0，锁定时间为timeout秒
            return lock.tryLock(0, timeout, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error("获取分布式锁被中断，lockName: {}", lockName, e);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void unlock(String lockName) {
        RLock lock = redissonClient.getLock(lockName);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("释放分布式锁成功，lockName: {}", lockName);
        }
    }
}
