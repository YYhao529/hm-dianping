-- 判断锁的标识和当前线程的标识是否一致
if(redis.call('get',KEYS[1]) == ARGV[1])
then
    redis.call('del',KEYS[1])
else
    return 0
end