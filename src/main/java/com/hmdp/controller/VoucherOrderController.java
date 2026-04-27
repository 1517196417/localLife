package com.hmdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 *  前端控制器
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    @Autowired
    private IVoucherOrderService voucherOrderService;
    
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.secKillOrder(voucherId);
    }
    
    /**
     * 普通优惠券下单
     * @param voucherId 优惠券ID
     * @return 订单ID
     */
    @PostMapping("/order/{id}")
    public Result orderVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.orderVoucher(voucherId);
    }
    
    
    /**
     * 查询我的订单列表（分页）
     * @param current 页码
     * @param status 订单状态（可选）
     * @return 订单列表
     */
    @GetMapping("/my")
    public Result queryMyOrders(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "status", required = false) Integer status) {
        return voucherOrderService.queryMyOrders(current, status);
    }
    
    /**
     * 查询订单详情
     * @param id 订单ID
     * @return 订单详情
     */
    @GetMapping("/{id}")
    public Result queryOrderById(@PathVariable("id") Long id) {
        return voucherOrderService.queryOrderById(id);
    }
    
    /**
     * 取消订单
     * @param id 订单ID
     * @return 操作结果
     */
    @PutMapping("/{id}/cancel")
    public Result cancelOrder(@PathVariable("id") Long id) {
        return voucherOrderService.cancelOrder(id);
    }
    
    /**
     * 删除订单
     * @param id 订单ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public Result deleteOrder(@PathVariable("id") Long id) {
        return voucherOrderService.deleteOrder(id);
    }
}
