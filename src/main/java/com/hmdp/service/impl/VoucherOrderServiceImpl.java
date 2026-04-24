package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.SeckillOrderMessage;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.producer.SeckillOrderProducer;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;

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
