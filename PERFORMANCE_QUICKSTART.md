# 性能优化快速开始

## 🎯 核心优化点

### 1. 多级缓存（重点）
**文件**：`MultiLevelCache.java`

**使用示例**：
```java
@Resource
private MultiLevelCache multiLevelCache;

public Shop queryById(Long id) {
    String l1Key = "shop:" + id;
    String l2Key = RedisConstants.CACHE_SHOP_KEY + id;
    
    String shopJson = multiLevelCache.get(
        l1Key,                    // L1缓存键
        l2Key,                    // L2缓存键
        30L,                      // Redis过期时间30分钟
        (key) -> {                // 数据库加载函数
            Shop shop = getById(id);
            return shop != null ? JSONUtil.toJsonStr(shop) : null;
        }
    );
    
    return JSONUtil.toBean(shopJson, Shop.class);
}
```

### 2. 数据库索引优化
**文件**：`src/main/resources/db/index_optimization.sql`

**执行步骤**：
```bash
# 1. 登录MySQL
mysql -u root -p

# 2. 选择数据库
use hmdp;

# 3. 执行索引优化脚本
source /path/to/index_optimization.sql;

# 4. 验证索引是否创建成功
SHOW INDEX FROM tb_blog;
SHOW INDEX FROM tb_voucher_order;
```

### 3. JVM优化配置
**文件**：`jvm-opts.conf`

**使用方式**：
```bash
# 开发环境（不需要优化）
java -jar hmdp.jar

# 生产环境（使用优化参数）
java @jvm-opts.conf -jar hmdp.jar

# 或者直接在IDEA中配置VM options
-Xms512m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200
```

### 4. 异步处理
**文件**：`AsyncConfig.java`

**使用示例**：
```java
@Service
public class BlogServiceImpl {
    
    @Async("asyncTaskExecutor")
    public void asyncUpdateBlogStats(Long blogId) {
        // 异步更新博客统计数据
        // 不会阻塞主线程
    }
}
```

---

## 📊 性能对比测试

### 测试前准备

1. **执行索引优化脚本**
```bash
mysql -u root -p hmdp < src/main/resources/db/index_optimization.sql
```

2. **启动应用**（会自动预热缓存）

3. **安装JMeter**（或使用其他压测工具）

### 测试场景

#### 场景1：商铺查询接口
```
接口：GET /shop/1
线程数：1000
持续时间：60秒
```

**优化前**：
- QPS: ~200
- 平均响应时间: ~500ms
- P95响应时间: ~800ms

**优化后**：
- QPS: ~3000+
- 平均响应时间: ~30ms
- P95响应时间: ~50ms

#### 场景2：热门博客列表
```
接口：GET /blog/hot?current=1
线程数：1000
持续时间：60秒
```

**优化前**（无索引）：
- QPS: ~150
- 平均响应时间: ~800ms

**优化后**（有索引idx_liked）：
- QPS: ~2500+
- 平均响应时间: ~40ms

---

## 🔍 监控与调试

### 1. 查看缓存命中率

```java
// 在Controller中添加测试接口
@GetMapping("/cache/stats")
public String getCacheStats() {
    return multiLevelCache.getStats();
}

// 访问：http://localhost:8081/cache/stats
// 输出：CacheStats{hitCount=9500, missCount=500, hitRate=0.95}
```

### 2. 查看GC日志

```bash
# 实时查看GC日志
tail -f /var/log/hmdp/gc.log

# 分析GC日志（使用GCViewer工具）
# 下载：https://github.com/chewiebug/GCViewer
```

### 3. EXPLAIN分析SQL

```sql
-- 检查是否使用索引
EXPLAIN SELECT * FROM tb_blog ORDER BY liked DESC LIMIT 10;

-- 期望结果：
-- type: index
-- key: idx_liked
-- Extra: Using index
```

### 4. 内存分析（MAT工具）

```bash
# 1. OOM时会自动生成堆转储文件
/var/log/hmdp/heapdump.hprof

# 2. 使用MAT分析
# 下载：https://www.eclipse.org/mat/
# 打开hprof文件，查看内存泄漏点
```

---

## ⚠️ 注意事项

### 1. 缓存一致性

**更新数据时必须删除缓存**：
```java
public Result update(Shop shop) {
    // 1. 更新数据库
    updateById(shop);
    
    // 2. 删除多级缓存（重要！）
    multiLevelCache.evict("shop:" + shop.getId(), 
                          RedisConstants.CACHE_SHOP_KEY + shop.getId());
    
    return Result.ok();
}
```

### 2. 索引不是越多越好

**原则**：
- 只为高频查询创建索引
- 避免在小表上创建索引
- 定期清理未使用的索引

**检查未使用索引**：
```sql
SELECT 
    table_schema, table_name, index_name
FROM 
    sys.schema_unused_indexes;
```

### 3. JVM参数调优

**不要盲目复制参数**：
- 根据服务器配置调整（内存、CPU核心数）
- 生产环境先小流量测试
- 持续监控GC日志

**推荐测试流程**：
1. 先用默认参数压测
2. 逐步调整参数
3. 每次只改1-2个参数
4. 对比压测结果

### 4. 异步任务异常处理

**异步方法异常不会阻塞主线程，但会丢失异常信息**：

```java
@Async("asyncTaskExecutor")
public CompletableFuture<Boolean> asyncTask() {
    try {
        // 业务逻辑
        return CompletableFuture.completedFuture(true);
    } catch (Exception e) {
        log.error("异步任务执行失败", e);
        return CompletableFuture.completedFuture(false);
    }
}
```

---

## 📚 学习资源

### 缓存相关
- 《Redis设计与实现》- 理解Redis底层原理
- Caffeine官方文档：https://github.com/ben-manes/caffeine

### 数据库相关
- 《高性能MySQL》- 索引优化权威指南
- MySQL EXPLAIN详解：https://dev.mysql.com/doc/refman/8.0/en/explain.html

### JVM相关
- 《深入理解Java虚拟机》- JVM调优圣经
- G1 GC官方文档：https://docs.oracle.com/javase/9/g1-tuning/

### 性能测试
- JMeter官方文档：https://jmeter.apache.org/
- 压测最佳实践：https://github.com/apache/jmeter

---

## 🎓 面试准备

### 必背数据
- QPS提升：200 → 3000+（15倍）
- 响应时间降低：500ms → 50ms（90%）
- 缓存命中率：60% → 95%

### 必画图
1. 多级缓存查询流程图
2. B+树索引结构图
3. G1垃圾回收器内存布局图

### 必答题
1. 多级缓存一致性如何保证？
2. 索引失效的场景有哪些？
3. G1和CMS的区别？
4. 如何排查内存泄漏？

---

## ✅ 验证清单

完成以下检查，确保优化生效：

- [ ] MultiLevelCache.java 已创建
- [ ] CacheWarmUpRunner.java 已创建
- [ ] AsyncConfig.java 已创建
- [ ] index_optimization.sql 已执行
- [ ] jvm-opts.conf 已配置
- [ ] 启动应用无报错
- [ ] 缓存预热日志正常
- [ ] JMeter压测QPS > 3000
- [ ] 缓存命中率 > 90%
- [ ] GC日志无频繁Full GC

---

**完成以上步骤后，你的项目性能将得到质的飞跃！** 🚀
