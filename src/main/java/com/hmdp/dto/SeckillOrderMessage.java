package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 秒杀订单消息体
 * 用于RabbitMQ传递秒杀订单信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillOrderMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 订单ID
     */
    private Long orderId;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 优惠券ID
     */
    private Long voucherId;
    
    /**
     * 重试次数
     */
    private Integer retryCount = 0;
    
    public SeckillOrderMessage(Long orderId, Long userId, Long voucherId) {
        this.orderId = orderId;
        this.userId = userId;
        this.voucherId = voucherId;
        this.retryCount = 0;
    }
}
