package com.zzdp.utils;

/**
 * @author 千叶零
 * @version 1.0
 * create 2023-06-06  13:19:00
 */
public interface ILock {

    /**
     * 尝试获取锁
     * @param timeoutSec 超时时间，过期后自动释放
     * @return 获取结果
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
