package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 根据id查询店铺信息
     */
    @Override
    public Result queryById(Long id) {
        // 缓存穿透
//        Shop shop = queryWithPassThrough(id);
        // 互斥锁解决缓存击穿
//        Shop shop = queryWithMutex(id);
        // 逻辑过期解决缓存击穿
        Shop shop = queryWithLogicalExpire(id);
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public Shop queryWithLogicalExpire(Long id) {
        // 1.根据店铺id查询redis是否命中
        String key = CACHE_SHOP_KEY + id;
        String shopStr = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(shopStr)) {
            // 2.没命中，直接返回null
            return null;
        }
        // 3.命中，判断缓存是否过期
        RedisData redisData = JSONUtil.toBean(shopStr, RedisData.class);
        Shop shop =JSONUtil.toBean((JSONObject) redisData.getData(),Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 4.判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 没有过期 直接返回结果
            return shop;
        }
        // 5.过期了，尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if (isLock) {
            // 5.1获取到了，创建新线程
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    // 5.2查询数据库，写入redis
                    this.saveShop2Redis(id,20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 5.3释放锁
                    unLock(lockKey);
                }
            });
        }
        // 6.返回过期数据
        return shop;
    }

    public Shop queryWithMutex(Long id) {
        Shop shop;
        String lockKey = LOCK_SHOP_KEY + id;
        try {
            // 1.根据店铺id查询redis
            String key = CACHE_SHOP_KEY + id;
            int i = 0;
            for (; i <= 100; i++) {
                if (i == 100) {
                    throw new RuntimeException("连接超时");
                }
                String shopStr = stringRedisTemplate.opsForValue().get(key);
                if (StrUtil.isNotBlank(shopStr)) {
                    // 2.存在，直接返回
                    shop = JSONUtil.toBean(shopStr, Shop.class);
                    return shop;
                }
                // 3.判断命中的是否为空值
                if ("".equals(shopStr)) {
                    // 4.为空对象，返回错误
                    return null;
                }
                // 5.尝试获取互斥锁
                boolean isLock = tryLock(lockKey);
                if (!isLock) {
                    // 没有获取到，休眠，重试
                    Thread.sleep(50);
                    continue;
                }
                break;
            }
            // 获取到了锁，查询数据库
            shop = getById(id);

            // 模拟重建缓存的延迟
            Thread.sleep(200);
            // 6.判断数据库是否命中
            if (shop == null) {
                // 没命中，返回错误信息
                // 往redis插入一个空对象（应对缓存穿透）
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 命中，写入redis
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 7.释放互斥锁
            unLock(lockKey);
        }
        // 8.返回结果
        return shop;
    }

    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        // 1.查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(2000);
        // 2.封装逻辑过期时间
        RedisData shopRedisData = new RedisData();
        shopRedisData.setData(shop);
        shopRedisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 3.写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shopRedisData));
    }


    public Shop queryWithPassThrough(Long id) {
        // 1.根据店铺id查询redis
        String key = CACHE_SHOP_KEY + id;
        String shopStr = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopStr)) {
            // 2.存在，直接返回
            Shop shop = JSONUtil.toBean(shopStr, Shop.class);
            return shop;
        }
        // 判断命中的是否为空值
        if ("".equals(shopStr)) {
            // 为空对象，返回错误
            return null;
        }
        // 3.不存在，查询数据库
        Shop shop = getById(id);
        if (shop == null) {
            // 4.不存在，返回错误信息
            // 往redis插入一个空对象（应对缓存穿透）
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 5.存在，写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    private boolean tryLock(String key) {
        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(lock);
    }

    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }


    /**
     * 更新店铺信息
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空！");
        }
        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
