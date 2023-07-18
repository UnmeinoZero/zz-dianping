package com.zzdp.service.impl;

import com.zzdp.dto.Result;
import com.zzdp.entity.Shop;
import com.zzdp.mapper.ShopMapper;
import com.zzdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zzdp.utils.CacheClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.zzdp.utils.RedisConstants.*;

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
    private CacheClient cacheClient;

    /**
     * 根据id查询店铺
     *
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {

        //罗辑过期解决缓存击穿
//        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        Shop shop = cacheClient.queryWithMutex(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        if (shop == null) {
            return Result.fail("店铺不存在！");
        }

        return Result.ok(shop);
    }

    //定义线程池
//    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


//    /**
//     * 罗辑删除解决缓存穿透
//     *
//     * @param id
//     * @return
//     */
//    public Shop queryWithLogicalExpire(Long id) {
//        //定义Redis key
//        String key = CACHE_SHOP_KEY + id;
//
//        //1.从redis查询商品
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//
//        //2.判断缓存是否为空
//        if (StrUtil.isBlank(shopJson)) {
//            //3.为空，返回null
//            return null;
//        }
//
//        //4.存在，把JSON反序列化为对象，判断缓存是否过期
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//
//        if (expireTime.isAfter(LocalDateTime.now())) {
//            //未过期，直接返回店铺信息
//            return shop;
//        }
//
//        //已过期，需要缓存重建
//        //5.缓存重建
//        //5.1获取互斥锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        boolean isLock = tryLock(lockKey);
//
//        //5.2.是否获取成功
//        if (isLock) {
//            //成功，DoubleCheck
//            shopJson = stringRedisTemplate.opsForValue().get(key);
//            //判断缓存是否存在
//            if (StrUtil.isNotBlank(shopJson)) {
//                //3.存在，查看是否过期
//                redisData = JSONUtil.toBean(shopJson, RedisData.class);
//                shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//                expireTime = redisData.getExpireTime();
//
//                if (expireTime.isAfter(LocalDateTime.now())) {
//                    //未过期，直接返回店铺信息
//                    return shop;
//                }
//            }
//
//            // 开启独立线程开启缓存重建
//            CACHE_REBUILD_EXECUTOR.submit(() -> {
//                try {
//                    this.saveShop2Redis(id, 20L);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                } finally {
//                    //释放锁
//                    unLock(lockKey);
//                }
//            });
//        }
//
//        //返回过期的商品信息
//        return shop;
//    }
//
//
//
//
//    /**
//     * 互斥锁解决缓存击穿
//     *
//     * @param id
//     * @return
//     */
//    public Shop queryWithMutex(Long id) {
//        //定义Redis key
//        String key = CACHE_SHOP_KEY + id;
//
//        //1.从redis查询商品
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//
//        //2.判断缓存是否不为空
//        if (StrUtil.isNotBlank(shopJson)) {
//            //3.不为空，返回缓存
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//
//        //解决缓存穿透,缓存命中的是否是"", 如果是，直接返回，不进行数据库查询
//        if (shopJson != null) {
//            // 返回错误信息
//            return null;
//        }
//
//        //4.实现缓存重建
//        //4.1.获取互斥锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        Shop shop;
//        try {
//            boolean isLock = tryLock(lockKey);
//            //4.2.判断是否获取成功
//            if (!isLock) {
//                //如果失败，休眠并重试
//                Thread.sleep(50);
//                //递归
//                return queryWithMutex(id);
//            }
//
//            //如果成功，再次进行缓存判断是否存在
//
//            //4.3.判断缓存是否存在
//            shopJson = stringRedisTemplate.opsForValue().get(key);
//            if (StrUtil.isNotBlank(shopJson)) {
//                //3.存在，返回缓存
//                return JSONUtil.toBean(shopJson, Shop.class);
//            }
//
//            //解决缓存穿透,缓存命中的是否是"", 如果是，直接返回，不进行数据库查询
//            if (shopJson != null) {
//                // 返回错误信息
//                return null;
//            }
//
//            //缓存不存在时，根据id查询
//            //模拟重建数据查询数据库延迟
//            Thread.sleep(200);
//            shop = getById(id);
//
//            //5.判断商品是否存在
//            if (shop == null) {
//                //解决缓存击穿，将null写入redis
//                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//
//            //6.商品存在，添加缓存写入Redis, 有效时间 30分钟
//            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//
//        } finally {
//            //7.释放互斥锁，返回数据
//            unLock(lockKey);
//        }
//        return shop;
//    }
//
//
//    /**
//     * 解决缓存穿透
//     *
//     * @param id
//     * @return
//     */
//    public Shop queryWithPassThrough(Long id) {
//        //定义Redis key
//        String key = CACHE_SHOP_KEY + id;
//
//        //1.从redis查询商品
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//
//        //2.判断缓存是否存在
//        if (StrUtil.isNotBlank(shopJson)) {
//            //3.存在，返回缓存
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//
//        //解决缓存穿透,缓存命中的是否是"", 如果是，直接返回，不进行数据库查询
//        if (shopJson != null) {
//            // 返回错误信息
//            return null;
//        }
//
//        //4.不存在，查询数据
//        Shop shop = getById(id);
//
//        //5.判断商品是否存在
//        if (shop == null) {
//            //解决缓存击穿，将null写入redis
//            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//
//            return null;
//        }
//
//
//        //6.商品存在，添加缓存写入Redis, 有效时间 30分钟
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//
//        //7.返回数据
//        return shop;
//    }
//
//
//    /**
//     * 添加互斥锁方法
//     *
//     * @param key
//     * @return
//     */
//    private boolean tryLock(String key) {
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
//
//
//    /**
//     * 移除互斥锁方法
//     *
//     * @param key
//     */
//    private void unLock(String key) {
//        stringRedisTemplate.delete(key);
//    }
//
//
//    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
//        //模拟延迟
//        Thread.sleep(200);
//        //1.查询店铺数据
//        Shop shop = getById(id);
//        //2.封装罗辑过期时间
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//        //3.写入Redis
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
//    }


    /**
     * 更新店铺信息
     *
     * @param shop
     * @return
     */
    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        //更新数据
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    /**
     * 根据位置信息分页查询
     */
