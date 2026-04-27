package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopComments;

/**
 * <p>
 * 商铺评论服务类
 * </p>
 *
 * @author localLife
 * @since 2026-04-25
 */
public interface IShopCommentsService extends IService<ShopComments> {

    /**
     * 发表商铺评论
     * @param shopId 商铺ID
     * @param content 评论内容
     * @param images 评论图片，多个图片以逗号分隔
     * @return 操作结果
     */
    Result addShopComment(Long shopId, String content, String images);

    /**
     * 查询商铺评论列表（按时间倒序）
     * @param shopId 商铺ID
     * @return 评论列表
     */
    Result getCommentsByShopId(Long shopId);

    /**
     * 点赞评论
     * @param commentId 评论ID
     * @return 操作结果
     */
    Result likeComment(Long commentId);

    /**
     * 删除评论（软删除）
     * @param commentId 评论ID
     * @return 操作结果
     */
    Result deleteComment(Long commentId);
}
