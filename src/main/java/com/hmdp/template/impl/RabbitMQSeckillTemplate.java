package com.hmdp.template.impl;

import com.hmdp.dto.SeckillOrderMessage;
import com.hmdp.producer.SeckillOrderProducer;
import com.hmdp.template.SeckillTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;

/**
 * 基于RabbitMQ的秒杀流程实现
 * 异步下单，提高吞吐量
 */
@Slf4j
@Component
public class RabbitMQSeckillTemplate extends SeckillTemplate {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SeckillOrderProducer seckillOrderProducer;

    /**
     * Lua脚本：检查库存和一人一单
     */
    private static final String SECKILL_SCRIPT = 
            "local voucherId = ARGS[1]\n" +
            "local userId = ARGS[2]\n" +
            "local stockKey = 'seckill:stock:' .. voucherId\n" +
            "local orderKey = 'seckill:order:' .. voucherId\n" +
            "local stock = tonumber(redis.call('GET', stockKey))\n" +
            "if stock <= 0 then\n" +
            "    return 1\n" +
            "end\n" +
            "if redis.call('SISMEMBER', orderKey, userId) == 1 then\n" +
            "    return 2\n" +
            "end\n" +
            "redis.call('INCRBY', stockKey, -1)\n" +
            "redis.call('SADD', orderKey, userId)\n" +
            "return 0\n";

    @Override
    protected boolean checkQualification(Long voucherId, Long userId) {
        // 执行Lua脚本检查资格
        Long result = stringRedisTemplate.execute(
                new org.springframework.data.redis.core.script.DefaultRedisScript<>(SECKILL_SCRIPT, Long.class),
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );

        int retVal = result.intValue();
        if (retVal != 0) {
            log.warn("秒杀资格校验失败，voucherId: {}, userId: {}, result: {}", voucherId, userId, retVal);
            return false;
        }

        return true;
    }

    @Override
    protected void sendOrderMessage(Long orderId, Long userId, Long voucherId) {
        // 构建秒杀订单消息
        SeckillOrderMessage message = new SeckillOrderMessage(orderId, userId, voucherId);

        // 发送消息到RabbitMQ（同步发送，确保消息到达）
        boolean sendSuccess = seckillOrderProducer.sendSeckillOrderSync(message);

        if (!sendSuccess) {
            log.error("秒杀订单消息发送失败，orderId: {}", orderId);
            throw new RuntimeException("消息发送失败");
        }

        log.info("秒杀订单消息发送成功，orderId: {}", orderId);
    }
}
