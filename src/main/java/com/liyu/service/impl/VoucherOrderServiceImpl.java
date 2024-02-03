package com.liyu.service.impl;

import com.liyu.dto.Result;
import com.liyu.entity.SeckillVoucher;
import com.liyu.entity.VoucherOrder;
import com.liyu.mapper.VoucherOrderMapper;
import com.liyu.service.ISeckillVoucherService;
import com.liyu.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liyu.utils.RedisIdWorker;
import com.liyu.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.liyu.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        //1、查询优惠卷
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2、判断秒杀是否开始
        LocalDateTime beginTime = voucher.getBeginTime();
        if(beginTime.isAfter(LocalDateTime.now())){
            return Result.fail("秒杀尚未开始");
        }
        //3、判断秒杀是否结束
        LocalDateTime endTime = voucher.getEndTime();
        if(LocalDateTime.now().isAfter(endTime)){
            return Result.fail("秒杀已经结束");
        }
        //4、判断库存是否充足
        Integer stock = voucher.getStock();
        if(stock < 1){
            return Result.fail("库存不足");
        }
        //利用悲观锁实现一人一单的功能
        //注意锁的粒度和动态代理的实现
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()){
            Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            if(count > 0){
                return Result.fail("用户已经购买过");
            }
            //5、扣减库存
            boolean updateSuccess = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    .gt("stock",0)        //实现乐观锁并且只要判断大于0即可
                    .update();
            if(!updateSuccess){
                return Result.fail("更新失败");
            }
            //6、创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            long orderId = redisIdWorker.nextId("order:");
            voucherOrder.setId(orderId);
            voucherOrder.setUserId(userId);
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);
            //7、返回订单id
            return Result.ok(orderId);
        }
    }
}
