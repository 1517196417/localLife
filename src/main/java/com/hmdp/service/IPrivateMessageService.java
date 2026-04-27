package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.PrivateMessage;
import com.hmdp.dto.UserDTO;

import java.util.List;

public interface IPrivateMessageService extends IService<PrivateMessage> {
    /**
     * 发送消息（文字或图片）
     */
    Result sendMessage(Long toUserId, String content, String type);
    
    /**
     * 获取与某人的聊天记录
     */
    Result getChatHistory(Long otherUserId, Integer page, Integer size);
    
    /**
     * 获取最近对话列表
     */
    Result getRecentConversations();
    
    /**
     * 检查当前用户是否可以给 targetUserId 发消息
     */
    Result canSendMessage(Long targetUserId);
    
    /**
     * 获取未读消息数量
     */
    Result getUnreadCount();
    
    /**
     * 标记消息已读
     */
    Result markAsRead(Long otherUserId);
}