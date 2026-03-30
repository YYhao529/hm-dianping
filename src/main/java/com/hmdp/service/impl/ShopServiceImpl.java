package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

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
        // 1.根据店铺id查询redis
        String key = CACHE_SHOP_KEY + id;
        String shopStr = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopStr)){
            // 2.存在，直接返回
            Shop shop = JSONUtil.toBean(shopStr, Shop.class);
            return Result.ok(shop);
        }
        // 3.不存在，查询数据库
        Shop shop = getById(id);
        if (shop==null){
            // 4.不存在，返回错误信息
            return Result.fail("该商铺不存在");
        }
        // 5.存在，写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop));
        // 6.返回结果
        return Result.ok(shop);
    }
}
