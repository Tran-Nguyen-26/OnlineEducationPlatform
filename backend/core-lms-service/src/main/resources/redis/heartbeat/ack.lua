if redis.call('GET', KEYS[2]) ~= ARGV[1] then
    return 0
end
return redis.call('DEL', KEYS[1], KEYS[2])
