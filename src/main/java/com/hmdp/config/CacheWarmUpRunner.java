package com.hmdp.config;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * 缓存预热组件
 * 应用启动时将热点数据加载到缓存中
 */
@Slf4j
@Component
public class CacheWarmUpRunner implements CommandLineRunner {

    @Resource
    private IShopService shopService;

    @Override
    public void run(String... args) {
        log.info("开始缓存预热...");
        
        try {
            // 预热热门商铺数据
            warmUpShopCache();
            
            log.info("缓存预热完成");
        } catch (Exception e) {
            log.error("缓存预热失败", e);
        }
    }

    /**
     * 预热商铺缓存
     */
    private void warmUpShopCache() {
        // 查询所有商铺（实际项目中可以只查询热门商铺）
        List<Shop> shopList = shopService.list();
        
        for (Shop shop : shopList) {
            String key = CACHE_SHOP_KEY + shop.getId();
            // 加载到Redis缓存
            shopService.saveShop2Redis(shop.getId(), 30L);
        }
        
        log.info("商铺缓存预热完成，共 {} 条数据", shopList.size());
    }
}
