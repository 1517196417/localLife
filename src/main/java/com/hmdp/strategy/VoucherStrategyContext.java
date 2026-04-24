package com.hmdp.strategy;

import com.hmdp.exception.BusinessException;
import com.hmdp.exception.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 优惠券策略上下文
 * 根据优惠券类型自动选择对应的折扣策略
 */
@Slf4j
@Component
public class VoucherStrategyContext {

    @Resource
    private List<VoucherDiscountStrategy> strategyList;

    /**
     * 策略Map：优惠券类型 -> 策略实例
     */
    private Map<Integer, VoucherDiscountStrategy> strategyMap;

    @PostConstruct
    public void init() {
        // 将所有策略注册到Map中
        strategyMap = strategyList.stream()
                .collect(Collectors.toMap(
                        VoucherDiscountStrategy::supportType,
                        Function.identity()
                ));
        log.info("优惠券策略初始化完成，共加载 {} 个策略", strategyMap.size());
    }

    /**
     * 根据优惠券类型获取对应策略
     * 
     * @param voucherType 优惠券类型
     * @return 折扣策略
     */
    public VoucherDiscountStrategy getStrategy(Integer voucherType) {
        VoucherDiscountStrategy strategy = strategyMap.get(voucherType);
        if (strategy == null) {
            log.error("不支持的优惠券类型：{}", voucherType);
            throw new BusinessException(ResultCode.VOUCHER_NOT_EXIST);
        }
        return strategy;
    }

    /**
     * 计算优惠价格
     * 
     * @param originalPrice 原价
     * @param voucherType 优惠券类型
     * @param voucherRule 优惠券规则
     * @return 优惠后价格
     */
    public Long calculateDiscount(Long originalPrice, Integer voucherType, String voucherRule) {
        VoucherDiscountStrategy strategy = getStrategy(voucherType);
        return strategy.calculateDiscount(originalPrice, voucherRule);
    }
}
