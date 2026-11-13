local id = redis.call('GET', KEYS[3])
if not id then
    if redis.call('EXISTS', KEYS[1]) == 0 then
        return {}
    end
    redis.call('RENAME', KEYS[1], KEYS[2])
    id = ARGV[1]
    redis.call('SET', KEYS[3], id)
    redis.call('DEL', KEYS[4])
end
local rows = redis.call('HVALS', KEYS[2])
table.insert(rows, 1, id)
return rows
