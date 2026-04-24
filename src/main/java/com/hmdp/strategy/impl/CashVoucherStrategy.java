package com.hmdp.strategy.impl;

import com.hmdp.strategy.VoucherDiscountStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 代金券策略（满减券）
 * 例如：满100减20
 */
@Slf4j
@Component
public class CashVoucherStrategy implements VoucherDiscountStrategy {

    @Override
    public Long calculateDiscount(Long originalPrice, String voucherRule) {
        // 解析规则：满X减Y
        // 这里简化处理，实际应该从rules字段解析
        // 假设 actualValue 就是抵扣金额
        log.debug("代金券策略计算：原价={}", originalPrice);
        
        // 代金券直接抵扣固定金额
        // 具体抵扣金额在Voucher.actualValue中
        return originalPrice;  // 返回原价，实际抵扣在业务层处理
    }

    @Override
    public Integer supportType() {
        return 1;  // 代金券类型
    }
}
