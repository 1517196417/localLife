package com.hmdp.utils;

import com.hmdp.entity.VoucherOrder;
import org.springframework.data.redis.core.StringRedisTemplate;

public interface ILock {
    boolean tryLock(long timeoutSec, StringRedisTemplate stringRedisTemplate);

    void unlock();

}
