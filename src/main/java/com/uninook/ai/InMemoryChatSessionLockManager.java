package com.uninook.ai;

import com.uninook.common.ErrorCode;
import com.uninook.exception.BusinessException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Component
@Profile("!redis")
public class InMemoryChatSessionLockManager implements ChatSessionLockManager {

    private static final SessionLock NO_OP_LOCK = () -> { };
    private final Map<String, LockHolder> locks = new ConcurrentHashMap<>();

    @Override
    public SessionLock acquire(Long userId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return NO_OP_LOCK;
        }
        String key = userId + ":" + sessionId;
        LockHolder holder = retain(key);
        if (!holder.lock().tryLock()) {
            releaseReference(key, holder);
            throw new BusinessException(ErrorCode.CONFLICT, "该会话正在生成回复，请等待当前回复完成后再提问");
        }
        return () -> {
            holder.lock().unlock();
            releaseReference(key, holder);
        };
    }

    private LockHolder retain(String key) {
        return locks.compute(key, (ignored, current) -> {
            LockHolder holder = current == null ? new LockHolder() : current;
            holder.references++;
            return holder;
        });
    }

    private void releaseReference(String key, LockHolder holder) {
        locks.computeIfPresent(key, (ignored, current) -> {
            if (current != holder) {
                return current;
            }
            return --holder.references == 0 ? null : holder;
        });
    }

    private static final class LockHolder {

        private final ReentrantLock lock = new ReentrantLock();
        private int references;

        private ReentrantLock lock() {
            return lock;
        }
    }
}
