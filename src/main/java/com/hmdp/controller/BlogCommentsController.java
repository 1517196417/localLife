package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IBlogCommentsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/blog-comments")
public class BlogCommentsController {

    @Resource
    private IBlogCommentsService blogCommentsService;

    /**
     * 发表评论（一级评论）
     * @param blogId 笔记ID
     * @param content 评论内容
     * @param images 评论图片，多个图片以逗号分隔
     * @return 操作结果
     */
    @PostMapping("/blog/{blogId}")
    public Result addComment(@PathVariable("blogId") Long blogId, 
                            @RequestParam("content") String content,
                            @RequestParam(value = "images", required = false) String images) {
        return blogCommentsService.addComment(blogId, content, images);
    }

    /**
     * 查询评论列表（按时间倒序）
     * @param blogId 笔记ID
     * @return 评论列表
     */
    @GetMapping("/blog/{blogId}")
    public Result getCommentsByBlogId(@PathVariable("blogId") Long blogId) {
        return blogCommentsService.getCommentsByBlogId(blogId);
    }

    /**
     * 点赞评论
     * @param commentId 评论ID
     * @return 操作结果
     */
    @PutMapping("/like/{commentId}")
    public Result likeComment(@PathVariable("commentId") Long commentId) {
        return blogCommentsService.likeComment(commentId);
    }

    /**
     * 删除评论（软删除）
     * @param commentId 评论ID
     * @return 操作结果
     */
    @DeleteMapping("/{commentId}")
    public Result deleteComment(@PathVariable("commentId") Long commentId) {
        return blogCommentsService.deleteComment(commentId);
    }
}
