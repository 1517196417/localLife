package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IShopCommentsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  商铺评论控制器
 * </p>
 *
 * @author localLife
 * @since 2026-04-25
 */
@Slf4j
@RestController
@RequestMapping("/shop-comments")
public class ShopCommentsController {

    @Resource
    private IShopCommentsService shopCommentsService;

    /**
     * 发表商铺评论
     * @param shopId 商铺ID
     * @param content 评论内容
     * @param images 评论图片，多个图片以逗号分隔
     * @return 操作结果
     */
    @PostMapping("/shop/{shopId}")
    public Result addShopComment(@PathVariable("shopId") Long shopId, 
                               @RequestParam("content") String content, 
                               @RequestParam(value = "images", required = false) String images) {
        return shopCommentsService.addShopComment(shopId, content, images);
    }

    /**
     * 查询商铺评论列表（按时间倒序）
     * @param shopId 商铺ID
     * @return 评论列表
     */
    @GetMapping("/shop/{shopId}")
    public Result getCommentsByShopId(@PathVariable("shopId") Long shopId) {
        return shopCommentsService.getCommentsByShopId(shopId);
    }

    /**
     * 点赞评论
     * @param commentId 评论ID
     * @return 操作结果
     */
    @PutMapping("/like/{commentId}")
    public Result likeComment(@PathVariable("commentId") Long commentId) {
        return shopCommentsService.likeComment(commentId);
    }

    /**
     * 删除评论（软删除）
     * @param commentId 评论ID
     * @return 操作结果
     */
    @DeleteMapping("/{commentId}")
    public Result deleteComment(@PathVariable("commentId") Long commentId) {
        return shopCommentsService.deleteComment(commentId);
    }
}