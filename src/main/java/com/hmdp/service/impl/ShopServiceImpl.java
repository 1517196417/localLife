package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.BloomFilterService;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.hmdp.utils.SystemConstants;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.sql.Time;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    private final StringRedisTemplate stringRedisTemplate;

    public ShopServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Autowired
    private BloomFilterService bloomFilterService;

    @Override
    public Object queryById(Long id) {
        Shop shop = queryWithMutex(id);
        if (shop == null) {
            return Result.fail("店铺不存在!");
        }
        return Result.ok(shop);
    }

    /**
     * @Override
     * public Result queryById(Long id) {
     *     // 使用多级缓存
     *     String l1Key = "shop:cache:" + id;
     *     String l2Key = RedisConstants.CACHE_SHOP_KEY + id;
     *
     *     String shopJson = multiLevelCache.get(
     *         l1Key,
     *         l2Key,
     *         30L,
     *         (key) -> {
     *             Shop shop = getById(id);
     *             return shop != null ? JSONUtil.toJsonStr(shop) : null;
     *         }
     *     );
     *
     *     if (shopJson == null || shopJson.isEmpty()) {
     *         return Result.fail("商铺不存在");
     *     }
     *
     *     Shop shop = JSONUtil.toBean(shopJson, Shop.class);
     *     return Result.ok(shop);
     * }

     */

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y, String name, String sortBy) {
        // 如果有搜索关键字，优先按关键字搜索（忽略距离排序和GEO）
        if (StrUtil.isNotBlank(name)) {
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .like("name", name)
                    .orderBy(StrUtil.isNotBlank(sortBy), "score".equals(sortBy) || "comments".equals(sortBy), sortBy)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }

        // 如果有sortBy且不是距离排序，直接使用数据库排序（不需要GEO距离）
        if (StrUtil.isNotBlank(sortBy) && !"distance".equals(sortBy)) {
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .orderBy(true, false, sortBy) // 降序排列
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }

        //1.判断是否需要根据距离来排序商家
        if(x == null || y == null)
        {
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }

        // 2. 计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // 3. 查询redis、按照距离排序、分页。结果：shopId、distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo() // GEOSEARCH key BYLONL
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        // 不限制距离范围，获取该类型下所有店铺按距离排序的结果
                        new Distance(Long.MAX_VALUE, Metrics.KILOMETERS),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );

        // 4. 解析出id
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        // 4.1. 截取 from ~ end 的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            // 4.2. 获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            // 4.3. 获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // 5. 根据id查询Shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }

        // 6. 返回
        return Result.ok(shops);

    }

    private static final ExecutorService pool = Executors.newFixedThreadPool(10);
    public Shop queryWithLogicalExpire(Long id) {
        String shopKey = CACHE_SHOP_KEY + id;
        //1：从redis缓存取出商户信息
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        //2：redis缓存未命中，直接返回
        if(StrUtil.isBlank(shopJson)) {
            return null;
        }

        //3：redis缓存命中，判断是否过期
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        System.out.println("expireTime: " + expireTime);
        //3.1：未过期直接返回商户信息
        if(expireTime.isAfter(LocalDateTime.now())) {
            return shop;
        }
        //3.2：信息过期，准备缓存重建
        String lockKey = LOCK_SHOP_KEY + id;
        if(tryLock(lockKey)) {
            //3.2.1：开启独立线程
            pool.submit(() -> {
                try {
                    this.saveShop2Redis(id, 20L);

                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unLock(lockKey);
                }
            });
        }
        //3.2.2：没拿到锁，直接返回旧信息
        return shop;
    }

    public void saveShop2Redis(Long id, Long expireSeconds) {
        Shop shop = getById(id);
        try {
            Thread.sleep(200L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));

        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    public Shop queryWithMutex(Long id)  {
        String shopKey = CACHE_SHOP_KEY + id;
        //1：从redis缓存取出商户信息
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        //2：redis缓存命中，直接返回
        if(StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //为解决缓存穿透，从redis中获取到”“就直接返回错误信息，防止访问数据库
        if(shopKey != null) {
            return null;
        }
        String lockKey = LOCK_SHOP_KEY + id;
        //3:获取互斥锁 判断是否获取到锁，为获取就递归调用获取锁
        Shop shop = null;
        try {
            if(!tryLock(lockKey)){
                Thread.sleep(50);
                queryWithMutex(id);
            }
            //4：redis缓存未命中，从数据库里查询
            shop = getById(id);
            Thread.sleep(200);
            //5：数据库不存在，直接返回店铺不存在,并在redis缓存中设置空字符串，防止缓存穿透
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //6：数据库存在，先加入redis，再返回店铺
            stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //7:释放互斥锁
            unLock(lockKey);
        }
        return shop;
    }

    private Boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    private Boolean unLock(String key) {
        return stringRedisTemplate.delete(key);
    }

    //封装好解决了缓存穿透的商户查询方法
    public Shop queryWithPassThrough(Long id) {
        // 先检查布隆过滤器
        if (!bloomFilterService.mightContain(id)) {
            return null; // 肯定不存在
        }
        String shopKey = CACHE_SHOP_KEY + id;
        //1：从redis缓存取出商户信息
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        //2：redis缓存命中，直接返回
        if(StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //为解决缓存穿透，从redis中获取到”“就直接返回错误信息，防止访问数据库
        if(shopJson != null) {
            return null;
        }
        //3：redis缓存未命中，从数据库里查询
        Shop shop = getById(id);
        //4：数据库不存在，直接返回店铺不存在,并在redis缓存中设置空字符串，防止缓存穿透
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //5：数据库存在，先加入redis，再返回店铺
        stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop));
        return shop;
    }

    @Override
    public boolean save(Shop entity) {
        boolean result = super.save(entity);
        if (result) {
            // 添加GEO数据
            stringRedisTemplate.opsForGeo().add(SHOP_GEO_KEY + entity.getTypeId(), new RedisGeoCommands.GeoLocation<>(entity.getId().toString(), new org.springframework.data.geo.Point(entity.getX(), entity.getY())));
            // 添加到布隆过滤器
            bloomFilterService.add(entity.getId());
        }
        return result;
    }

@Override
@Transactional
public Result update(Shop shop) {
    Long shopId = shop.getId();
    if (shopId == null) {
        return Result.fail("店铺id不能为空");
    }
    //1：先更新数据库
    updateById(shop);
    //2：再删除缓存
    stringRedisTemplate.delete(CACHE_SHOP_KEY + shopId);
    //3：更新GEO数据
    stringRedisTemplate.opsForGeo().add(SHOP_GEO_KEY + shop.getTypeId(), new RedisGeoCommands.GeoLocation<>(shop.getId().toString(), new org.springframework.data.geo.Point(shop.getX(), shop.getY())));
    return Result.ok();
}



}
