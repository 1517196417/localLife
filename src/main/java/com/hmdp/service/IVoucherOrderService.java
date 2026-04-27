package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 秒杀下单（基于RabbitMQ异步处理）
     * @param voucherId 优惠券ID
     * @return 订单ID
     */
    Result secKillOrder(Long voucherId);
    
    /**
     * 普通优惠券下单
     * @param voucherId 优惠券ID
     * @return 订单ID
     */
    Result orderVoucher(Long voucherId);
    
    /**
     * 查询我的订单列表（分页）
     * @param current 页码
     * @param status 订单状态（可选）
     * @return 订单列表
     */
    Result queryMyOrders(Integer current, Integer status);
    
    /**
     * 查询订单详情
     * @param id 订单ID
     * @return 订单详情
     */
    Result queryOrderById(Long id);
    
    /**
     * 取消订单
     * @param id 订单ID
     * @return 操作结果
     */
    Result cancelOrder(Long id);
    
    /**
     * 删除订单
     * @param id 订单ID
     * @return 操作结果
     */
    Result deleteOrder(Long id);
}
