package com.hmdp.dto;

import com.hmdp.entity.Blog;
import com.hmdp.entity.Shop;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 搜索结果响应DTO
 */
@Data
public class SearchResponse {
    
    /**
     * 商铺列表
     */
    private List<ShopResult> shops;
    
    /**
     * 博客列表
     */
    private List<Blog> blogs;
    
    /**
     * 用户列表
     */
    private List<UserResult> users;
    
    /**
     * 商铺搜索结果
     */
    @Data
    public static class ShopResult {
        private Long id;
        private String name;
        private String images;
        private String area;
        private String address;
        private Double x;
        private Double y;
        private Long avgPrice;
        private Integer sold;          // 销量（人气）
        private Integer comments;      // 评论数
        private Integer score;         // 评分
        private String openHours;
        private Double distance;       // 距离（米）
        private Long typeId;
        private String typeName;       // 类型名称
    }
    
    /**
     * 用户搜索结果
     */
    @Data
    public static class UserResult {
        private Long id;
        private String nickName;
        private String icon;
        private Integer fansCount;     // 粉丝数
        private String introduce;      // 简介
    }
}
