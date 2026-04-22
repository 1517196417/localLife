package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    private BlogMapper blogMapper;

    @Autowired
    private IUserService userService;

    @Resource
    private StringRedisTemplate  stringRedisTemplate;
    @Autowired
    private ShopMapper shopMapper;
    @Autowired
    private IFollowService followService;

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();

        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSave = save(blog);
        if (!isSave) {
            return Result.fail("保存blog失败");
        }
        //获取当前用户的所有粉丝id
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        //推送给粉丝blogId
        for(Follow follow : follows) {
            String followKey = "feed:" + follow.getUserId();
            stringRedisTemplate.opsForZSet().add(followKey, blog.getId().toString(), System.currentTimeMillis());
        }

        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if(blog == null){
            return  Result.fail("博客不存在");
        }
        queryBlogUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        Long userId = blog.getUserId();

        Long blogId = blog.getId();
        String key = "blog:like:" + blogId;

        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    @Override
    public void likeBlog(Long id) {
        Blog blog = blogMapper.selectById(id);
        Long userId = UserHolder.getUser().getId();
        String key = "blog:like:" + id;

        //1：判断该blog是否被当前用户点赞了
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        if(score == null){
            //2.1：未点赞，数据库点赞数+1，并将userId缓存到redis
            update().setSql("liked = liked + 1")
                    .eq("id", id)
                    .update();

            stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
        }
        //2.2：已点赞，取消点赞（数据库-1），将userId移除redis
        else{
            update().setSql("liked = liked - 1")
                    .eq("id", id)
                    .update();

            stringRedisTemplate.opsForZSet().remove(key, userId.toString());
        }
    }

    private void queryBlogUser(Blog blog){
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public Result queryBlogLikes(Long id) {
        //1：查询前5位点赞的用户
        String key = "blog:like:" + id;
        Set<String> userSet = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        //可能没人点赞，userSet为空
        if(userSet == null || userSet.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //2：解析出用户id
        List<Long> ids = userSet.stream().map(Long::valueOf).collect(Collectors.toList());
        //3：根据用户id查询用户信息，并返回UserDTO
        String ids_str = StrUtil.join(",", ids);
        List<UserDTO> userDTOs = userService.query().in("id", ids).last("ORDER BY FIELD(id," + ids_str + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        //返回userDTOs给前端
        return Result.ok(userDTOs);



    }

    public Result queryBlogOfFollows(Long max, Integer offset){
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.查询收件箱
        String key = "feed:" + userId;
        Long maxTime = System.currentTimeMillis();
        int os = 1;
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, maxTime, offset, 3);
        //3.判断是否为空
        if(tuples == null || tuples.isEmpty()){
            return Result.ok();
        }
        //4.解析获取数据：blogId，minTime，offset
        ArrayList<Long> blogIds = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            blogIds.add(Long.valueOf(tuple.getValue()));
            long timeScore = tuple.getScore().longValue();
            if(timeScore == maxTime) {
                os++;
            } else {
                maxTime = timeScore;
                os = 1;
            }

        }
        //5.根据数据查询blog
        String idStr = StrUtil.join(",", blogIds);
        List<Blog> blogs = query().in("id", blogIds).last("ORDER BY FIELD(id," + idStr + ")").list();
        //6.封装并返回数据
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setMinTime(maxTime);
        scrollResult.setOffset(os);
        return Result.ok(scrollResult);
    }
}