//    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
//        //1.判断是否需要根据坐标查询
//        if (x == null || y == null) {
//            //不需要坐标查询，按数据库查询
//            Page<Shop> page = query()
//                    .eq("type_id", typeId)
//                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
//            //返回数据
//            return Result.ok(page.getRecords());
//        }
//
//        //2.计算分页参数
//        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
//        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
//
//        //3.查询redis，按照距离排序，分页，结果：shopId, distance
//        String key = SHOP_GEO_KEY + typeId;
//        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
//                .search(
//                        key,
//                        GeoReference.fromCoordinate(x, y),
//                        new Distance(5000),
//                        RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance().limit(end)
//                );
//
//        //4.解析处id
//        if (results == null){
//            return Result.ok(Collections.emptyList());
//        }
//        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
//        if (list.size() <= from){
//            //没有下一页了，结束
//            return Result.ok(Collections.emptyList());
//        }
//        //截取 from ~ end 的部分
//        List<Long> ids = new ArrayList<>(list.size());
//        Map<String, Distance> distanceMap = new HashMap<>(list.size());
//        list.stream().skip(from).forEach(result -> {
//            //获取店铺Id
//            String shopIdStr = result.getContent().getName();
//            ids.add(Long.valueOf(shopIdStr));
//            //获取距离
//            Distance distance = result.getDistance();
//            distanceMap.put(shopIdStr, distance);
//        });
//        //5.根据id查询shop
//        String idStr = StrUtil.join(",", ids);
//        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELE(id," + idStr + ")").list();
//        for (Shop shop : shops) {
//            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
//        }
//
//        //6.返回
//        return Result.ok(shops);
        return null;
    }


}
