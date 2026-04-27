package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.SeckillOrderMessage;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.producer.SeckillOrderProducer;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private RedisIdWorker redisIdWorker;
    
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    
    @Autowired
    private SeckillOrderProducer seckillOrderProducer;
    
    @Autowired
    private IVoucherService voucherService;
    
    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setResultType(Long.class);
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
    }

    /**
     * 基于RabbitMQ的异步秒杀下单版本（推荐）
     */
    @Override
    public Result secKillOrder(Long voucherId) {
        // 1：执行lua脚本，判断下单资格
        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString());
        int retVal = result.intValue();
        
        if (retVal != 0) {
            return Result.fail(retVal == 1 ? "库存不足": "重复下单，失败！");
        }

        // 2：生成订单ID（提前生成，用于消息传递）
        long orderId = redisIdWorker.nextId("order");
        
        // 3：构建秒杀订单消息
        SeckillOrderMessage message = new SeckillOrderMessage(orderId, userId, voucherId);
        
        // 4：发送消息到RabbitMQ
        boolean sendSuccess = seckillOrderProducer.sendSeckillOrderSync(message);
        
        if (!sendSuccess) {
            log.error("秒杀订单消息发送失败，orderId: {}, userId: {}", orderId, userId);
            return Result.fail("系统繁忙，请稍后重试");
        }
        
        log.info("秒杀订单请求已提交，orderId: {}, userId: {}, voucherId: {}", orderId, userId, voucherId);
        return Result.ok(orderId);
    }
    
    @Override
    @Transactional
    public Result orderVoucher(Long voucherId) {
        // 1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        
        // 2. 查询优惠券信息
        Voucher voucher = voucherService.getById(voucherId);
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }
        
        // 3. 判断是否是秒杀券(秒杀券必须走秒杀接口)
        // 通过查询是否有秒杀信息来判断
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        if (seckillVoucher != null) {
            return Result.fail("秒杀券请使用秒杀接口购买");
        }
        
        // 4. 普通优惠券不限购，可以购买多张，直接创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setStatus(1); // 1-未支付
        
        boolean success = save(voucherOrder);
        if (!success) {
            log.error("创建订单失败，userId: {}, voucherId: {}", userId, voucherId);
            return Result.fail("创建订单失败");
        }
        
        log.info("普通优惠券订单创建成功，orderId: {}, userId: {}, voucherId: {}", orderId, userId, voucherId);
        return Result.ok(orderId);
    }
    
    @Override
    public Result queryMyOrders(Integer current, Integer status) {
        // 1. 获取当前登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录");
        }
        
        // 2. 构建查询条件
        LambdaQueryWrapper<VoucherOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(VoucherOrder::getUserId, user.getId());
        
        // 3. 如果指定了状态，按状态筛选
        if (status != null) {
            queryWrapper.eq(VoucherOrder::getStatus, status);
        }
        
        // 4. 按创建时间降序排列
        queryWrapper.orderByDesc(VoucherOrder::getCreateTime);
        
        // 5. 分页查询
        Page<VoucherOrder> page = new Page<>(current, 10);
        page(page, queryWrapper);
        
        return Result.ok(page);
    }
    
    @Override
    public Result queryOrderById(Long id) {
        // 1. 获取当前登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录");
        }
        
        // 2. 查询订单详情
        VoucherOrder order = getById(id);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        
        // 3. 验证订单归属（只能查看自己的订单）
        if (!order.getUserId().equals(user.getId())) {
            return Result.fail("无权查看该订单");
        }
        
        return Result.ok(order);
    }
    
    @Override
    public Result cancelOrder(Long id) {
        // 1. 获取当前登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录");
        }
        
        // 2. 查询订单
        VoucherOrder order = getById(id);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        
        // 3. 验证订单归属
        if (!order.getUserId().equals(user.getId())) {
            return Result.fail("无权操作该订单");
        }
        
        // 4. 只有未支付和已支付的订单可以取消
        if (order.getStatus() != 1 && order.getStatus() != 2) {
            return Result.fail("该订单状态不允许取消");
        }
        
        // 5. 更新订单状态为已取消（4）
        order.setStatus(4);
        updateById(order);
        
        return Result.ok();
    }
    
    @Override
    public Result deleteOrder(Long id) {
        // 1. 获取当前登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录");
        }
        
        // 2. 查询订单
        VoucherOrder order = getById(id);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        
        // 3. 验证订单归属
        if (!order.getUserId().equals(user.getId())) {
            return Result.fail("无权操作该订单");
        }
        
        // 4. 只有已取消、已完成、已退款的订单可以删除
        if (order.getStatus() != 4 && order.getStatus() != 3 && order.getStatus() != 6) {
            return Result.fail("该订单状态不允许删除");
        }
        
        // 5. 删除订单
        removeById(id);
        
        return Result.ok();
    }

//同步线性下单秒杀
//    @Override
//    public Result secKillOrder(Long voucherId) {
//        //1：查询优惠券信息
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        System.out.println("从数据库查询出优惠券voucher:" + voucher);
//        //2：判断秒杀是否开始， 未开始返回报错
//        if(voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            System.out.println("秒杀活动尚未开始!");
//            return Result.fail("秒杀活动尚未开始!");
//        }
//        if(voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            System.out.println("秒杀活动已结束!");
//            return Result.fail("秒杀活动已结束!");
//        }
//        //3：判断优惠券库存是否充足， 不充足返回报错
//            if(voucher.getStock() < 1) {
//                System.out.println("优惠券数量不足!");
//                return Result.fail("优惠券数量不足!");
//            }
//
//
//        //获取锁对象
////        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
////        boolean success = lock.tryLock(1200L, stringRedisTemplate);
//
////        try {
////            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
////            return proxy.createVoucherOrder(userId,voucherId);
////        } finally {
////            lock.unlock();
////        }
//
//        return createVoucherOrder(voucherId);
//    }


//    @Transactional
//    public Result createVoucherOrder( Long voucherId) {
//        Long userId = UserHolder.getUser().getId();
//
//        //一人一单判断
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        boolean success = lock.tryLock();
//        System.out.println("lock.tryLock()返回值：" + success);
//        if(!success) {
//            return Result.fail("获取分布式锁失败, 不允许重复下单");
//        }
//
//        try {
//            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
//            if(count > 0) {
//                return Result.fail("用户已经购买一次了");
//            }
//
//            //4：扣减库存
//            boolean orderSuccess = seckillVoucherService.update()
//                    .setSql("stock = stock - 1")
//                    .eq("voucher_id", voucherId)
//                    .gt("stock", 0)
//                    .update();
//            if(!orderSuccess) {
//                return Result.fail("删减库存失败");
//            }
//            //5：创建订单,存入用户userId，优惠券voucherId，订单orderId
//            VoucherOrder voucherOrder = new VoucherOrder();
//            voucherOrder.setVoucherId(voucherId);
//            long orderId = redisIdWorker.nextId("order");
//            System.out.println("orderId = " + orderId);
//            voucherOrder.setId(orderId);
//            voucherOrder.setUserId(userId);
//
//            save(voucherOrder);
//            //返回订单id
//            return  Result.ok(orderId);
//        }finally {
//            lock.unlock();
//        }


//    }
}
