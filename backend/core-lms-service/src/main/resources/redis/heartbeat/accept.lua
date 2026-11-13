local stream = redis.call('HMGET', KEYS[1], 'token', 'enrollment', 'lesson',
    'content', 'duration', 'sequence', 'at')
if stream[1] ~= ARGV[1] or stream[2] ~= ARGV[2] or stream[3] ~= ARGV[3] then
    return -1
end
local sequence = tonumber(ARGV[4])
if sequence <= tonumber(stream[6]) then
    return 0
end

local clock = redis.call('TIME')
local now = clock[1] * 1000 + math.floor(clock[2] / 1000)
local elapsed = math.max(0, math.floor((now - tonumber(stream[7])) / 1000))
local duration = tonumber(stream[5])
local content = stream[4]
local watched = tonumber(redis.call('HGET', KEYS[4], content) or '0')
local observed = math.min(30, elapsed, tonumber(ARGV[6]))
local credit = math.min(observed, math.max(0, duration - watched))
local position = math.min(duration, tonumber(ARGV[5]))
local field = ARGV[2] .. ':' .. ARGV[3]
local previous = redis.call('HGET', KEYS[2], field)
local row = previous and cjson.decode(previous) or {
    enrollment = ARGV[2], lesson = ARGV[3], first_ms = now, seconds = 0, events = 0
}
row.content = content
row.last_ms = now
row.position = position
row.seconds = math.min(2147483647, row.seconds + credit)
row.events = row.events + 1

redis.call('HSET', KEYS[2], field, cjson.encode(row))
if credit > 0 then
    redis.call('HINCRBY', KEYS[4], content, credit)
end
for i = 5, #KEYS do
    local reviewed = tonumber(redis.call('HGET', KEYS[i], content) or '0')
    local review_credit = math.min(observed, math.max(0, duration - reviewed))
    if review_credit > 0 then
        redis.call('HINCRBY', KEYS[i], content, review_credit)
    end
end
redis.call('HSET', KEYS[1], 'sequence', sequence, 'at', now)
redis.call('PEXPIRE', KEYS[1], 45000)

local threshold = math.ceil(duration * 0.8)
if watched < threshold and watched + credit >= threshold then
    redis.call('SET', KEYS[3], '1')
end
return 1
