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
import com.hmdp.entity.BlogDocument;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.repository.BlogRepository;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import com.hmdp.websocket.WebSocketServer;
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
    @Resource
    private BlogRepository blogRepository;

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

        // 同步到Elasticsearch
        BlogDocument blogDocument = convertToBlogDocument(blog);
        blogRepository.save(blogDocument);

        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.BLOG_PAGE_SIZE));
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
        // 记录用户浏览行为
        UserDTO user = UserHolder.getUser();
        if(user != null) {
            String key = "user:view:" + user.getId();
            stringRedisTemplate.opsForZSet().add(key, id.toString(), System.currentTimeMillis());
        }
        return Result.ok(blog);
    }

    @Override
    public void isBlogLiked(Blog blog) {
        // 获取当前登录用户，如果未登录则默认未点赞
        UserDTO user = UserHolder.getUser();
        if(user == null){
            blog.setIsLike(false);
            return;
        }
        Long userId = user.getId();
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
            // 记录用户点赞行为
            String userLikeKey = "user:like:" + userId;
            stringRedisTemplate.opsForZSet().add(userLikeKey, id.toString(), System.currentTimeMillis());
            // 发送点赞通知
            Long blogUserId = blog.getUserId();
            if (!blogUserId.equals(userId)) {
                WebSocketServer.sendMessage(blogUserId, "您的博客被用户" + userId + "点赞了");
            }
        }
        //2.2：已点赞，取消点赞（数据库-1），将userId移除redis
        else{
            update().setSql("liked = liked - 1")
                    .eq("id", id)
                    .update();

            stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            // 移除用户点赞行为
            String userLikeKey = "user:like:" + userId;
            stringRedisTemplate.opsForZSet().remove(userLikeKey, id.toString());
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

    // ... existing code ...
    @Override
    public Result recommendBlogs() {
        UserDTO user = UserHolder.getUser();
        
        // 如果用户未登录，直接返回热门博客
        if (user == null) {
            return queryHotBlog(1);
        }
        Long userId = user.getId();

        String userLikeKey = "user:like:" + userId;
        Set<String> likedBlogs = stringRedisTemplate.opsForZSet().range(userLikeKey, 0, -1);
        
        // 如果用户没有点赞记录，返回热门博客
        if (likedBlogs == null || likedBlogs.isEmpty()) {
            return queryHotBlog(1);
        }

        // 统计每个点赞用户的相似度（点赞相同博客的数量）
        java.util.Map<Long, Integer> userSimilarityMap = new java.util.HashMap<>();
        for (String blogId : likedBlogs) {
            String blogLikeKey = "blog:like:" + blogId;
            Set<String> likers = stringRedisTemplate.opsForZSet().range(blogLikeKey, 0, -1);
            if (likers != null) {
                for (String likerId : likers) {
                    Long likerUid = Long.valueOf(likerId);
                    if (!likerUid.equals(userId)) {
                        userSimilarityMap.merge(likerUid, 1, Integer::sum);
                    }
                }
            }
        }

        // 如果没有相似用户，返回热门博客
        if (userSimilarityMap.isEmpty()) {
            return queryHotBlog(1);
        }

        // 按相似度排序，取前10个相似用户
        List<Long> similarUsers = userSimilarityMap.entrySet().stream()
                .sorted(java.util.Map.Entry.<Long, Integer>comparingByValue().reversed())
                .limit(10)
                .map(java.util.Map.Entry::getKey)
                .collect(Collectors.toList());

        // 获取这些相似用户点赞的其他博客
        Set<String> recommendBlogIds = new java.util.HashSet<>();
        for (Long similarUid : similarUsers) {
            String otherLikeKey = "user:like:" + similarUid;
            Set<String> otherLikes = stringRedisTemplate.opsForZSet().range(otherLikeKey, 0, -1);
            if (otherLikes != null) {
                for (String blogId : otherLikes) {
                    if (!likedBlogs.contains(blogId)) {
                        recommendBlogIds.add(blogId);
                    }
                }
            }
        }

        // 查询博客详情
        List<Blog> recommendBlogs = new java.util.ArrayList<>();
        if (!recommendBlogIds.isEmpty()) {
            List<Long> ids = recommendBlogIds.stream()
                    .map(Long::valueOf)
                    .collect(Collectors.toList());

            // 获取全部推荐博客，不限制10条，让前端做分页
            recommendBlogs = query().in("id", ids).orderByDesc("liked").list();
            recommendBlogs.forEach(b -> {
                queryBlogUser(b);
                isBlogLiked(b);
            });
        }
        
        // 如果推荐数据不足5条，使用热门博客补充
        int minRecommendCount = 5;
        if (recommendBlogs.size() < minRecommendCount) {
            // 查询热门博客
            Page<Blog> hotPage = query()
                    .orderByDesc("liked")
                    .page(new Page<>(1, SystemConstants.MAX_PAGE_SIZE));
            List<Blog> hotBlogs = hotPage.getRecords();
            
            // 过滤掉已经存在的推荐博客
            Set<Long> existingIds = recommendBlogs.stream()
                    .map(Blog::getId)
                    .collect(Collectors.toSet());
            
            for (Blog hotBlog : hotBlogs) {
                if (!existingIds.contains(hotBlog.getId())) {
                    queryBlogUser(hotBlog);
                    isBlogLiked(hotBlog);
                    recommendBlogs.add(hotBlog);
                }
            }
        }

        return Result.ok(recommendBlogs);
    }

    public BlogDocument convertToBlogDocument(Blog blog) {
        BlogDocument document = new BlogDocument();
        document.setId(blog.getId());

        document.setUserId(blog.getUserId());
        document.setTitle(blog.getTitle());
        document.setContent(blog.getContent());
        document.setTags(blog.getTags());
        document.setLiked(blog.getLiked());
        document.setComments(blog.getComments());
        document.setCreateTime(blog.getCreateTime());
        document.setUpdateTime(blog.getUpdateTime());
        return document;
    }
    
    @Override
    public Result deleteBlog(Long id) {
        // 1. 获取当前登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录");
        }
        
        // 2. 查询笔记
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        
        // 3. 校验权限（只能删除自己的笔记）
        if (!blog.getUserId().equals(user.getId())) {
            return Result.fail("无权删除该笔记");
        }
        
        // 4. 删除笔记
        boolean success = removeById(id);
        if (!success) {
            return Result.fail("删除笔记失败");
        }
        
        // 5. 从Elasticsearch中删除
        try {
            blogRepository.deleteById(id);
        } catch (Exception e) {
            // ES删除失败不影响主流程，只记录日志
            e.printStackTrace();
        }
        
        return Result.ok();
    }
    
    @Override
    public Result updateBlog(Blog blog) {
        // 1. 获取当前登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录");
        }
        
        // 2. 查询笔记
        Blog existingBlog = getById(blog.getId());
        if (existingBlog == null) {
            return Result.fail("笔记不存在");
        }
        
        // 3. 校验权限（只能修改自己的笔记）
        if (!existingBlog.getUserId().equals(user.getId())) {
            return Result.fail("无权修改该笔记");
        }
        
        // 4. 更新笔记（只更新标题、内容、图片、关联商户）
        Blog updateBlog = new Blog();
        updateBlog.setId(blog.getId());
        if (blog.getTitle() != null) {
            updateBlog.setTitle(blog.getTitle());
        }
        if (blog.getContent() != null) {
            updateBlog.setContent(blog.getContent());
        }
        if (blog.getImages() != null) {
            updateBlog.setImages(blog.getImages());
        }
        if (blog.getShopId() != null) {
            updateBlog.setShopId(blog.getShopId());
        }
        
        boolean success = updateById(updateBlog);
        if (!success) {
            return Result.fail("更新笔记失败");
        }
        
        // 5. 更新Elasticsearch
        try {
            BlogDocument document = convertToBlogDocument(getById(blog.getId()));
            blogRepository.save(document);
        } catch (Exception e) {
            // ES更新失败不影响主流程，只记录日志
            e.printStackTrace();
        }
        
        return Result.ok();
    }
}