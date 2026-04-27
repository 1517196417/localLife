package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.entity.BlogDocument;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    BlogDocument convertToBlogDocument(Blog blog);
    Result saveBlog(Blog blog);

    Result queryHotBlog (Integer current);

    Result queryBlogById(Long id);

    void likeBlog(Long id);

    Result queryBlogLikes(Long id);

    Result recommendBlogs();
    Result queryBlogOfFollows(Long max, Integer offset);
    
    /**
     * 检查博客是否被当前用户点赞
     * @param blog 博客对象
     */
    void isBlogLiked(Blog blog);
    
    /**
     * 删除我的笔记
     * @param id 笔记ID
     * @return 删除结果
     */
    Result deleteBlog(Long id);
    
    /**
     * 更新我的笔记
     * @param blog 笔记信息
     * @return 更新结果
     */
    Result updateBlog(Blog blog);
}
