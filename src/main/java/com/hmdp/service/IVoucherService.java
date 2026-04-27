package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherService extends IService<Voucher> {

    Result queryVoucherOfShop(Long shopId);

    void addSeckillVoucher(Voucher voucher);
    
    /**
     * 查询我的优惠券列表
     * @param status 优惠券状态（可选）：1-未使用，2-已使用，3-已过期
     * @return 优惠券列表
     */
    Result queryMyVouchers(Integer status);
    
    /**
     * 查询我已使用的优惠券记录
     * @return 已使用的优惠券列表
     */
    Result queryMyUsedVouchers();
    
    /**
     * 同步秒杀券库存到Redis
     * @param voucherId 优惠券ID
     * @return 同步结果
     */
    Result syncSeckillStock(Long voucherId);
}
