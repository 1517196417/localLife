package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.hmdp.service.IVoucherService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher")
public class VoucherController {

    @Resource
    private IVoucherService voucherService;

    /**
     * 新增普通券
     * @param voucher 优惠券信息
     * @return 优惠券id
     */
    @PostMapping
    public Result addVoucher(@RequestBody Voucher voucher) {
        voucherService.save(voucher);
        return Result.ok(voucher.getId());
    }

    /**
     * 新增秒杀券
     * @param voucher 优惠券信息，包含秒杀信息
     * @return 优惠券id
     */
    @PostMapping("seckill")
    public Result addSeckillVoucher(@RequestBody Voucher voucher) {
        voucherService.addSeckillVoucher(voucher);
        return Result.ok(voucher.getId());
    }

    /**
     * 查询店铺的优惠券列表
     * @param shopId 店铺id
     * @return 优惠券列表
     */
    @GetMapping("/list/{shopId}")
    public Result queryVoucherOfShop(@PathVariable("shopId") Long shopId) {
       return voucherService.queryVoucherOfShop(shopId);
    }
    
    /**
     * 查询我的优惠券列表
     * @param status 优惠券状态（可选）：1-未使用，2-已使用，3-已过期
     * @return 优惠券列表
     */
    @GetMapping("/my")
    public Result queryMyVouchers(
            @RequestParam(value = "status", required = false) Integer status) {
        return voucherService.queryMyVouchers(status);
    }
    
    /**
     * 查询我已使用的优惠券记录
     * @return 已使用的优惠券列表
     */
    @GetMapping("/my/used")
    public Result queryMyUsedVouchers() {
        return voucherService.queryMyUsedVouchers();
    }
    
    /**
     * 同步秒杀券库存到Redis（管理接口）
     * TODO: 需要添加管理员权限控制
     * @param voucherId 优惠券ID
     * @return 同步结果
     */
    @PostMapping("/seckill/sync-stock/{voucherId}")
    public Result syncSeckillStock(@PathVariable("voucherId") Long voucherId) {
        return voucherService.syncSeckillStock(voucherId);
    }
}
