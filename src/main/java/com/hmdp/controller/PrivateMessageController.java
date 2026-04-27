package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IPrivateMessageService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/chat")
public class PrivateMessageController {

    @Resource
    private IPrivateMessageService privateMessageService;

    /**
     * 发送消息（文字）
     */
    @PostMapping("/send")
    public Result sendMessage(@RequestParam("toUserId") Long toUserId,
                              @RequestParam("content") String content,
                              @RequestParam(value = "type", defaultValue = "text") String type) {
        return privateMessageService.sendMessage(toUserId, content, type);
    }

    /**
     * 获取与某人的聊天记录
     */
    @GetMapping("/history/{otherUserId}")
    public Result getChatHistory(@PathVariable Long otherUserId,
                                 @RequestParam(value = "page", defaultValue = "1") Integer page,
                                 @RequestParam(value = "size", defaultValue = "50") Integer size) {
        return privateMessageService.getChatHistory(otherUserId, page, size);
    }

    /**
     * 获取最近对话列表
     */
    @GetMapping("/conversations")
    public Result getRecentConversations() {
        return privateMessageService.getRecentConversations();
    }

    /**
     * 检查能否给对方发消息
     */
    @GetMapping("/can-send/{targetUserId}")
    public Result canSendMessage(@PathVariable Long targetUserId) {
        return privateMessageService.canSendMessage(targetUserId);
    }

    /**
     * 获取未读消息数量
     */
    @GetMapping("/unread-count")
    public Result getUnreadCount() {
        return privateMessageService.getUnreadCount();
    }

    /**
     * 标记与某人的消息已读
     */
    @PutMapping("/mark-read/{otherUserId}")
    public Result markAsRead(@PathVariable Long otherUserId) {
        return privateMessageService.markAsRead(otherUserId);
    }
}