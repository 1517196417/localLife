package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.dto.SearchRequest;

/**
 * 搜索服务接口
 */
public interface ISearchService {
    
    /**
     * 统一搜索接口
     * @param request 搜索请求
     * @return 搜索结果
     */
    Result search(SearchRequest request);
}
