package com.hmdp.listener;

import com.hmdp.config.RabbitMQConfig;
import com.hmdp.dto.SeckillOrderMessage;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 秒杀订单消费者
 * 监听RabbitMQ中的秒杀订单消息，异步处理订单创建
 */
@Slf4j
@Component
public class SeckillOrderListener {

    @Autowired
    private IVoucherOrderService voucherOrderService;

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 监听秒杀订单队列
     * 
     * @param message 消息体
     * @param channel 通道
     * @param rabbitMessage 原始消息
     */
    @RabbitListener(queues = RabbitMQConfig.SECKILL_ORDER_QUEUE)
    public void handleSeckillOrder(SeckillOrderMessage message, Channel channel, Message rabbitMessage) {
        long deliveryTag = rabbitMessage.getMessageProperties().getDeliveryTag();
        String messageId = rabbitMessage.getMessageProperties().getMessageId();
        
        try {
            log.info("收到秒杀订单消息，messageId: {}, orderId: {}, userId: {}, voucherId: {}, retryCount: {}",
                    messageId, message.getOrderId(), message.getUserId(), message.getVoucherId(), message.getRetryCount());
            
            // 处理订单
            processOrder(message);
            
            // 手动ACK确认
            channel.basicAck(deliveryTag, false);
            log.info("秒杀订单处理成功，messageId: {}", messageId);
            
        } catch (Exception e) {
            log.error("秒杀订单处理异常，messageId: {}", messageId, e);
            
            try {
                // 判断重试次数
                if (message.getRetryCount() >= 3) {
                    // 超过最大重试次数，拒绝消息并进入死信队列
                    log.warn("消息重试次数已达上限，进入死信队列，messageId: {}", messageId);
                    channel.basicNack(deliveryTag, false, false);
                } else {
                    // 重试消息
                    message.setRetryCount(message.getRetryCount() + 1);
                    log.info("消息重试，第 {} 次，messageId: {}", message.getRetryCount(), messageId);
                    // 拒绝消息并重新入队
                    channel.basicNack(deliveryTag, false, true);
                }
            } catch (IOException ioException) {
                log.error("消息ACK失败，messageId: {}", messageId, ioException);
            }
        }
    }

    /**
     * 处理订单逻辑
     */
    private void processOrder(SeckillOrderMessage message) {
        Long userId = message.getUserId();
        Long voucherId = message.getVoucherId();
        Long orderId = message.getOrderId();

        // 获取分布式锁，防止重复下单
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean locked = false;
        
        try {
            locked = lock.tryLock();
            if (!locked) {
                log.warn("获取分布式锁失败，可能正在处理该用户的订单，userId: {}", userId);
                throw new RuntimeException("获取分布式锁失败");
            }

            // 检查是否已经下过单（一人一单）
            int count = voucherOrderService.query()
                    .eq("user_id", userId)
                    .eq("voucher_id", voucherId)
                    .count();
            
            if (count > 0) {
                log.warn("用户已购买过该优惠券，跳过创建订单，userId: {}, voucherId: {}", userId, voucherId);
                return;
            }

            // 扣减库存
            boolean stockSuccess = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0)
                    .update();
            
            if (!stockSuccess) {
                log.error("库存不足，扣减失败，voucherId: {}", voucherId);
                throw new RuntimeException("库存不足");
            }

            // 创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            voucherOrder.setId(orderId);
            voucherOrder.setUserId(userId);
            voucherOrder.setVoucherId(voucherId);
            
            voucherOrderService.save(voucherOrder);
            
            log.info("秒杀订单创建成功，orderId: {}, userId: {}, voucherId: {}", orderId, userId, voucherId);
            
        } finally {
            if (locked) {
                lock.unlock();
            }
        }
    }

    /**
     * 监听死信队列
     * 处理无法消费的消息
     */
    @RabbitListener(queues = RabbitMQConfig.SECKILL_ORDER_DLX_QUEUE)
    public void handleDeadLetter(SeckillOrderMessage message, Channel channel, Message rabbitMessage) {
        long deliveryTag = rabbitMessage.getMessageProperties().getDeliveryTag();
        
        try {
            log.error("收到死信消息，记录日志，可能需要进行补偿处理。orderId: {}, userId: {}, voucherId: {}",
                    message.getOrderId(), message.getUserId(), message.getVoucherId());
            
            // TODO: 可以将死信消息记录到数据库，后续人工处理或定时任务补偿
            
            // ACK确认
            channel.basicAck(deliveryTag, false);
            
        } catch (Exception e) {
            log.error("死信消息处理异常", e);
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ioException) {
                log.error("死信消息ACK失败", ioException);
            }
        }
    }
}
