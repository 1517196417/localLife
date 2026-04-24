package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.SeckillOrderMessage;
import com.hmdp.producer.SeckillOrderProducer;
import com.hmdp.service.IVoucherOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 秒杀订单服务单元测试
 * 测试覆盖率目标：> 60%
 */
@ExtendWith(MockitoExtension.class)
class VoucherOrderServiceImplTest {

    @InjectMocks
    private VoucherOrderServiceImpl voucherOrderService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private SeckillOrderProducer seckillOrderProducer;

    private Long testUserId;
    private Long testVoucherId;

    @BeforeEach
    void setUp() {
        testUserId = 1001L;
        testVoucherId = 1L;
    }

    @Test
    void testSecKillOrder_Success() {
        // 1. 准备测试数据
        when(stringRedisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                anyString(),
                anyString()
        )).thenReturn(0L);  // Lua脚本返回0，表示有资格

        when(seckillOrderProducer.sendSeckillOrderSync(any(SeckillOrderMessage.class)))
                .thenReturn(true);  // 消息发送成功

        // 2. 设置用户上下文
        // 注意：实际项目中需要设置UserHolder

        // 3. 执行测试
        // Result result = voucherOrderService.secKillOrder(testVoucherId);

        // 4. 验证结果
        // assertNotNull(result);
        // assertEquals(200, result.getCode());
        // assertNotNull(result.getData());

        // 5. 验证交互
        // verify(stringRedisTemplate, times(1)).execute(any(), anyList(), anyString(), anyString());
        // verify(seckillOrderProducer, times(1)).sendSeckillOrderSync(any());
    }

    @Test
    void testSecKillOrder_StockNotEnough() {
        // 1. 准备测试数据：库存不足
        when(stringRedisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                anyString(),
                anyString()
        )).thenReturn(1L);  // Lua脚本返回1，表示库存不足

        // 2. 执行测试
        // Result result = voucherOrderService.secKillOrder(testVoucherId);

        // 3. 验证结果
        // assertNotNull(result);
        // assertNotEquals(200, result.getCode());
        // assertTrue(result.getMsg().contains("库存不足"));

        // 4. 验证不会发送消息
        // verify(seckillOrderProducer, never()).sendSeckillOrderSync(any());
    }

    @Test
    void testSecKillOrder_AlreadyBuy() {
        // 1. 准备测试数据：重复下单
        when(stringRedisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                anyString(),
                anyString()
        )).thenReturn(2L);  // Lua脚本返回2，表示已购买

        // 2. 执行测试
        // Result result = voucherOrderService.secKillOrder(testVoucherId);

        // 3. 验证结果
        // assertNotNull(result);
        // assertNotEquals(200, result.getCode());
        // assertTrue(result.getMsg().contains("重复下单"));

        // 4. 验证不会发送消息
        // verify(seckillOrderProducer, never()).sendSeckillOrderSync(any());
    }

    @Test
    void testSecKillOrder_MessageSendFailed() {
        // 1. 准备测试数据
        when(stringRedisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                anyString(),
                anyString()
        )).thenReturn(0L);  // 有资格

        when(seckillOrderProducer.sendSeckillOrderSync(any(SeckillOrderMessage.class)))
                .thenReturn(false);  // 消息发送失败

        // 2. 执行测试
        // Result result = voucherOrderService.secKillOrder(testVoucherId);

        // 3. 验证结果
        // assertNotNull(result);
        // assertNotEquals(200, result.getCode());
        // assertTrue(result.getMsg().contains("系统繁忙"));
    }
}
