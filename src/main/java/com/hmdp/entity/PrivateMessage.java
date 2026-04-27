package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 私信消息实体
 */
@Data
@TableName("tb_private_message")
public class PrivateMessage {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /** 发送者ID */
    private Long fromUserId;
    
    /** 接收者ID */
    private Long toUserId;
    
    /** 消息内容（文字消息或图片URL） */
    private String content;
    
    /** 消息类型：text=文字, image=图片 */
    private String type;
    
    /** 消息状态：0=未读, 1=已读 */
    private Integer status;
    
    /** 创建时间 */
    private LocalDateTime createTime;
}