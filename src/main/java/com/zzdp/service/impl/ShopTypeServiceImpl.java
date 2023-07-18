package com.zzdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.zzdp.dto.Result;
import com.zzdp.entity.ShopType;
import com.zzdp.mapper.ShopTypeMapper;
import com.zzdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.zzdp.utils.RedisConstants.TPYE_SHOP_KEY;

/**
 * <p>
 *  服务实现类
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
     * 查询商品分类
     * @return
     */
    @Override
    public Result queryType() {

        //1. 查询redis缓存
        String shopTypeJSON = stringRedisTemplate.opsForValue().get(TPYE_SHOP_KEY);

        //2.判断缓存是否存在
        if (shopTypeJSON != null) {
            //存在，返回数据
            List<ShopType> shopTypeList = JSONUtil.toList(shopTypeJSON, ShopType.class);
            return Result.ok(shopTypeList);
        }

        //3.不存在，查询数据
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();

        //4.添加缓存
        //将list转为JSON
        shopTypeJSON = JSONUtil.toJsonStr(shopTypeList);
        stringRedisTemplate.opsForValue().set(TPYE_SHOP_KEY, shopTypeJSON);

        //5.返回数据
        return Result.ok(shopTypeList);
    }
}
