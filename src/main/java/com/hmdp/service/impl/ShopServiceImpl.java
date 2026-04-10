package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

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
    @Resource
    private CacheClient CacheClient;

    /**
     * 根据id查询店铺信息
     */
    @Override
    public Result queryById(Long id) {
        // 解决缓存穿透
//        Shop shop = cacheClient
//                .queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.SECONDS);

        // 互斥锁解决缓存击穿
        // Shop shop = queryWithMutex(id);

        // 逻辑过期解决缓存击穿
        Shop shop = CacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 24L, TimeUnit.HOURS);
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }

//    public Shop queryWithMutex(Long id) {
//        Shop shop;
//        String lockKey = LOCK_SHOP_KEY + id;
//        try {
//            // 1.根据店铺id查询redis
//            String key = CACHE_SHOP_KEY + id;
//            int i = 0;
//            for (; i <= 100; i++) {
//                if (i == 100) {
//                    throw new RuntimeException("连接超时");
//                }
//                String shopStr = stringRedisTemplate.opsForValue().get(key);
//                if (StrUtil.isNotBlank(shopStr)) {
//                    // 2.存在，直接返回
//                    shop = JSONUtil.toBean(shopStr, Shop.class);
//                    return shop;
//                }
//                // 3.判断命中的是否为空值
//                if ("".equals(shopStr)) {
//                    // 4.为空对象，返回错误
//                    return null;
//                }
//                // 5.尝试获取互斥锁
//                boolean isLock = tryLock(lockKey);
//                if (!isLock) {
//                    // 没有获取到，休眠，重试
//                    Thread.sleep(50);
//                    continue;
//                }
//                break;
//            }
//            // 获取到了锁，查询数据库
//            shop = getById(id);
//
//            // 模拟重建缓存的延迟
//            Thread.sleep(200);
//            // 6.判断数据库是否命中
//            if (shop == null) {
//                // 没命中，返回错误信息
//                // 往redis插入一个空对象（应对缓存穿透）
//                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//            // 命中，写入redis
//            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            // 7.释放互斥锁
//            unLock(lockKey);
//        }
//        // 8.返回结果
//        return shop;
//    }

//    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
//        // 1.查询店铺数据
//        Shop shop = getById(id);
//        Thread.sleep(2000);
//        // 2.封装逻辑过期时间
//        RedisData shopRedisData = new RedisData();
//        shopRedisData.setData(shop);
//        shopRedisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//        // 3.写入redis
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shopRedisData));
//    }

//    public Shop queryWithPassThrough(Long id) {
//        // 1.根据店铺id查询redis
//        String key = CACHE_SHOP_KEY + id;
//        String shopStr = stringRedisTemplate.opsForValue().get(key);
//        if (StrUtil.isNotBlank(shopStr)) {
//            // 2.存在，直接返回
//            Shop shop = JSONUtil.toBean(shopStr, Shop.class);
//            return shop;
//        }
//        // 判断命中的是否为空值
//        if ("".equals(shopStr)) {
//            // 为空对象，返回错误
//            return null;
//        }
//        // 3.不存在，查询数据库
//        Shop shop = getById(id);
//        if (shop == null) {
//            // 4.不存在，返回错误信息
//            // 往redis插入一个空对象（应对缓存穿透）
//            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return null;
//        }
//        // 5.存在，写入redis
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        return shop;
//    }

//    private boolean tryLock(String key) {
//        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(lock);
//    }

//    private void unLock(String key) {
//        stringRedisTemplate.delete(key);
//    }

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

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1.判断是否需要根据坐标查询
        if (x == null || y == null) {
            // 不需要坐标查询，按数据库查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        // 2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        // 3.查询redis,按照距离排序、分页，结果：shopId、distance
        String key=SHOP_GEO_KEY+typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo() // GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
                .search(key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        // 4.解析出id
        if (results==null){
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size()<=from){
            // 没有下一页了，结束
            return Result.ok(Collections.emptyList());
        }
        // 4.1截取from~end的部分
        List<Long> ids=new ArrayList<>(list.size());
        Map<String,Distance> distanceMap=new HashMap<>(list.size());
        list.stream().skip(from).forEach(new Consumer<GeoResult<RedisGeoCommands.GeoLocation<String>>>() {
            @Override
            public void accept(GeoResult<RedisGeoCommands.GeoLocation<String>> r) {
                // 4.2获取店铺id
                String shopIdStr = r.getContent().getName();
                ids.add(Long.valueOf(shopIdStr));
                // 4.3获取距离
                Distance distance = r.getDistance();
                distanceMap.put(shopIdStr,distance);
            }
        });
        // 5.根据id查询店铺Shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD( id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        // 6.返回
        return Result.ok(shops);
    }
}
