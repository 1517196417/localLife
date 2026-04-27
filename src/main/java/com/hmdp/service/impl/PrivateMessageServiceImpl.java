package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.PrivateMessage;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.mapper.PrivateMessageMapper;
import com.hmdp.service.IPrivateMessageService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import com.hmdp.websocket.WebSocketServer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class PrivateMessageServiceImpl extends ServiceImpl<PrivateMessageMapper, PrivateMessage> implements IPrivateMessageService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Resource
    private FollowMapper followMapper;

    private static final String CHAT_PENDING_KEY = "chat:pending:";

    @Override
    public Result sendMessage(Long toUserId, String content, String type) {
        Long fromUserId = UserHolder.getUser().getId();
        if (fromUserId.equals(toUserId)) {
            return Result.fail("不能给自己发消息");
        }

        // 1. 检查是否互相关注
        boolean isMutual = isMutualFollow(fromUserId, toUserId);

        // 2. 如果不是互关，检查限制：只能发一条，直到对方回复
        if (!isMutual) {
            String pendingKey = CHAT_PENDING_KEY + fromUserId + ":" + toUserId;
            String pending = stringRedisTemplate.opsForValue().get(pendingKey);
            if (pending != null && "1".equals(pending)) {
                return Result.fail("对方还未回复你的消息，请等待回复后再发送");
            }
        }

        // 3. 保存消息
        PrivateMessage msg = new PrivateMessage();
        msg.setFromUserId(fromUserId);
        msg.setToUserId(toUserId);
        msg.setContent(content);
        msg.setType(type);
        msg.setStatus(0);
        msg.setCreateTime(LocalDateTime.now());
        save(msg);

        // 4. 设置待回复标记（仅非互关场景）
        if (!isMutual) {
            stringRedisTemplate.opsForValue().set(
                    CHAT_PENDING_KEY + fromUserId + ":" + toUserId,
                    "1"
            );
        }

        // 5. 实时推送 WS 消息
        Map<String, Object> wsMsg = new HashMap<>();
        wsMsg.put("type", "new_message");
        wsMsg.put("fromUserId", fromUserId);
        wsMsg.put("toUserId", toUserId);
        wsMsg.put("content", content);
        wsMsg.put("msgType", type);
        wsMsg.put("createTime", msg.getCreateTime().toString());
        UserDTO sender = BeanUtil.copyProperties(userService.getById(fromUserId), UserDTO.class);
        wsMsg.put("fromNickName", sender != null ? sender.getNickName() : "");
        wsMsg.put("fromIcon", sender != null ? sender.getIcon() : "");

        cn.hutool.json.JSONObject json = cn.hutool.json.JSONUtil.parseObj(wsMsg);
        WebSocketServer.sendMessage(toUserId, json.toString());
        WebSocketServer.sendMessage(fromUserId, json.toString());

        return Result.ok(msg.getId());
    }

    @Override
    public Result getChatHistory(Long otherUserId, Integer page, Integer size) {
        Long userId = UserHolder.getUser().getId();
        if (page == null || page < 1) page = 1;
        if (size == null || size < 1) size = 20;

        QueryWrapper<PrivateMessage> wrapper = new QueryWrapper<>();
        wrapper.and(w -> w.eq("from_user_id", userId).eq("to_user_id", otherUserId)
                .or(w2 -> w2.eq("from_user_id", otherUserId).eq("to_user_id", userId)))
                .orderByDesc("create_time");

        Page<PrivateMessage> p = page(new Page<>(page, size), wrapper);
        List<PrivateMessage> records = p.getRecords();
        Collections.reverse(records);

        return Result.ok(records);
    }

    @Override
    public Result getRecentConversations() {
        Long userId = UserHolder.getUser().getId();

        List<PrivateMessage> myMessages = query()
                .eq("from_user_id", userId)
                .or(w -> w.eq("to_user_id", userId))
                .orderByDesc("create_time")
                .list();

        Map<Long, PrivateMessage> latestMap = new LinkedHashMap<>();
        Map<Long, Integer> unreadMap = new HashMap<>();

        for (PrivateMessage msg : myMessages) {
            Long otherId = msg.getFromUserId().equals(userId) ? msg.getToUserId() : msg.getFromUserId();
            if (!latestMap.containsKey(otherId)) {
                latestMap.put(otherId, msg);
            }
            if (msg.getToUserId().equals(userId) && msg.getStatus() == 0) {
                unreadMap.merge(otherId, 1, Integer::sum);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, PrivateMessage> entry : latestMap.entrySet()) {
            Long otherId = entry.getKey();
            PrivateMessage lastMsg = entry.getValue();
            UserDTO otherUser = BeanUtil.copyProperties(userService.getById(otherId), UserDTO.class);
            if (otherUser == null) continue;

            Map<String, Object> item = new HashMap<>();
            item.put("otherUserId", otherId);
            item.put("otherNickName", otherUser.getNickName());
            item.put("otherIcon", otherUser.getIcon());
            item.put("lastContent", lastMsg.getContent());
            item.put("lastType", lastMsg.getType());
            item.put("lastTime", lastMsg.getCreateTime());
            item.put("unreadCount", unreadMap.getOrDefault(otherId, 0));
            item.put("isMutual", isMutualFollow(userId, otherId));
            result.add(item);
        }

        return Result.ok(result);
    }

    @Override
    public Result canSendMessage(Long targetUserId) {
        Long userId = UserHolder.getUser().getId();
        boolean isMutual = isMutualFollow(userId, targetUserId);
        if (isMutual) {
            return Result.ok(true);
        }
        String pendingKey = CHAT_PENDING_KEY + userId + ":" + targetUserId;
        String pending = stringRedisTemplate.opsForValue().get(pendingKey);
        if (pending != null && "1".equals(pending)) {
            return Result.fail("对方还未回复你的消息，请等待回复后再发送");
        }
        return Result.ok(true);
    }

    @Override
    public Result getUnreadCount() {
        Long userId = UserHolder.getUser().getId();
        Integer count = count(new QueryWrapper<PrivateMessage>()
                .eq("to_user_id", userId)
                .eq("status", 0));
        return Result.ok(count);
    }

    @Override
    public Result markAsRead(Long otherUserId) {
        Long userId = UserHolder.getUser().getId();

        List<PrivateMessage> unread = query()
                .eq("from_user_id", otherUserId)
                .eq("to_user_id", userId)
                .eq("status", 0)
                .list();
        for (PrivateMessage msg : unread) {
            msg.setStatus(1);
            updateById(msg);
        }

        // 清除待回复标记：我方回复了，对方可以继续发
        String reversePendingKey = CHAT_PENDING_KEY + otherUserId + ":" + userId;
        stringRedisTemplate.delete(reversePendingKey);

        return Result.ok();
    }

    private boolean isMutualFollow(Long userId1, Long userId2) {
        Integer count1 = followMapper.selectCount(
                new QueryWrapper<com.hmdp.entity.Follow>()
                        .eq("user_id", userId1)
                        .eq("follow_user_id", userId2)
        );
        Integer count2 = followMapper.selectCount(
                new QueryWrapper<com.hmdp.entity.Follow>()
                        .eq("user_id", userId2)
                        .eq("follow_user_id", userId1)
        );
        return (count1 != null && count1 > 0) && (count2 != null && count2 > 0);
    }
}