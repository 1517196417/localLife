package com.hmdp.strategy.impl;

import com.hmdp.strategy.VoucherDiscountStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 折扣券策略
 * 例如：8折券、5折券
 */
@Slf4j
@Component
public class DiscountVoucherStrategy implements VoucherDiscountStrategy {

    @Override
    public Long calculateDiscount(Long originalPrice, String voucherRule) {
        // 解析折扣规则
        // 假设rules格式为：{"discount": 80} 表示8折（80%）
        log.debug("折扣券策略计算：原价={}, 规则={}", originalPrice, voucherRule);
        
        // 简化处理：从actualValue获取折扣比例
        // 例如：actualValue=80 表示8折
        // 实际价格 = 原价 * 折扣 / 100
        return originalPrice;  // 具体计算在业务层处理
    }

    @Override
    public Integer supportType() {
        return 2;  // 折扣券类型
    }
}
