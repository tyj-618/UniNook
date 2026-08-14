package com.uninook.ai;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("!redis")
public class InMemoryChatSessionStore implements ChatSessionStore {

    private final AiProperties properties;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public InMemoryChatSessionStore(AiProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<ChatMessage> load(Long userId, String sessionId) {
        Session session = sessions.get(sessionId);
        if (session == null) {
            return List.of();
        }
        if (session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(sessionId, session);
            return List.of();
        }
        if (!session.userId().equals(userId)) {
            return List.of();
        }
        return session.messages();
    }

    @Override
    public void save(Long userId, String sessionId, List<ChatMessage> messages) {
        sessions.put(sessionId, new Session(userId, List.copyOf(messages),
                Instant.now().plusSeconds(properties.getChatSessionTtlSeconds())));
    }

    private record Session(Long userId, List<ChatMessage> messages, Instant expiresAt) {
    }
}
