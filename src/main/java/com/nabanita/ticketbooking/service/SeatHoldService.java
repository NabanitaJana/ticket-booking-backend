package com.nabanita.ticketbooking.service;

import com.nabanita.ticketbooking.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/** Temporary seat holds in Redis. Lua scripts run atomically, so multi-seat operations are all-or-nothing. */
@Service
@RequiredArgsConstructor
public class SeatHoldService {

    /**
     * Fails (0) if any seat is held by someone else. Seats the user already holds keep their
     * original TTL, so repeating the call cannot extend a hold. Returns the shortest TTL in ms.
     */
    private static final RedisScript<Long> HOLD = RedisScript.of("""
            for _, k in ipairs(KEYS) do
              local owner = redis.call('GET', k)
              if owner and owner ~= ARGV[1] then return 0 end
            end
            local ttl = tonumber(ARGV[2])
            for _, k in ipairs(KEYS) do
              redis.call('SET', k, ARGV[1], 'NX', 'PX', ARGV[2])
              ttl = math.min(ttl, redis.call('PTTL', k))
            end
            return math.max(ttl, 1)
            """, Long.class);

    /** Deletes only the caller's holds: an expired hold may already belong to another user. */
    private static final RedisScript<Long> RELEASE = RedisScript.of("""
            local released = 0
            for _, k in ipairs(KEYS) do
              if redis.call('GET', k) == ARGV[1] then released = released + redis.call('DEL', k) end
            end
            return released
            """, Long.class);

    private final StringRedisTemplate redis;
    private final AppProperties props;

    /** @return remaining hold time in milliseconds, or 0 if another user holds one of the seats */
    public long hold(Long showId, Collection<Long> seatIds, String user) {
        return redis.execute(HOLD, keys(showId, seatIds), user, String.valueOf(props.holdTtl().toMillis()));
    }

    public boolean isHeldBy(Long showId, Collection<Long> seatIds, String user) {
        return owners(showId, seatIds).stream().allMatch(user::equals);
    }

    /** Hold owners in the same order as seatIds; null where a seat is not held. */
    public List<String> owners(Long showId, Collection<Long> seatIds) {
        return redis.opsForValue().multiGet(keys(showId, seatIds));
    }

    public void release(Long showId, Collection<Long> seatIds, String user) {
        redis.execute(RELEASE, keys(showId, seatIds), user);
    }

    private List<String> keys(Long showId, Collection<Long> seatIds) {
        return seatIds.stream().map(id -> "seat-hold:" + showId + ":" + id).toList();
    }
}
