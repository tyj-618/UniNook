package com.uninook.ai;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ConversationContext {

    private static final Pattern QUESTION_PATTERN = Pattern.compile("<question>\\s*(.*?)\\s*</question>", Pattern.DOTALL);
    private static final Pattern STANDALONE_FOLLOW_UP_PATTERN = Pattern.compile(
            "^(?:\u5de5\u4f5c\u65e5|\u5468\u672b|\u51e0\u70b9|\u54ea\u91cc|\u5730\u70b9)(?:\u5462|\u554a|\u5440)?$");
    private static final Pattern SCHEDULE_FOLLOW_UP_PATTERN = Pattern.compile(
            "^(?:(?:\u5de5\u4f5c\u65e5|\u5468\u672b)(?:\u5f00\u653e)?(?:\u5230)?\u51e0\u70b9|\u5f00\u653e\u5230\u51e0\u70b9)(?:\u5462|\u554a|\u5440)?$");

    private ConversationContext() {
    }

    static String extractQuestion(String message) {
        Matcher matcher = QUESTION_PATTERN.matcher(message == null ? "" : message);
        return matcher.find() ? matcher.group(1).trim() : (message == null ? "" : message.trim());
    }

    static boolean isContextualFollowUp(String question) {
        String normalized = (question == null ? "" : question)
                .replaceAll("[\\s\\p{Punct}\\x{FF0C}\\x{3002}\\x{FF1F}\\x{FF01}\\x{3001}]", "");
        return normalized.startsWith("\u90a3") || normalized.startsWith("\u5b83")
                || normalized.startsWith("\u5177\u4f53") || normalized.startsWith("\u7136\u540e")
                || normalized.startsWith("\u8fd9\u4e2a") || normalized.startsWith("\u8fd9\u95f4")
                || normalized.startsWith("\u8fd9\u91cc") || normalized.startsWith("\u8fd9\u6837")
                || STANDALONE_FOLLOW_UP_PATTERN.matcher(normalized).matches()
                || SCHEDULE_FOLLOW_UP_PATTERN.matcher(normalized).matches();
    }

    static int findLatestTopicUserMessageIndex(List<ChatMessage> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            ChatMessage message = messages.get(index);
            if (message.role() == ChatMessage.Role.USER && !isContextualFollowUp(extractQuestion(message.content()))) {
                return index;
            }
        }
        return -1;
    }
}
