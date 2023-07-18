package com.zzdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zzdp.dto.Result;
import com.zzdp.dto.ScrollResult;
import com.zzdp.dto.UserDTO;
import com.zzdp.entity.Blog;
import com.zzdp.entity.Follow;
import com.zzdp.entity.User;
import com.zzdp.mapper.BlogMapper;
import com.zzdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zzdp.service.IFollowService;
import com.zzdp.service.IUserService;
import com.zzdp.utils.SystemConstants;
import com.zzdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.zzdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.zzdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IFollowService followService;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        //1.查询blog
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("博客不存在");
        }
        //2.查询blog有关的对象
        queryBlogUser(blog);
        //3.查询blog是否被点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }


    @Override
    public Result likeBlog(Long id) {
        //获取当前用户
        Long userId = UserHolder.getUser().getId();
        //判断当前用户是否已经点赞
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            //未点赞，可以点赞
            //数据库点赞数 +1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            //保存用户到Reids的Zset集合  zadd key value score
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }

        } else {
            //已点赞，取消点赞
            //数据库点赞数 -1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            //把用户冲Redis的Set集合移除
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }

        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        //查询top5点赞用户 zrange key 0 4
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);

        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        //解析出用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        //根据用户id查询用户
        List<UserDTO> userDTOS = userService
                .query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list()
                .stream().map(user ->
                        BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());

        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = save(blog);

        if (!isSuccess) {
            return Result.fail("新增笔记失败");
        }
        //查询笔记作者的所有粉丝 select * from tb_follow where follow_user_id = ?
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();

        //推送笔记id给所有粉丝
        for (Follow follow : follows) {
            //获取粉丝id
            Long userId = follow.getUserId();
            //推送
            String key = FEED_KEY + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());

        }

        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();//2.查询收件箱
        String key = FEED_KEY + userId;

        //2.查询(滚动分页查询) ZREVRANGEBYSCORE key Max Min LIMIT offset count
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        //非空判断
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        //3.解析数据 blogId, minTime(时间戳), offSet
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            //获取id
            ids.add(Long.valueOf(tuple.getValue()));
            //获取分数(时间戳)
            long time = tuple.getScore().longValue();
            if (time == minTime) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }

        //4.根据id查询blog
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();

        for (Blog blog : blogs) {
            //查询blog有关的用户
            queryBlogUser(blog);
            //查询blog是否被点赞
            isBlogLiked(blog);
        }


        //5.封装返回
        ScrollResult r = new ScrollResult();
        r.setList(blogs);
        r.setOffset(os);
        r.setMinTime(minTime);

        return Result.ok(r);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    private void isBlogLiked(Blog blog) {
        //获取当前用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            //用户未登录
            return;
        }
        Long userId = user.getId();
        //判断当前用户是否已经点赞
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }
}
