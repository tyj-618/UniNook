package com.uninook.ai;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class PreparePostTool implements AgentTool {

    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "prepare_post",
            "Prepare a campus post draft when the user explicitly asks to publish a post. "
                    + "This tool never publishes anything; it only returns a confirmation-required draft.",
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "title", Map.of("type", "string"),
                            "content", Map.of("type", "string")
                    ),
                    "required", List.of("title", "content")
            ),
            ToolOperation.WRITE
    );

    private final PendingActionService pendingActionService;

    public PreparePostTool(PendingActionService pendingActionService) {
        this.pendingActionService = pendingActionService;
    }

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return preparePendingConfirmation(context, arguments);
    }

    @Override
    public ToolExecutionResult preparePendingConfirmation(ToolExecutionContext context, Map<String, Object> arguments) {
        PendingActionSummary pendingAction = pendingActionService.preparePostDraft(context, arguments);
        return ToolExecutionResult.pendingConfirmation(
                "待确认发布草稿：标题“%s”，内容“%s”。确认前不会创建帖子。"
                        .formatted(pendingAction.title(), pendingAction.content()),
                pendingAction);
    }
}
