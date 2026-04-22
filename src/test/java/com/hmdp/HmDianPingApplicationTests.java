package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private RedisIdWorker redisIdWorker;

    private ExecutorService executorService = Executors.newFixedThreadPool(500);
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IShopService shopService;

    @Test
    void contextLoads() {
        CountDownLatch countDownLatch = new CountDownLatch(300);



        long start = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            long id = redisIdWorker.nextId("order");
            System.out.println("id = " + id);
            countDownLatch.countDown();
        }
        long end = System.currentTimeMillis();
        System.out.println("总耗时： " + (end - start));
    }

    @Test
    void test1() {
        stringRedisTemplate.opsForValue().set("order:1", "1");
        stringRedisTemplate.opsForValue().set("order:2", "2");
        stringRedisTemplate.opsForValue().set("order:3", "3");
        stringRedisTemplate.opsForValue().set("order:4", "4");
        String s = stringRedisTemplate.opsForValue().get("order:1");
        System.out.println(s);
    }

    @Test
    void testAddShop() {
        //1.获取商品信息
        List<Shop> list = shopService.list();
        //2.根据typeId分类保存到map
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //3.遍历map，分批写入redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            Long typeId = entry.getKey();
            String key = "shop:geo:" + typeId;
            List<Shop> shops = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(shops.size());
            for(Shop  shop : shops){
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }


    }
}
