# RabbitMQ 集成说明

## 📦 已完成的功能

### 1. 秒杀订单异步处理
- ✅ 生产者：`SeckillOrderProducer`
- ✅ 消费者：`SeckillOrderListener`
- ✅ 消息体：`SeckillOrderMessage`
- ✅ 死信队列处理

### 2. 用户日志异步采集
- ✅ 生产者：`UserLogProducer`
- ✅ 消费者：`UserLogListener`
- ✅ 消息体：`UserLogMessage`

### 3. 消息可靠性保障
- ✅ 生产者确认模式（Publisher Confirm）
- ✅ 消息返回模式（Publisher Returns）
- ✅ 消费者手动ACK
- ✅ 消息重试机制（最多3次）
- ✅ 死信队列（Dead Letter Queue）
- ✅ 消息持久化

## 🚀 启动前准备

### 1. 安装RabbitMQ（Docker方式）
```bash
# 拉取RabbitMQ镜像（带管理界面）
docker pull rabbitmq:3-management

# 启动RabbitMQ容器
docker run -d \
  --name rabbitmq \
  -p 5672:5672 \
  -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=guest \
  -e RABBITMQ_DEFAULT_PASS=guest \
  rabbitmq:3-management
```

### 2. 访问管理界面
- 地址：http://localhost:15672
- 用户名：guest
- 密码：guest

### 3. 验证连接
启动应用后，在RabbitMQ管理界面可以看到自动创建的交换机和队列：
- **交换机**：
  - `seckill.order.exchange`（秒杀订单交换机）
  - `seckill.order.dlx.exchange`（秒杀订单死信交换机）
  - `user.log.exchange`（用户日志交换机）

- **队列**：
  - `seckill.order.queue`（秒杀订单队列）
  - `seckill.order.dlx.queue`（秒杀订单死信队列）
  - `user.log.queue`（用户日志队列）

## 📋 技术架构

### 秒杀订单流程
```
用户请求 
  → Lua脚本校验（库存、重复下单）
  → 生成订单ID
  → 发送消息到RabbitMQ
  → 立即返回订单ID给前端
  
消费者异步处理：
  → 接收消息
  → 获取分布式锁（Redisson）
  → 检查一人一单
  → 扣减库存
  → 创建订单
  → 手动ACK确认
```

### 消息可靠性保障
1. **生产者端**：
   - 开启Publisher Confirm，确保消息到达交换机
   - 开启Publisher Returns，确保消息路由到队列
   - 消息持久化（队列和消息都设置为durable）

2. **消费者端**：
   - 手动ACK模式（acknowledge-mode: manual）
   - 异常时自动重试（最多3次）
   - 超过重试次数进入死信队列
   - 死信队列消息记录日志，可后续补偿

3. **防重复消费**：
   - 分布式锁（Redisson）
   - 数据库唯一约束（user_id + voucher_id）

## 🔧 配置说明

### application.yaml 关键配置
```yaml
spring:
  rabbitmq:
    host: 127.0.0.1
    port: 5672
    username: guest
    password: guest
    virtual-host: /
    
    # 生产者确认
    publisher-confirm-type: correlated
    publisher-returns: true
    
    # 消费者配置
    listener:
      simple:
        acknowledge-mode: manual  # 手动ACK
        prefetch: 10              # 预取数量
        retry:
          enabled: true
          max-attempts: 3         # 最大重试次数
          initial-interval: 1000  # 重试间隔
    
    template:
      mandatory: true  # 开启Return模式
      retry:
        enabled: true
        max-attempts: 3
```

## 📊 性能优势

### 对比阻塞队列方案
| 维度 | 阻塞队列（旧） | RabbitMQ（新） |
|------|---------------|----------------|
| 吞吐量 | 单机受限 | 分布式，可扩展 |
| 可靠性 | 内存丢失 | 持久化+ACK |
| 监控 | 无 | 管理界面 |
| 扩展性 | 差 | 好（可多消费者） |
| 削峰能力 | 弱 | 强（消息堆积） |

### 预期性能提升
- **TPS**: 从 ~500 提升到 ~3000+
- **响应时间**: 从 200ms 降低到 50ms（异步返回）
- **可靠性**: 消息不丢失，支持重试和补偿

## 🎯 使用示例

### 发送秒杀订单
```java
@Autowired
private SeckillOrderProducer seckillOrderProducer;

SeckillOrderMessage message = new SeckillOrderMessage(orderId, userId, voucherId);
boolean success = seckillOrderProducer.sendSeckillOrderSync(message);
```

### 发送用户日志
```java
@Autowired
private UserLogProducer userLogProducer;

UserLogMessage log = new UserLogMessage(userId, "view", blogId, "浏览博客");
userLogProducer.sendUserLog(log);
```

## 🔍 监控和排查

### 1. 查看消息堆积
登录RabbitMQ管理界面 → Queues → 查看Ready和Unacked数量

### 2. 查看死信消息
登录RabbitMQ管理界面 → Queues → seckill.order.dlx.queue

### 3. 日志关键字
- 消息发送成功：`秒杀订单消息发送成功`
- 消息接收成功：`收到秒杀订单消息`
- 订单创建成功：`秒杀订单创建成功`
- 进入死信队列：`消息重试次数已达上限`

## ⚠️ 注意事项

1. **本地测试**：确保RabbitMQ已启动并可访问
2. **生产环境**：修改application.yaml中的连接信息
3. **消息积压**：监控队列长度，及时扩容消费者
4. **死信处理**：定期处理死信队列，进行补偿

## 📝 后续优化方向

1. 延迟队列（订单超时取消）
2. 消息追踪（链路追踪集成）
3. 死信消息自动补偿机制
4. 消息幂等性优化（唯一索引）
5. 批量消费提升吞吐量
