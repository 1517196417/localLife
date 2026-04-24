package com.hmdp.factory;

import com.hmdp.factory.impl.RedissonDistributedLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 分布式锁工厂
 * 根据类型创建不同的分布式锁实现
 */
@Slf4j
@Component
public class DistributedLockFactory {

    /**
     * 锁类型枚举
     */
    public enum LockType {
        REDIS,        // Redis简单锁
        REDISSON      // Redisson可重入锁
    }

    /**
     * 创建分布式锁
     * 
     * @param type 锁类型
     * @return 分布式锁实例
     */
    public DistributedLock createLock(LockType type) {
        switch (type) {
            case REDISSON:
                log.debug("创建Redisson分布式锁");
                return new RedissonDistributedLock();
            default:
                log.warn("未知的锁类型：{}，使用默认Redisson锁", type);
                return new RedissonDistributedLock();
        }
    }

    /**
     * 创建默认分布式锁（Redisson）
     */
    public DistributedLock createDefaultLock() {
        return createLock(LockType.REDISSON);
    }
}
