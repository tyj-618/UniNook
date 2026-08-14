package com.uninook.ai;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("!redis")
public class InMemoryPendingActionStore implements PendingActionStore {

    private final Map<String, PendingAction> actions = new ConcurrentHashMap<>();

    @Override
    public void save(PendingAction action) {
        actions.put(key(action.userId(), action.actionId()), action);
    }

    @Override
    public Optional<PendingAction> load(Long userId, String actionId) {
        String key = key(userId, actionId);
        return available(userId, key, actions.get(key));
    }

    @Override
    public Optional<PendingAction> take(Long userId, String actionId) {
        String key = key(userId, actionId);
        return available(userId, key, actions.remove(key));
    }

    @Override
    public void delete(Long userId, String actionId) {
        actions.remove(key(userId, actionId));
    }

    private Optional<PendingAction> available(Long userId, String key, PendingAction action) {
        if (action == null) {
            return Optional.empty();
        }
        if (!action.userId().equals(userId) || !action.expiresAt().isAfter(Instant.now())) {
            actions.remove(key, action);
            return Optional.empty();
        }
        return Optional.of(action);
    }

    private String key(Long userId, String actionId) {
        return userId + ":" + actionId;
    }
}
