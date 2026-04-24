package com.hmdp.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ配置类
 * 包含秒杀订单和用户日志的消息队列配置
 */
@Configuration
public class RabbitMQConfig {

    // ==================== 秒杀订单相关 ====================
    
    /**
     * 秒杀订单交换机（延迟交换机，使用普通交换机+死信队列实现）
     */
    public static final String SECKILL_ORDER_EXCHANGE = "seckill.order.exchange";
    
    /**
     * 秒杀订单队列
     */
    public static final String SECKILL_ORDER_QUEUE = "seckill.order.queue";
    
    /**
     * 秒杀订单路由键
     */
    public static final String SECKILL_ORDER_ROUTING_KEY = "seckill.order.create";
    
    /**
     * 秒杀订单死信交换机
     */
    public static final String SECKILL_ORDER_DLX_EXCHANGE = "seckill.order.dlx.exchange";
    
    /**
     * 秒杀订单死信队列
     */
    public static final String SECKILL_ORDER_DLX_QUEUE = "seckill.order.dlx.queue";

    // ==================== 用户日志相关 ====================
    
    /**
     * 用户日志交换机
     */
    public static final String USER_LOG_EXCHANGE = "user.log.exchange";
    
    /**
     * 用户日志队列
     */
    public static final String USER_LOG_QUEUE = "user.log.queue";
    
    /**
     * 用户日志路由键
     */
    public static final String USER_LOG_ROUTING_KEY = "user.log.collect";

    // ==================== 消息转换器 ====================
    
    /**
     * JSON消息转换器
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 配置RabbitTemplate
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter());
        
        // 开启Return模式（消息不可达时回调）
        rabbitTemplate.setMandatory(true);
        
        // 生产者确认回调
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                // 消息成功到达交换机
            } else {
                // 消息未到达交换机，可以记录日志或重试
            }
        });
        
        // 消息不可达回调（兼容Spring Boot 2.3.x版本）
        rabbitTemplate.setReturnCallback((message, replyCode, replyText, exchange, routingKey) -> {
            // 消息未路由到队列，记录日志
        });
        
        return rabbitTemplate;
    }

    // ==================== 秒杀订单队列配置 ====================

    /**
     * 秒杀订单死信交换机
     */
    @Bean
    public DirectExchange seckillOrderDlxExchange() {
        return new DirectExchange(SECKILL_ORDER_DLX_EXCHANGE, true, false);
    }

    /**
     * 秒杀订单死信队列
     */
    @Bean
    public Queue seckillOrderDlxQueue() {
        return QueueBuilder.durable(SECKILL_ORDER_DLX_QUEUE)
                .withArgument("x-message-ttl", 60000) // 死信消息保留60秒
                .build();
    }

    /**
     * 死信队列绑定
     */
    @Bean
    public Binding seckillOrderDlxBinding() {
        return BindingBuilder.bind(seckillOrderDlxQueue())
                .to(seckillOrderDlxExchange())
                .with(SECKILL_ORDER_ROUTING_KEY + ".dlx");
    }

    /**
     * 秒杀订单交换机
     */
    @Bean
    public DirectExchange seckillOrderExchange() {
        return new DirectExchange(SECKILL_ORDER_EXCHANGE, true, false);
    }

    /**
     * 秒杀订单队列（配置死信队列）
     */
    @Bean
    public Queue seckillOrderQueue() {
        return QueueBuilder.durable(SECKILL_ORDER_QUEUE)
                .withArgument("x-dead-letter-exchange", SECKILL_ORDER_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", SECKILL_ORDER_ROUTING_KEY + ".dlx")
                .withArgument("x-message-ttl", 300000) // 消息过期时间5分钟
                .build();
    }

    /**
     * 秒杀订单队列绑定
     */
    @Bean
    public Binding seckillOrderBinding() {
        return BindingBuilder.bind(seckillOrderQueue())
                .to(seckillOrderExchange())
                .with(SECKILL_ORDER_ROUTING_KEY);
    }

    // ==================== 用户日志队列配置 ====================

    /**
     * 用户日志交换机
     */
    @Bean
    public TopicExchange userLogExchange() {
        return new TopicExchange(USER_LOG_EXCHANGE, true, false);
    }

    /**
     * 用户日志队列
     */
    @Bean
    public Queue userLogQueue() {
        return QueueBuilder.durable(USER_LOG_QUEUE)
                .build();
    }

    /**
     * 用户日志队列绑定
     */
    @Bean
    public Binding userLogBinding() {
        return BindingBuilder.bind(userLogQueue())
                .to(userLogExchange())
                .with(USER_LOG_ROUTING_KEY);
    }
}
