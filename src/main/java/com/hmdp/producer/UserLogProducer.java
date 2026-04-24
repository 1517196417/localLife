package com.hmdp.producer;

import com.hmdp.config.RabbitMQConfig;
import com.hmdp.dto.UserLogMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 用户日志消息生产者
 * 负责发送用户行为日志到RabbitMQ
 */
@Slf4j
@Component
public class UserLogProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送用户行为日志
     * 
     * @param logMessage 用户日志消息
     */
    public void sendUserLog(UserLogMessage logMessage) {
        try {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.USER_LOG_EXCHANGE,
                RabbitMQConfig.USER_LOG_ROUTING_KEY,
                logMessage
            );
            
            log.debug("用户日志发送成功，userId: {}, actionType: {}, targetId: {}",
                    logMessage.getUserId(), logMessage.getActionType(), logMessage.getTargetId());
        } catch (Exception e) {
            log.error("用户日志发送失败", e);
        }
    }
}
