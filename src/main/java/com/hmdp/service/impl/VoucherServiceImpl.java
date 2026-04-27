package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IVoucherOrderService voucherOrderService;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // 返回结果
        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券
        save(voucher);

        // 确保获取到数据库生成的ID
        if (voucher.getId() == null) {
            throw new RuntimeException("优惠券保存失败，未获取到ID");
        }
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);

        //往redis中存放优惠券id， 库存
        stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + voucher.getId(),
                voucher.getStock().toString());
    }
    
    @Override
    public Result queryMyVouchers(Integer status) {
        try {
            // 1. 获取当前登录用户
            UserDTO user = UserHolder.getUser();
            if (user == null) {
                return Result.fail("用户未登录");
            }
            
            System.out.println("[DEBUG] 查询用户优惠券，用户ID: " + user.getId() + ", 状态: " + status);
            
            // 2. 查询用户的订单（即购买的优惠券）
            LambdaQueryWrapper<VoucherOrder> orderQuery = new LambdaQueryWrapper<>();
            orderQuery.eq(VoucherOrder::getUserId, user.getId());
            
            // 如果指定了状态，按状态筛选
            if (status != null) {
                orderQuery.eq(VoucherOrder::getStatus, status);
            }
            
            // 按创建时间降序排列
            orderQuery.orderByDesc(VoucherOrder::getCreateTime);
            
            List<VoucherOrder> orders = voucherOrderService.list(orderQuery);
            
            System.out.println("[DEBUG] 查询到订单数量: " + orders.size());
            
            if (orders.isEmpty()) {
                return Result.ok(Collections.emptyList());
            }
            
            // 3. 获取优惠券ID列表（去重）
            List<Long> voucherIds = orders.stream()
                    .map(VoucherOrder::getVoucherId)
                    .distinct()
                    .collect(Collectors.toList());
            
            System.out.println("[DEBUG] 优惠券ID列表: " + voucherIds);
            
            // 4. 批量查询优惠券详情
            List<Voucher> vouchers = listByIds(voucherIds);
            System.out.println("[DEBUG] 查询到优惠券数量: " + vouchers.size());
            
            Map<Long, Voucher> voucherMap = vouchers.stream()
                    .collect(Collectors.toMap(Voucher::getId, v -> v));
            
            // 5. 按优惠券ID和状态分组，统计数量
            Map<String, List<VoucherOrder>> groupMap = orders.stream()
                    .collect(Collectors.groupingBy(order -> order.getVoucherId() + "_" + order.getStatus()));
            
            System.out.println("[DEBUG] 分组数量: " + groupMap.size());
            
            // 6. 组装数据：同一种优惠券如果状态相同合并成一项，并显示数量
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map.Entry<String, List<VoucherOrder>> entry : groupMap.entrySet()) {
                List<VoucherOrder> orderList = entry.getValue();
                VoucherOrder firstOrder = orderList.get(0);
                Voucher voucher = voucherMap.get(firstOrder.getVoucherId());
                
                if (voucher == null) {
                    System.out.println("[WARN] 优惠券不存在，voucherId: " + firstOrder.getVoucherId());
                    continue;
                }
                
                Map<String, Object> item = new HashMap<>();
                
                // 优惠券信息
                item.put("id", voucher.getId());
                item.put("title", voucher.getTitle());
                item.put("subTitle", voucher.getSubTitle());
                item.put("rules", voucher.getRules());
                item.put("payValue", voucher.getPayValue());
                item.put("actualValue", voucher.getActualValue());
                item.put("type", voucher.getType());
                
                // 订单信息（取最新的一张订单）
                item.put("status", firstOrder.getStatus());
                item.put("createTime", firstOrder.getCreateTime());
                item.put("useTime", firstOrder.getUseTime());
                
                // 数量统计
                item.put("count", orderList.size());
                
                result.add(item);
            }
            
            System.out.println("[DEBUG] 返回的优惠券数据数量: " + result.size());
            
            // 按创建时间降序排序（使用LocalDateTime比较）
            result.sort((a, b) -> {
                Object timeA = a.get("createTime");
                Object timeB = b.get("createTime");
                if (timeA == null && timeB == null) return 0;
                if (timeA == null) return 1;
                if (timeB == null) return -1;
                
                // 使用toString进行比较（LocalDateTime实现了Comparable）
                return timeB.toString().compareTo(timeA.toString());
            });
            
            return Result.ok(result);
        } catch (Exception e) {
            System.err.println("[ERROR] 查询我的优惠券异常: " + e.getMessage());
            e.printStackTrace();
            throw e;  // 抛出异常，让全局异常处理器处理
        }
    }
    
    @Override
    public Result queryMyUsedVouchers() {
        // 1. 获取当前登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录");
        }
        
        // 2. 查询已使用的订单（状态为3-已核销）
        LambdaQueryWrapper<VoucherOrder> orderQuery = new LambdaQueryWrapper<>();
        orderQuery.eq(VoucherOrder::getUserId, user.getId());
        orderQuery.eq(VoucherOrder::getStatus, 3); // 3-已核销
        orderQuery.orderByDesc(VoucherOrder::getUseTime);
        
        List<VoucherOrder> orders = voucherOrderService.list(orderQuery);
        
        // 3. 获取优惠券ID列表
        List<Long> voucherIds = orders.stream()
                .map(VoucherOrder::getVoucherId)
                .collect(Collectors.toList());
        
        if (voucherIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        
        // 4. 批量查询优惠券详情
        List<Voucher> vouchers = listByIds(voucherIds);
        
        // 5. 组装数据
        Map<Long, VoucherOrder> orderMap = orders.stream()
                .collect(Collectors.toMap(VoucherOrder::getVoucherId, o -> o));
        
        List<Map<String, Object>> result = vouchers.stream().map(voucher -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", voucher.getId());
            item.put("title", voucher.getTitle());
            item.put("subTitle", voucher.getSubTitle());
            item.put("actualValue", voucher.getActualValue());
            
            VoucherOrder order = orderMap.get(voucher.getId());
            item.put("orderId", order.getId());
            item.put("useTime", order.getUseTime());
            
            return item;
        }).collect(Collectors.toList());
        
        return Result.ok(result);
    }
    
    @Override
    public Result syncSeckillStock(Long voucherId) {
        // 1. 查询数据库中的秒杀券信息
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        if (seckillVoucher == null) {
            return Result.fail("秒杀券不存在");
        }
        
        // 2. 将数据库中的库存同步到Redis
        String stockKey = SECKILL_STOCK_KEY + voucherId;
        stringRedisTemplate.opsForValue().set(stockKey, seckillVoucher.getStock().toString());
        
        // 3. 清空已购买用户集合（可选，如果需要重置的话）
        String orderKey = "seckill:order:" + voucherId;
        // stringRedisTemplate.delete(orderKey); // 谨慎使用，会清空购买记录
        
        return Result.ok(seckillVoucher.getStock());
    }
}