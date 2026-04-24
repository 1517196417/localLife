package com.hmdp.producer;

import com.hmdp.config.RabbitMQConfig;
import com.hmdp.dto.SeckillOrderMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 秒杀订单消息生产者
 * 负责发送秒杀订单消息到RabbitMQ
 */
@Slf4j
@Component
public class SeckillOrderProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送秒杀订单消息
     * 
     * @param message 秒杀订单消息
     * @return CompletableFuture 异步结果
     */
    public CompletableFuture<Boolean> sendSeckillOrder(SeckillOrderMessage message) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        // 生成消息ID
        String messageId = UUID.randomUUID().toString();
        CorrelationData correlationData = new CorrelationData(messageId);
        
        try {
            // 发送消息
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.SECKILL_ORDER_EXCHANGE,
                RabbitMQConfig.SECKILL_ORDER_ROUTING_KEY,
                message,
                correlationData
            );
            
            log.info("秒杀订单消息发送成功，messageId: {}, orderId: {}, userId: {}, voucherId: {}",
                    messageId, message.getOrderId(), message.getUserId(), message.getVoucherId());
            
            future.complete(true);
        } catch (Exception e) {
            log.error("秒杀订单消息发送失败，messageId: {}", messageId, e);
            future.complete(false);
        }
        
        return future;
    }

    /**
     * 发送秒杀订单消息（同步阻塞方式）
     * 
     * @param message 秒杀订单消息
     * @return 是否发送成功
     */
    public boolean sendSeckillOrderSync(SeckillOrderMessage message) {
        try {
            String messageId = UUID.randomUUID().toString();
            CorrelationData correlationData = new CorrelationData(messageId);
            
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.SECKILL_ORDER_EXCHANGE,
                RabbitMQConfig.SECKILL_ORDER_ROUTING_KEY,
                message,
                correlationData
            );
            
            log.info("秒杀订单消息同步发送成功，orderId: {}", message.getOrderId());
            return true;
        } catch (Exception e) {
            log.error("秒杀订单消息同步发送失败", e);
            return false;
        }
    }
}
