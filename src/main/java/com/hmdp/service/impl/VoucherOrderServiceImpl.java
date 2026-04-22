package com.hmdp.service.impl;

import ch.qos.logback.classic.Level;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.ILock;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private ResourceLoader resourceLoader;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private IVoucherOrderService voucherOrderService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setResultType(Long.class);
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
    }

    //引入阻塞队列实现异步处理，开启线程任务执行
    private BlockingQueue<VoucherOrder> tasksQueue = new ArrayBlockingQueue<>(1024 * 1024);

    private static final ExecutorService SECKILL_ORDER_EXEXUTOR = Executors.newSingleThreadExecutor();

    //代理对象基于ThreadLocal实现，由于子线程无法获取可以设置为成员变量
    private IVoucherOrderService proxy;

    //使用@PostConstrut注解初始化该线程任务,每时每刻都可能有订单，所以项目一启动就初始化
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXEXUTOR.submit(new VoucherOrderHandler());
    }

    //异步秒杀版本v1
    @Override
    public Result secKillOrder(Long voucherId) {
        //1：执行lua脚本，判断下单资格
        Long userId = UserHolder.getUser().getId();
        System.out.println("秒杀时userId：" + userId);
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString());
        int retVal = result.intValue();
        System.out.println("retVal = " + retVal);
        if (retVal != 0) {
            return Result.fail(retVal == 1 ? "库存不足": "重复下单，失败！");
        }

        //2：将用户，优惠券，订单id传入到阻塞线程BlockingQueue  orderTask
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setVoucherId(voucherId);
        long orderId = redisIdWorker.nextId("order");
        System.out.println("orderId = " + orderId);
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);

        //创建ExecutorService SEKILL_ORDER_EXECUTOR,线程任务VoucherOrderHandler
        tasksQueue.add(voucherOrder);

        //获取proxy代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        return Result.ok(orderId);
    }


    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    VoucherOrder voucherOrder = tasksQueue.take();
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }

            }

        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();

        //一人一单判断
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean success = lock.tryLock();
        System.out.println("lock.tryLock()返回值：" + success);
        if(!success) {
            log.error("获取分布式锁失败, 不允许重复下单");
        }

        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }

    }

    @Transactional
    public void createVoucherOrder( VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();

        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            log.error("用户已购买一次，请勿重复下单");
            return;
        }

        //4：扣减库存
        boolean orderSuccess = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!orderSuccess) {
            log.error("库存不足");
            return;
        }

        save(voucherOrder);
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
