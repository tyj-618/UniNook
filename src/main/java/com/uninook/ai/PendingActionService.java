package com.uninook.ai;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class PendingActionService {

    private final PendingActionStore pendingActionStore;
    private final AiProperties properties;

    public PendingActionService(PendingActionStore pendingActionStore, AiProperties properties) {
        this.pendingActionStore = pendingActionStore;
        this.properties = properties;
    }

    public PendingActionSummary preparePostDraft(ToolExecutionContext context, Map<String, Object> arguments) {
        String title = String.valueOf(arguments.get("title")).trim();
        String content = String.valueOf(arguments.get("content")).trim();
        Instant expiresAt = Instant.now().plusSeconds(properties.getPendingActionTtlSeconds());
        PendingAction action = new PendingAction(UUID.randomUUID().toString(), PendingActionType.CREATE_POST,
                context.userId(), title, content, expiresAt);
        pendingActionStore.save(action);
        return action.summary();
    }

    public java.util.Optional<PendingAction> loadForUser(Long userId, String actionId) {
        return pendingActionStore.load(userId, actionId);
    }
}
