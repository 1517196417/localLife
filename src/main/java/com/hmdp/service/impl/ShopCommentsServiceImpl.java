package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.ShopComments;
import com.hmdp.entity.User;
import com.hmdp.mapper.ShopCommentsMapper;
import com.hmdp.service.IShopCommentsService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 商铺评论服务实现类
 * </p>
 *
 * @author localLife
 * @since 2026-04-25
 */
@Slf4j
@Service
public class ShopCommentsServiceImpl extends ServiceImpl<ShopCommentsMapper, ShopComments> implements IShopCommentsService {

    @Resource
    private IUserService userService;

    @Override
    @Transactional
    public Result addShopComment(Long shopId, String content, String images) {
        try {
            // 1. 获取当前登录用户
            UserDTO userDTO = UserHolder.getUser();
            if (userDTO == null) {
                return Result.fail("请先登录");
            }
            Long userId = userDTO.getId();
            
            // 2. 参数校验
            if (content == null || content.trim().isEmpty()) {
                return Result.fail("评论内容不能为空");
            }
            if (content.length() > 500) {
                return Result.fail("评论内容不能超过500字");
            }
            
            // 3. 创建评论对象
            ShopComments comment = new ShopComments();
            comment.setShopId(shopId);
            comment.setUserId(userId);
            comment.setContent(content.trim());
            comment.setImages(images);
            comment.setParentId(0L); // 一级评论
            comment.setAnswerId(0L);
            comment.setLiked(0);
            comment.setStatus(false); // 0-正常
            
            // 4. 保存到数据库
            boolean success = save(comment);
            if (!success) {
                return Result.fail("评论失败，请稍后重试");
            }
            
            log.info("用户 {} 对商铺 {} 发表评论: {}", userId, shopId, content);
            return Result.ok(comment);
        } catch (Exception e) {
            log.error("发表商铺评论异常", e);
            return Result.fail("评论失败，请稍后重试");
        }
    }

    @Override
    public Result getCommentsByShopId(Long shopId) {
        try {
            // 1. 查询商铺评论列表（按时间倒序，只查询正常状态的评论）
            QueryWrapper<ShopComments> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("shop_id", shopId)
                       .eq("status", false) // 0-正常
                       .orderByDesc("create_time");
            
            List<ShopComments> comments = list(queryWrapper);
            
            // 2. 填充用户信息
            List<CommentVO> commentVOList = comments.stream().map(comment -> {
                CommentVO vo = new CommentVO();
                vo.setId(comment.getId());
                vo.setUserId(comment.getUserId());
                vo.setShopId(comment.getShopId());
                vo.setContent(comment.getContent());
                vo.setImages(comment.getImages());
                vo.setLiked(comment.getLiked());
                vo.setCreateTime(comment.getCreateTime());
                
                // 查询用户信息
                User user = userService.getById(comment.getUserId());
                if (user != null) {
                    vo.setUserName(user.getNickName());
                    vo.setUserIcon(user.getIcon());
                }
                
                return vo;
            }).collect(Collectors.toList());
            
            return Result.ok(commentVOList);
        } catch (Exception e) {
            log.error("查询商铺评论列表异常", e);
            return Result.fail("查询失败，请稍后重试");
        }
    }

    @Override
    @Transactional
    public Result likeComment(Long commentId) {
        try {
            // 1. 检查登录状态
            UserDTO userDTO = UserHolder.getUser();
            if (userDTO == null) {
                return Result.fail("请先登录");
            }
            
            // 2. 查询评论是否存在
            ShopComments comment = getById(commentId);
            if (comment == null) {
                return Result.fail("评论不存在");
            }
            
            // 3. 点赞数 +1
            comment.setLiked(comment.getLiked() + 1);
            boolean success = updateById(comment);
            
            if (success) {
                log.info("评论 {} 点赞成功，当前点赞数: {}", commentId, comment.getLiked());
                return Result.ok(comment.getLiked());
            } else {
                return Result.fail("点赞失败");
            }
        } catch (Exception e) {
            log.error("点赞评论异常", e);
            return Result.fail("点赞失败，请稍后重试");
        }
    }

    @Override
    @Transactional
    public Result deleteComment(Long commentId) {
        try {
            // 1. 获取当前用户
            UserDTO userDTO = UserHolder.getUser();
            if (userDTO == null) {
                return Result.fail("请先登录");
            }
            Long userId = userDTO.getId();
            
            // 2. 查询评论
            ShopComments comment = getById(commentId);
            if (comment == null) {
                return Result.fail("评论不存在");
            }
            
            // 3. 验证是否是自己的评论
            if (!comment.getUserId().equals(userId)) {
                return Result.fail("只能删除自己的评论");
            }
            
            // 4. 软删除（修改状态）
            comment.setStatus(true); // 1-被举报/删除
            boolean success = updateById(comment);
            
            if (success) {
                log.info("用户 {} 删除评论 {}", userId, commentId);
                return Result.ok();
            } else {
                return Result.fail("删除失败");
            }
        } catch (Exception e) {
            log.error("删除评论异常", e);
            return Result.fail("删除失败，请稍后重试");
        }
    }
    
    /**
     * 评论VO（用于返回给前端）
     */
    @lombok.Data
    static class CommentVO {
        private Long id;
        private Long userId;
        private Long shopId;
        private String content;
        private String images;
        private Integer liked;
        private String userName;
        private String userIcon;
        private java.time.LocalDateTime createTime;
    }
}
