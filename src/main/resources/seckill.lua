--1.参数列表
local voucherId = ARGV[1]
local userId = ARGV[2]

--2.数据Key
local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

--3.脚本业务
--判断库存是否充足
if (tonumber(redis.call('get', stockKey)) <= 0) then
    --库存不足，返回1
    return 1
end
--判断用户是否下单
if (redis.call('sismember', orderKey, userId) == 1) then
    --存在，说明是重复下单, 返回2
    return 2
end
--扣库存，下单
redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)
return 0