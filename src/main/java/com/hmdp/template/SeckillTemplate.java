package com.hmdp.template;

import com.hmdp.dto.Result;
import lombok.extern.slf4j.Slf4j;

/**
 * 秒杀流程模板方法
 * 定义秒杀的标准流程，子类可以自定义具体步骤
 */
@Slf4j
public abstract class SeckillTemplate {

    /**
     * 秒杀流程模板方法（final防止子类重写）
     * 
     * @param voucherId 优惠券ID
     * @param userId 用户ID
     * @return 订单ID
     */
    public final Result executeSeckill(Long voucherId, Long userId) {
        try {
            // 1. 参数校验
            validateParams(voucherId, userId);

            // 2. 资格校验（Lua脚本）
            boolean hasQualification = checkQualification(voucherId, userId);
            if (!hasQualification) {
                return buildFailResult("没有抢购资格");
            }

            // 3. 生成订单ID
            Long orderId = generateOrderId();

            // 4. 发送消息（同步或异步）
            sendOrderMessage(orderId, userId, voucherId);

            // 5. 返回结果
            return buildSuccessResult(orderId);

        } catch (Exception e) {
            log.error("秒杀失败，voucherId: {}, userId: {}", voucherId, userId, e);
            return buildFailResult("系统繁忙，请稍后重试");
        }
    }

    /**
     * 参数校验（子类可重写）
     */
    protected void validateParams(Long voucherId, Long userId) {
        if (voucherId == null || userId == null) {
            throw new IllegalArgumentException("参数不能为空");
        }
    }

    /**
     * 资格校验（子类必须实现）
     * 使用Lua脚本检查库存和一人一单
     */
    protected abstract boolean checkQualification(Long voucherId, Long userId);

    /**
     * 生成订单ID（子类可重写）
     */
    protected Long generateOrderId() {
        return System.currentTimeMillis();
    }

    /**
     * 发送订单消息（子类必须实现）
     * 可以是同步调用或异步MQ消息
     */
    protected abstract void sendOrderMessage(Long orderId, Long userId, Long voucherId);

    /**
     * 构建成功结果
     */
    protected Result buildSuccessResult(Long orderId) {
        return Result.ok(orderId);
    }

    /**
     * 构建失败结果
     */
    protected Result buildFailResult(String message) {
        return Result.fail(message);
    }
}
