package com.uninook.ai;

import java.util.UUID;

/**
 * Holds the correlation identifier for one assistant request on the handling thread.
 */
public final class AiRequestContext {

    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

    private AiRequestContext() {
    }

    public static String begin(String requestId) {
        String resolved = requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
        REQUEST_ID.set(resolved);
        return resolved;
    }

    public static String requestId() {
        String requestId = REQUEST_ID.get();
        return requestId == null ? "-" : requestId;
    }

    public static void clear() {
        REQUEST_ID.remove();
    }
}
