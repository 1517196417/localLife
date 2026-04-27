package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.dto.SearchRequest;
import com.hmdp.service.ISearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 统一搜索控制器
 */
@Slf4j
@RestController
@RequestMapping("/search")
public class SearchController {
    
    @Resource
    private ISearchService searchService;
    
    /**
     * 统一搜索接口
     * @param request 搜索请求
     * @return 搜索结果
     */
    @PostMapping
    public Result search(@RequestBody SearchRequest request) {
        log.info("搜索请求：{}", request);
        return searchService.search(request);
    }
    
    /**
     * GET方式的搜索（方便测试）
     */
    @GetMapping
    public Result searchGet(
            @RequestParam String keyword,
            @RequestParam(required = false, defaultValue = "all") String type,
            @RequestParam(required = false, defaultValue = "score") String shopSortBy,
            @RequestParam(required = false, defaultValue = "hot") String blogSortBy,
            @RequestParam(required = false) Double x,
            @RequestParam(required = false) Double y,
            @RequestParam(required = false, defaultValue = "1") Integer current
    ) {
        SearchRequest request = new SearchRequest();
        request.setKeyword(keyword);
        request.setType(type);
        request.setShopSortBy(shopSortBy);
        request.setBlogSortBy(blogSortBy);
        request.setX(x);
        request.setY(y);
        request.setCurrent(current);
        
        return searchService.search(request);
    }
}
