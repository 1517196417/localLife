package com.hmdp.strategy;

/**
 * 优惠券折扣策略接口
 * 不同优惠券类型实现不同的折扣计算策略
 */
public interface VoucherDiscountStrategy {

    /**
     * 计算优惠后的价格
     * 
     * @param originalPrice 原价（分）
     * @param voucherRule 优惠券规则
     * @return 优惠后价格（分）
     */
    Long calculateDiscount(Long originalPrice, String voucherRule);

    /**
     * 获取策略支持的优惠券类型
     * 
     * @return 优惠券类型码
     */
    Integer supportType();
}
