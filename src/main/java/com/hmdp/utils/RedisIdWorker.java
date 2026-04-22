package com.hmdp.utils;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private final long Begin_Time_Seconds = 1767225600L;
    private static final long COUNT_BITS = 32;

    public long nextId(String prekey){
        //1：生成时间戳
        LocalDateTime localDateTime = LocalDateTime.now();
        long nowSecond = localDateTime.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSecond - Begin_Time_Seconds;
        //2：生成序列号
        String date = localDateTime.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + prekey + ":" + date);

        //3：拼接并返回
        return timeStamp << COUNT_BITS | count;
    }

//    public static void main(String[] args) {
//        LocalDateTime startTime = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
//        long second = startTime.toEpochSecond(ZoneOffset.UTC);
//        System.out.println(second);
//    }
}
