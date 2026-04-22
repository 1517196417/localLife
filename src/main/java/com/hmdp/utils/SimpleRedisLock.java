package com.hmdp.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOCK_SIMPLE_KEY;

public class SimpleRedisLock implements ILock{
    private String name;
    private StringRedisTemplate stringRedisTemplate;
    private final String ID_PREFIX= UUID.randomUUID().toString();

    //定义成静态变量，只需要加载一次文件，减少io操作时间
    //不定义的话，每次释放锁都需要加载lua脚本
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unLock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }


    public SimpleRedisLock( String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec, StringRedisTemplate stringRedisTemplate) {
        long threadId = Thread.currentThread().getId();
        Boolean isLock = stringRedisTemplate.opsForValue()
                .setIfAbsent(LOCK_SIMPLE_KEY + name, ID_PREFIX +threadId + "", timeoutSec, TimeUnit.SECONDS);
        System.out.println("执行上分布式锁的返回值=" + isLock);
        return Boolean.TRUE.equals(isLock);
    }

    @Override
    public void unlock() {

        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(LOCK_SIMPLE_KEY + name),
                ID_PREFIX +Thread.currentThread().getId());
    }
}
