package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.SearchRequest;
import com.hmdp.dto.SearchResponse;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopDocument;
import com.hmdp.entity.ShopType;
import com.hmdp.entity.User;
import com.hmdp.repository.ShopRepository;
import com.hmdp.service.IFollowService;
import com.hmdp.service.ISearchService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IShopTypeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 搜索服务实现
 */
@Slf4j
@Service
public class SearchServiceImpl implements ISearchService {
    
    @Resource
    private IShopService shopService;
    
    @Resource
    private com.hmdp.service.IBlogService blogService;
    
    @Resource
    private com.hmdp.service.IUserService userService;
    
    @Resource
    private IFollowService followService;
    
    @Resource
    private IShopTypeService shopTypeService;
    
    @Resource
    private ShopRepository shopRepository;
    
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    @Override
    public Result search(SearchRequest request) {
        String keyword = request.getKeyword();
        if (StrUtil.isBlank(keyword)) {
            return Result.fail("搜索关键字不能为空");
        }
        
        SearchResponse response = new SearchResponse();
        String type = request.getType();
        
        try {
            // 根据类型执行不同的搜索
            if ("all".equals(type) || "shop".equals(type)) {
                response.setShops(searchShops(request));
            }
            if ("all".equals(type) || "blog".equals(type)) {
                response.setBlogs(searchBlogs(request));
            }
            if ("all".equals(type) || "user".equals(type)) {
                response.setUsers(searchUsers(request));
            }
            
            return Result.ok(response);
        } catch (Exception e) {
            log.error("搜索失败", e);
            return Result.fail("搜索失败：" + e.getMessage());
        }
    }
    
    /**
     * 搜索商铺
     */
    private List<SearchResponse.ShopResult> searchShops(SearchRequest request) {
        String keyword = request.getKeyword();
        String sortBy = request.getShopSortBy();
        
        // 使用数据库搜索（支持多种排序）
        LambdaQueryWrapper<Shop> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(Shop::getName, keyword)
                .or()
                .like(Shop::getAddress, keyword)
                .or()
                .like(Shop::getArea, keyword);
        
        // 根据排序方式排序
        if ("distance".equals(sortBy) && request.getX() != null && request.getY() != null) {
            // 距离排序需要在内存中计算
            queryWrapper.last("LIMIT 100"); // 先限制查询数量
        } else if ("popularity".equals(sortBy)) {
            // 按人气（销量）排序
            queryWrapper.orderByDesc(Shop::getSold);
        } else {
            // 默认按评分排序
            queryWrapper.orderByDesc(Shop::getScore);
        }
        
        List<Shop> shops = shopService.list(queryWrapper);
        
        // 查询商铺类型
        List<Long> typeIds = shops.stream().map(Shop::getTypeId).distinct().collect(Collectors.toList());
        Map<Long, ShopType> typeMap = shopTypeService.listByIds(typeIds).stream()
                .collect(Collectors.toMap(ShopType::getId, t -> t));
        
        // 转换为结果
        List<SearchResponse.ShopResult> results = new ArrayList<>();
        for (Shop shop : shops) {
            SearchResponse.ShopResult result = new SearchResponse.ShopResult();
            result.setId(shop.getId());
            result.setName(shop.getName());
            result.setImages(shop.getImages());
            result.setArea(shop.getArea());
            result.setAddress(shop.getAddress());
            result.setX(shop.getX());
            result.setY(shop.getY());
            result.setAvgPrice(shop.getAvgPrice());
            result.setSold(shop.getSold());
            result.setComments(shop.getComments());
            result.setScore(shop.getScore());
            result.setOpenHours(shop.getOpenHours());
            result.setTypeId(shop.getTypeId());
            
            ShopType type = typeMap.get(shop.getTypeId());
            if (type != null) {
                result.setTypeName(type.getName());
            }
            
            // 计算距离
            if (request.getX() != null && request.getY() != null) {
                double distance = calculateDistance(
                    request.getX(), request.getY(),
                    shop.getX(), shop.getY()
                );
                result.setDistance(distance);
            }
            
            results.add(result);
        }
        
        // 如果是距离排序，在内存中排序
        if ("distance".equals(sortBy) && request.getX() != null && request.getY() != null) {
            results.sort((a, b) -> {
                if (a.getDistance() == null) return 1;
                if (b.getDistance() == null) return -1;
                return a.getDistance().compareTo(b.getDistance());
            });
        }
        
        return results;
    }
    
    /**
     * 搜索博客
     */
    private List<Blog> searchBlogs(SearchRequest request) {
        String keyword = request.getKeyword();
        String sortBy = request.getBlogSortBy();
        
        LambdaQueryWrapper<Blog> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(Blog::getTitle, keyword)
                .or()
                .like(Blog::getContent, keyword);
        
        // 根据排序方式排序
        if ("latest".equals(sortBy)) {
            // 最新
            queryWrapper.orderByDesc(Blog::getCreateTime);
        } else {
            // 最热（点赞数）
            queryWrapper.orderByDesc(Blog::getLiked);
        }
        
        queryWrapper.last("LIMIT 20"); // 限制数量
        
        return blogService.list(queryWrapper);
    }
    
    /**
     * 搜索用户
     */
    private List<SearchResponse.UserResult> searchUsers(SearchRequest request) {
        String keyword = request.getKeyword();
        
        // 搜索用户（昵称包含关键字）
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(User::getNickName, keyword)
                .orderByDesc(User::getCreateTime)  // MySQL没有fans字段，暂时按创建时间排序
                .last("LIMIT 20");
        
        List<User> users = userService.list(queryWrapper);
        
        // 转换为结果
        List<SearchResponse.UserResult> results = new ArrayList<>();
        for (User user : users) {
            SearchResponse.UserResult result = new SearchResponse.UserResult();
            result.setId(user.getId());
            result.setNickName(user.getNickName());
            result.setIcon(user.getIcon());
            
            // TODO: 查询粉丝数（如果有关注表的话）
            // 这里暂时设置为0
            result.setFansCount(0);
            
            results.add(result);
        }
        
        return results;
    }
    
    /**
     * 计算两点之间的距离（使用Haversine公式）
     * @return 距离（米）
     */
    private double calculateDistance(double lon1, double lat1, double lon2, double lat2) {
        final int R = 6371000; // 地球半径（米）
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }
}
