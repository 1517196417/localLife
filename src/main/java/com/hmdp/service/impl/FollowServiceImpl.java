package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Path;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private  StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;




    @Override
    public Result Follow(Long id, Boolean isFollow) {
        //获取userId
        Long userId = UserHolder.getUser().getId();

        String followKey = "follow:" + userId;
        if(isFollow) {
            //没关注，就新增双方id到中间表，实现关注功能
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(id);
            save(follow);

            //为实现共同关注，每次关注时将用户id，被关注者id存入redis的set集合
            stringRedisTemplate.opsForSet().add(followKey, id.toString());
        } else {
            //已关注，就删除，实现取关
            remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", id));
            //为实现共同关注，每次取消关注时将用户id，被关注者id删除redis的set集合
            stringRedisTemplate.opsForSet().remove(followKey, id.toString());
        }
        return Result.ok();
    }

    //判断是否关注
    @Override
    public Result isFollow(Long id) {
        Long userId = UserHolder.getUser().getId();
        query().eq("user_id", userId)
                .eq("follow_user_id", id)
                .count();
        return Result.ok(count() > 0);
    }

    //查询共同关注
    @Override
    public Result followCommon(Long id) {
        Long userId = UserHolder.getUser().getId();

        Set<String> intersect = stringRedisTemplate.opsForSet().intersect("follow:" + userId, "follow:" + id);
        if(intersect == null ||intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> userIds = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> userDTOs = userService.listByIds(userIds).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOs);
    }
}
