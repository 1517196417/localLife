package com.hmdp.dto;

import lombok.Data;

/**
 * 搜索请求DTO
 */
@Data
public class SearchRequest {
    
    /**
     * 搜索关键字
     */
    private String keyword;
    
    /**
     * 搜索类型：shop-商铺, blog-博客, user-用户, all-全部
     */
    private String type = "all";
    
    /**
     * 商铺排序方式：distance-距离, popularity-人气, score-评分
     */
    private String shopSortBy = "score";
    
    /**
     * 博客排序方式：latest-最新, hot-最热
     */
    private String blogSortBy = "hot";
    
    /**
     * 用户排序方式：fans-粉丝数（默认）
     */
    private String userSortBy = "fans";
    
    /**
     * 用户当前位置经度（用于距离排序）
     */
    private Double x;
    
    /**
     * 用户当前位置纬度（用于距离排序）
     */
    private Double y;
    
    /**
     * 页码
     */
    private Integer current = 1;
    
    /**
     * 每页数量
     */
    private Integer size = 10;
}
