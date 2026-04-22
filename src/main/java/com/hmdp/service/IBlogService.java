package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {
    Result saveBlog(Blog blog);

    Result queryHotBlog (Integer current);

    Result queryBlogById(Long id);

    void likeBlog(Long id);

    Result queryBlogLikes(Long id);

    Result queryBlogOfFollows(Long max, Integer offset);
}
