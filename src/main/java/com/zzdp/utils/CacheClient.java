package com.zzdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.zzdp.utils.RedisConstants.*;

/**
 * @author 千叶零
 * @version 1.0
 * create 2023-05-27  16:21:47
 */
@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 添加缓存方法
     *
     * @param key   缓存key
     * @param value 缓存值
     * @param time  过期时间
     * @param unit  时间类型
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }


    /**
     * 添加缓存，设置逻辑过期方法
     *
     * @param key   缓存key
     * @param value 缓存值
     * @param time  逻辑过期时间
     * @param unit  时间类型
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

        //写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    /**
     * 获取缓存，解决缓存穿透
     *
     * @param keyPrefix  key前缀
     * @param id         id
     * @param type       对象类型
     * @param dbFallBack 查询函数
     * @param time       过期时间
     * @param unit       时间类型
     * @param <R>        自定义泛型
     * @param <ID>       自定义泛型
     * @return 对象
     */
    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallBack,
            Long time, TimeUnit unit) {

        //定义Redis key
        String key = keyPrefix + id;

        //1.从redis查询
        String json = stringRedisTemplate.opsForValue().get(key);

        //2.判断缓存是否存在
        if (StrUtil.isNotBlank(json)) {
            //3.存在，返回缓存数据
            return JSONUtil.toBean(json, type);
        }

        //解决缓存穿透,缓存命中的是否是"", 如果是，直接返回，不进行数据库查询
        if (json != null) {
            // 返回错误信息
            return null;
        }

        //4.不存在，查询数据
        R r = dbFallBack.apply(id);

        //5.判断是否存在
        if (r == null) {
            //解决缓存击穿，将null写入redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }


        //6.存在，添加缓存写入Redis
        this.set(key, r, time, unit);

        //7.返回数据
        return r;
    }


    //定义线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    /**
     * 添加互斥锁方法
     *
     * @param key
     * @return
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }


    /**
     * 移除互斥锁方法
     *
     * @param key
     */
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }


    /**
     * 获取缓存，逻辑过期解决缓存击穿
     *
     * @param keyPrefix  key前缀
     * @param id         id
     * @param type       对象类型
     * @param dbFallBack 查询函数
     * @param time       过期时间
     * @param unit       时间类型
     * @param <R>        自定义泛型
     * @param <ID>       自定义泛型
     * @return 对象
     */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallBack,
                                            Long time, TimeUnit unit) {
        //定义Redis key
        String key = keyPrefix + id;

        //1.从redis查询商品
        String json = stringRedisTemplate.opsForValue().get(key);

        //2.判断缓存是否为空
        if (StrUtil.isBlank(json)) {
            //3.为空，返回null
            return null;
        }

        //4.存在，把JSON反序列化为对象，判断缓存是否过期
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();

        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期，直接返回店铺信息
            return r;
        }

        //已过期，需要缓存重建
        //5.缓存重建
        //5.1获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);

        //5.2.是否获取成功
        if (isLock) {
            //成功，DoubleCheck
            json = stringRedisTemplate.opsForValue().get(key);
            //判断缓存是否存在
            if (StrUtil.isNotBlank(json)) {
                //3.存在，查看是否过期
                redisData = JSONUtil.toBean(json, RedisData.class);
                r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
                expireTime = redisData.getExpireTime();

                if (expireTime.isAfter(LocalDateTime.now())) {
                    //未过期，直接返回店铺信息
                    return r;
                }
            }

            // 开启独立线程开启缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //查询数据库
                    R r1 = dbFallBack.apply(id);

                    //吸入Redis
                    this.setWithLogicalExpire(key, r1, time, unit);

                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }

        //返回过期的商品信息
        return r;
    }


    /**
     * 互斥锁解决缓存击穿
     *
     * @param keyPrefix  key前缀
     * @param id         id
     * @param type       对象类型
     * @param dbFallBack 查询函数
     * @param time       过期时间
     * @param unit       时间类型
     * @param <R>        自定义泛型
     * @param <ID>       自定义泛型
     * @return 对象
     */
    public <R, ID> R queryWithMutex(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallBack,
                                    Long time, TimeUnit unit) {
        //定义Redis key
        String key = keyPrefix + id;

        //1.从redis查询商品
        String json = stringRedisTemplate.opsForValue().get(key);

        //2.判断缓存是否不为空
        if (StrUtil.isNotBlank(json)) {
            //3.不为空，返回缓存
            return JSONUtil.toBean(json, type);
        }

        //解决缓存穿透,缓存命中的是否是"", 如果是，直接返回，不进行数据库查询
        if (json != null) {
            // 返回错误信息
            return null;
        }

        //4.实现缓存重建
        //4.1.获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;

        R r;
        try {

            boolean isLock = tryLock(lockKey);
            //4.2.判断是否获取成功
            if (!isLock) {
                //如果失败，休眠并重试
                Thread.sleep(50);
                //递归
                return queryWithMutex(keyPrefix, id, type, dbFallBack, time, unit);
            }

            //如果成功，再次进行缓存判断是否存在

            //4.3.判断缓存是否存在
            json = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(json)) {
                //3.存在，返回缓存
                return JSONUtil.toBean(json, type);
            }

            //解决缓存穿透,缓存命中的是否是"", 如果是，直接返回，不进行数据库查询
            if (json != null) {
                // 返回错误信息
                return null;
            }


            //缓存不存在时，进行相关查询
            r = dbFallBack.apply(id);

            //5.判断商品是否存在
            if (r == null) {
                //解决缓存击穿，将null写入redis
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }

            //6.商品存在，添加缓存写入Redis
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(r), time, unit);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);

        } finally {
            //7.释放互斥锁，返回数据
            unLock(lockKey);
        }
        return r;
    }


}
