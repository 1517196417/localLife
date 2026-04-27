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

import org.springframework.data.redis.connection.DataType;

import org.springframework.data.redis.core.StringRedisTemplate;
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
    
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    
    // 监听器初始化时打印日志
    public SeckillOrderListener() {
        log.info("========== SeckillOrderListener 已加载 ==========");
    }

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
            log.info("=================================================================");
            log.info("[监听器被调用] 收到秒杀订单消息！");
            log.info("messageId: {}, orderId: {}, userId: {}, voucherId: {}, retryCount: {}",
                    messageId, message.getOrderId(), message.getUserId(), message.getVoucherId(), message.getRetryCount());
            log.info("=================================================================");
            
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
     * 处理订单逻辑(手动保证数据一致性)
     */
    public void processOrder(SeckillOrderMessage message) {
        Long userId = message.getUserId();
        Long voucherId = message.getVoucherId();
        Long orderId = message.getOrderId();

        log.info("[开始处理订单] userId: {}, voucherId: {}, orderId: {}", userId, voucherId, orderId);

        // 获取分布式锁，防止重复下单
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean locked = false;
        
        try {
            locked = lock.tryLock();
            if (!locked) {
                log.warn("获取分布式锁失败，可能正在处理该用户的订单，userId: {}", userId);
                throw new RuntimeException("获取分布式锁失败");
            }
            log.info("[获取分布式锁成功] userId: {}", userId);

            // 检查是否已经下过单(一人一单)
            int count = voucherOrderService.query()
                    .eq("user_id", userId)
                    .eq("voucher_id", voucherId)
                    .count();
            log.info("[一人一单检查] userId: {}, voucherId: {}, count: {}", userId, voucherId, count);

            if (count > 0) {
                log.warn("用户已购买过该优惠券,恢复Redis库存并确认消息,userId: {}, voucherId: {}", userId, voucherId);
                // 恢复Redis中被扣减的库存(因为数据库不会扣减)
                String stockKey = "seckill:stock:" + voucherId;

                stringRedisTemplate.opsForValue().increment(stockKey, 1);
                // 移除订单记录中的用户(因为本次下单失败)
                String orderKey = "seckill:order:" + voucherId;
                stringRedisTemplate.opsForSet().remove(orderKey, userId.toString());

                // 重复下单不需要重试,直接抛出异常让上层ACK确认
                throw new RuntimeException("用户已购买过该优惠券,不允许重复下单");
            }

            // 扣减库存(数据库)
            log.info("[开始扣减数据库库存] voucherId: {}", voucherId);
            boolean stockSuccess = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0)
                    .update();
            log.info("[数据库库存扣减结果] voucherId: {}, success: {}", voucherId, stockSuccess);
            
            if (!stockSuccess) {
                log.error("库存不足,扣减失败,需要恢复Redis库存,voucherId: {}", voucherId);
                // 恢复Redis库存
                String stockKey = "seckill:stock:" + voucherId;

                stringRedisTemplate.opsForValue().increment(stockKey, 1);
                String orderKey = "seckill:order:" + voucherId;
                stringRedisTemplate.opsForSet().remove(orderKey, userId.toString());
                
                throw new RuntimeException("库存不足");
            }

            // 创建订单
            try {
                log.info("[开始创建订单] orderId: {}, userId: {}, voucherId: {}", orderId, userId, voucherId);
                VoucherOrder voucherOrder = new VoucherOrder();
                voucherOrder.setId(orderId);
                voucherOrder.setUserId(userId);
                voucherOrder.setVoucherId(voucherId);
                
                voucherOrderService.save(voucherOrder);
                
                log.info("[订单创建成功] orderId: {}, userId: {}, voucherId: {}", orderId, userId, voucherId);
            } catch (Exception e) {
                log.error("订单创建失败,需要回滚库存,orderId: {}, userId: {}, voucherId: {}", orderId, userId, voucherId, e);
                // 回滚数据库库存(手动回滚)
                seckillVoucherService.update()
                        .setSql("stock = stock + 1")
                        .eq("voucher_id", voucherId)
                        .update();
                // 回滚Redis库存
                String stockKey = "seckill:stock:" + voucherId;

                stringRedisTemplate.opsForValue().increment(stockKey, 1);
                String orderKey = "seckill:order:" + voucherId;
                stringRedisTemplate.opsForSet().remove(orderKey, userId.toString());
                
                throw new RuntimeException("订单创建失败", e);
            }
            
        } finally {
            if (locked) {
                lock.unlock();
                log.info("[释放分布式锁] userId: {}", userId);
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
