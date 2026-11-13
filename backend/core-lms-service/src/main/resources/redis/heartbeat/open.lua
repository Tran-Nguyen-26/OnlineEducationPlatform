local clock = redis.call('TIME')
local now = clock[1] * 1000 + math.floor(clock[2] / 1000)
redis.call('HSET', KEYS[1], 'token', ARGV[1], 'enrollment', ARGV[2], 'lesson', ARGV[3],
    'content', ARGV[4], 'duration', ARGV[5], 'sequence', 0, 'at', now)
redis.call('PEXPIRE', KEYS[1], 45000)
return 1
