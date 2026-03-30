package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询商铺类型
     */
    @Override
    public Result queryShopType() {
        // 1.查询redis中商铺信息是否存在
        Set<ZSetOperations.TypedTuple<String>> tupleSet = stringRedisTemplate.opsForZSet().rangeWithScores(CACHE_SHOPTYPE_KEY, 0, -1);

        if (tupleSet != null && !tupleSet.isEmpty()) {
            // 2.存在
            // 遍历每个tuple，把里面的value获取出来，封装成ShopType对象
            List<ShopType> shopTypeList = new ArrayList<>();
            for (ZSetOperations.TypedTuple<String> tuple : tupleSet) {
                String jsonStr = tuple.getValue();
                // 把一个个ShopType对象封装进一个List集合里
                shopTypeList.add(JSONUtil.toBean(jsonStr, ShopType.class));
            }
            // 返回结果
            return Result.ok(shopTypeList);
        }
        // 3.不存在，查询数据库
        // 创建查询条件
        QueryWrapper<ShopType> queryWrapper = new QueryWrapper<>();
        // 手动指定要查询的字段
        queryWrapper.select("id", "name", "sort", "icon");
        // 排序
        queryWrapper.orderByAsc("sort");
        // 查询
        List<ShopType> list = list(queryWrapper);

        if (list == null || list.isEmpty()) {
            // 4.不存在，返回错误信息
            return Result.fail("没有查询到商铺种类");
        }
        // 5.存在，写入到redis中
        Set<ZSetOperations.TypedTuple<String>> resultSet = new HashSet<>();
        for (ShopType shopType : list) {
            // 遍历list集合，将每个元素作为封装成一个个ZSetOperations.TypedTuple<String>
            String jsonStr = JSONUtil.toJsonStr(shopType);
            Double score = Double.valueOf(shopType.getSort());
            // 然后放进一个set里
            resultSet.add(new DefaultTypedTuple<>(jsonStr, score));
        }
        stringRedisTemplate.opsForZSet().add(CACHE_SHOPTYPE_KEY, resultSet);
        stringRedisTemplate.expire(CACHE_SHOPTYPE_KEY, CACHE_SHOPTYPE_TTL, TimeUnit.HOURS);
        // 6.返回查询结果
        return Result.ok(list);
    }
}
