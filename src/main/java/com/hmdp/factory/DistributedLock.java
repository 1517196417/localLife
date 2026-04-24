package com.hmdp.factory;

import java.util.concurrent.locks.Lock;

/**
 * 分布式锁接口
 * 统一不同实现方式的分布式锁
 */
public interface DistributedLock {

    /**
     * 尝试获取锁
     * 
     * @param lockName 锁名称
     * @param timeout 超时时间（秒）
     * @return 是否获取成功
     */
    boolean tryLock(String lockName, long timeout);

    /**
     * 释放锁
     * 
     * @param lockName 锁名称
     */
    void unlock(String lockName);
}
