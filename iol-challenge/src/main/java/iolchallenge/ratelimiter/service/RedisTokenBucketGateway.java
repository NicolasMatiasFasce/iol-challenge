package iolchallenge.ratelimiter.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class RedisTokenBucketGateway implements TokenBucketGateway {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisTokenBucketGateway.class);

    private static final String LUA_SCRIPT = """
        local capacity = tonumber(ARGV[1])
        local refill = tonumber(ARGV[2])
        local now = tonumber(ARGV[3])

        local data = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
        local tokens = tonumber(data[1])
        local ts = tonumber(data[2])

        if not tokens then
          tokens = capacity
          ts = now
        end

        local delta = math.max(0, now - ts) / 1000.0
        tokens = math.min(capacity, tokens + (delta * refill))

        local allowed = 0
        local retryAfter = 0

        if tokens >= 1 then
          allowed = 1
          tokens = tokens - 1
        else
          local deficit = 1 - tokens
          retryAfter = math.ceil(deficit / refill)
        end

        redis.call('HMSET', KEYS[1], 'tokens', tokens, 'ts', now)
        local ttl = math.ceil((capacity / refill) * 2)
        if ttl < 1 then ttl = 1 end
        redis.call('EXPIRE', KEYS[1], ttl)

        return {allowed, math.floor(tokens), retryAfter}
        """;

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List> script;

    /**
     * Inicializa el gateway Redis y compila el script Lua atomico del token bucket.
     */
    public RedisTokenBucketGateway(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>(LUA_SCRIPT, List.class);
    }

    /**
     * Consume un token de la cuota distribuida y calcula retry-after cuando no hay disponibilidad.
     */
    @Override
    public TokenBucketResult consume(String key, int capacity, int refillRatePerSecond) {
        List<?> result = redisTemplate.execute(
            script,
            List.of(key),
            String.valueOf(capacity),
            String.valueOf(refillRatePerSecond),
            String.valueOf(Instant.now().toEpochMilli()));

        if (result == null || result.size() < 3) {
            LOGGER.error("Invalid Redis Lua response key={} result={}", key, result);
            throw new IllegalStateException("Invalid Redis Lua response for token bucket");
        }

        boolean allowed = parseLong(result.get(0)) == 1L;
        int remaining = Math.max(0, (int) parseLong(result.get(1)));
        long retryAfterSeconds = Math.max(0, parseLong(result.get(2)));

        LOGGER.debug("Token bucket consume key={} allowed={} remaining={} retryAfterSeconds={} capacity={} refillRatePerSecond={}",
            key, allowed, remaining, retryAfterSeconds, capacity, refillRatePerSecond);

        return new TokenBucketResult(allowed, remaining, retryAfterSeconds);
    }

    /**
     * Convierte de forma segura valores numericos devueltos por Redis/Lua.
     */
    private long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}

