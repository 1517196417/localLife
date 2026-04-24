package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户行为日志消息体
 * 用于RabbitMQ传递用户行为日志信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserLogMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 行为类型（浏览、点赞、关注、搜索等）
     */
    private String actionType;
    
    /**
     * 目标ID（博客ID、商铺ID等）
     */
    private Long targetId;
    
    /**
     * 行为描述
     */
    private String description;
    
    /**
     * 行为时间
     */
    private LocalDateTime actionTime;
    
    public UserLogMessage(Long userId, String actionType, Long targetId, String description) {
        this.userId = userId;
        this.actionType = actionType;
        this.targetId = targetId;
        this.description = description;
        this.actionTime = LocalDateTime.now();
    }
}
