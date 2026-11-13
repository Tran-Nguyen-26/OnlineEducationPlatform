local stream = redis.call('HMGET', KEYS[1], 'token', 'enrollment', 'lesson')
if stream[1] ~= ARGV[1] or stream[2] ~= ARGV[2] or stream[3] ~= ARGV[3] then
    return -1
end
redis.call('PEXPIRE', KEYS[1], 45000)
return 1
