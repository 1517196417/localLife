package com.hmdp.listener;

import com.hmdp.config.RabbitMQConfig;
import com.hmdp.dto.UserLogMessage;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 用户日志消费者
 * 监听用户行为日志消息，进行异步处理（如存储到数据库、统计分析等）
 */
@Slf4j
@Component
public class UserLogListener {

    /**
     * 监听用户日志队列
     * 
     * @param logMessage 日志消息
     * @param channel 通道
     * @param rabbitMessage 原始消息
     */
    @RabbitListener(queues = RabbitMQConfig.USER_LOG_QUEUE)
    public void handleUserLog(UserLogMessage logMessage, Channel channel, Message rabbitMessage) {
        long deliveryTag = rabbitMessage.getMessageProperties().getDeliveryTag();
        
        try {
            log.debug("收到用户日志消息，userId: {}, actionType: {}, targetId: {}",
                    logMessage.getUserId(), logMessage.getActionType(), logMessage.getTargetId());
            
            // 处理用户日志
            processUserLog(logMessage);
            
            // 手动ACK确认
            channel.basicAck(deliveryTag, false);
            
        } catch (Exception e) {
            log.error("用户日志处理异常", e);
            try {
                // 日志消息允许丢失，直接ACK
                channel.basicAck(deliveryTag, false);
            } catch (IOException ioException) {
                log.error("用户日志ACK失败", ioException);
            }
        }
    }

    /**
     * 处理用户日志
     * TODO: 可以扩展为存储到数据库、Elasticsearch或进行实时分析
     */
    private void processUserLog(UserLogMessage logMessage) {
        // 示例：根据行为类型进行不同处理
        switch (logMessage.getActionType()) {
            case "view":
                // 浏览行为 - 可以用于推荐系统
                log.debug("记录用户浏览行为，targetId: {}", logMessage.getTargetId());
                break;
            case "like":
                // 点赞行为 - 用户兴趣分析
                log.debug("记录用户点赞行为，targetId: {}", logMessage.getTargetId());
                break;
            case "follow":
                // 关注行为 - 社交关系分析
                log.debug("记录用户关注行为，targetId: {}", logMessage.getTargetId());
                break;
            case "search":
                // 搜索行为 - 搜索热词统计
                log.debug("记录用户搜索行为，keyword: {}", logMessage.getDescription());
                break;
            default:
                log.debug("记录用户其他行为，actionType: {}", logMessage.getActionType());
        }
        
        // TODO: 可以将日志写入数据库或Elasticsearch进行持久化
    }
}
