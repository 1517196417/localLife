package com.hmdp.service;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.nio.charset.Charset;
import java.util.List;

@Slf4j
@Service
public class BloomFilterService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private BloomFilter<Long> shopBloomFilter;

    @Autowired
    private ShopMapper shopMapper;
    @PostConstruct
    public void init() {
        // 初始化布隆过滤器，预计插入10000个元素，误判率0.01
        shopBloomFilter = BloomFilter.create(Funnels.longFunnel(), 10000, 0.01);
        // 从数据库加载所有shop id到布隆过滤器
        loadShopIds();
    }

    private void loadShopIds() {
        // 这里应该从数据库查询所有shop id，但为了简化，假设通过其他方式获取
        // 例如，从ShopService获取
        List<Shop> shops = shopMapper.selectList(null);
        if (shops != null && !shops.isEmpty()) {
            for (Shop shop : shops) {
                shopBloomFilter.put(shop.getId());
            }
            log.info("布隆过滤器初始化完成，已加载 {} 个商铺ID", shops.size());
        } else {
            log.warn("布隆过滤器初始化完成，但未加载任何商铺ID");
        }
    }

    public boolean mightContain(Long shopId) {
        return shopBloomFilter.mightContain(shopId);
    }

    public void add(Long shopId) {
        shopBloomFilter.put(shopId);
    }
}
