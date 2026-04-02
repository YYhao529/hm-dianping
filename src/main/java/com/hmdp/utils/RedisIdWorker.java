package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    /**
     *  id格式：
     *  1 bit符号位   |   31 bit时间戳   |   32 bit序列号
     *  时间戳：当前时间戳-开始时间戳
     *  序列号：
     *  一个long类型，使用redis的String类型存储，自增且键的格式为yyyy:MM:dd，对于一个有符号整数而言，虽然redis最大能存储2^64，但是序列号只有32bit
     *  所以每一天能最多容纳2^32次方个不同的id
     */

    // 开始时间戳
    private static final long BEGIN_TIMESTAMP = 1767225600L;

    // 序列号位数
    private static final int COUNT_BITS=32;


    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public Long nextId(String keyPrefix) {
        // 1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 2.生成序列号  "icr:prefix:2026:1:1"
        // 2.1生成key
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));

        // 2.2自增
        long count = stringRedisTemplate.opsForValue().increment("icr" + ":" + keyPrefix + ":" + date);

        // 3.拼接返回
        return timestamp << COUNT_BITS | count;
    }

}
