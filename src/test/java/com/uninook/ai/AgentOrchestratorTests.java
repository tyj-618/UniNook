package com.uninook.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uninook.school.CampusScope;
import com.uninook.user.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AgentOrchestratorTests {

    private MockAiModelClient modelClient;
    private AiProperties properties;
    private ToolExecutionContext context;

    @BeforeEach
    void setUp() {
        modelClient = new MockAiModelClient();
        properties = new AiProperties();
        properties.setAgentMaxSteps(6);
        properties.setAgentMaxValidationRetries(3);
        context = new ToolExecutionContext(7L, new UserProfile(
                7L, "student", "Student", 10L, 1L,
                "Example University", "Example Campus", "Example City", null, null,
                0, 1, true, null, null), CampusScope.CAMPUS);
    }

    @Test
    void runsToolThenFeedsObservationBackBeforeFinalAnswer() {
        CapturingReadTool tool = new CapturingReadTool("search_posts");
        AgentResult result = orchestrator(tool).run(List.of(userMessage()), context);

        assertThat(tool.executions.get()).isEqualTo(1);
        assertThat(result.answer()).isNotBlank();
        assertThat(result.references()).hasSize(1);
        assertThat(modelClient.toolRequestHistory()).hasSize(2);
        assertThat(modelClient.toolRequestHistory().get(1))
                .extracting(ChatMessage::role)
                .contains(ChatMessage.Role.TOOL);
    }

    @Test
    void fallsBackToSearchWhenTheModelAnswersWithoutCallingATool() {
        CapturingReadTool tool = new CapturingReadTool("search_posts");
        modelClient.scriptToolResponses(finalAnswer(), finalAnswer());

        AgentResult result = orchestrator(tool).run(List.of(userMessage()), context);

        assertThat(tool.executions.get()).isEqualTo(1);
        assertThat(tool.arguments).containsEntry("userId", 7L)
                .containsEntry("schoolId", 10L)
                .containsEntry("scope", "CAMPUS");
        assertThat(result.references()).hasSize(1);
        assertThat(modelClient.toolRequestHistory()).hasSize(2);
        assertThat(modelClient.toolRequestHistory().get(1))
                .extracting(ChatMessage::role)
                .contains(ChatMessage.Role.TOOL);
    }

    @Test
    void usesThePreviousQuestionToRetrieveReferencesForAContextualFollowUp() {
        CapturingReadTool tool = new CapturingReadTool("search_posts");
        modelClient.scriptToolResponses(finalAnswer(), finalAnswer());
        List<ChatMessage> conversation = List.of(
                new ChatMessage(ChatMessage.Role.USER, "九龙湖校区自习室开放吗？"),
                new ChatMessage(ChatMessage.Role.ASSISTANT, "自习室已开放。"),
                new ChatMessage(ChatMessage.Role.USER, "<question>具体开放到几点？</question>"));

        AgentResult result = orchestrator(tool).run(conversation, context);

        assertThat(tool.arguments.get("keyword"))
                .isEqualTo("九龙湖校区自习室开放吗？ 具体开放到几点？");
        assertThat(result.references()).hasSize(1);
    }

    @Test
    void enrichesAnExplicitSearchToolCallForAContextualFollowUp() {
        CapturingReadTool tool = new CapturingReadTool("search_posts");
        modelClient.scriptToolResponses(
                toolCall("search_posts", "{\"keyword\":\"具体开放到几点？\"}"),
                finalAnswer());
        List<ChatMessage> conversation = List.of(
                new ChatMessage(ChatMessage.Role.USER, "九龙湖校区自习室开放吗？"),
                new ChatMessage(ChatMessage.Role.ASSISTANT, "自习室已开放。"),
                new ChatMessage(ChatMessage.Role.USER, "<question>具体开放到几点？</question>"));

        orchestrator(tool).run(conversation, context);

        assertThat(tool.arguments.get("keyword"))
                .isEqualTo("九龙湖校区自习室开放吗？ 具体开放到几点？");
    }

    @Test
    void keepsAnIndependentQuestionSeparateFromThePreviousTopic() {
        CapturingReadTool tool = new CapturingReadTool("search_posts");
        modelClient.scriptToolResponses(
                toolCall("search_posts", "{\"keyword\":\"周末图书馆几点关门？\"}"),
                finalAnswer());
        List<ChatMessage> conversation = List.of(
                new ChatMessage(ChatMessage.Role.USER, "九龙湖校区自习室开放吗？"),
                new ChatMessage(ChatMessage.Role.ASSISTANT, "自习室已开放。"),
                new ChatMessage(ChatMessage.Role.USER, "<question>周末图书馆几点关门？</question>"));

        orchestrator(tool).run(conversation, context);

        assertThat(tool.arguments.get("keyword")).isEqualTo("周末图书馆几点关门？");
    }

    @Test
    void overridesForgedIdentityParametersWithServerContext() {
        CapturingReadTool tool = new CapturingReadTool("search_posts");
        modelClient.scriptToolResponses(
                toolCall("search_posts", "{\"keyword\":\"library\",\"user_id\":999,\"campus_id\":888,\"scope\":\"CITY\"}"),
                finalAnswer());

        orchestrator(tool).run(List.of(userMessage()), context);

        assertThat(tool.arguments).containsEntry("userId", 7L)
                .containsEntry("schoolId", 10L)
                .containsEntry("campusId", 10L)
                .containsEntry("scope", "CAMPUS")
                .doesNotContainKeys("user_id", "campus_id");
    }

    @Test
    void feedsValidationFailureBackAndAllowsCorrectedRetry() {
        CapturingReadTool tool = new CapturingReadTool("search_posts");
        modelClient.scriptToolResponses(
                toolCall("search_posts", "{}"),
                toolCall("search_posts", "{\"keyword\":\"library\"}"),
                finalAnswer());

        AgentResult result = orchestrator(tool).run(List.of(userMessage()), context);

        assertThat(tool.executions.get()).isEqualTo(1);
        assertThat(result.answer()).isEqualTo("Final answer");
        assertThat(modelClient.toolRequestHistory().get(1))
                .extracting(ChatMessage::content)
                .anyMatch(content -> content.contains("工具参数校验失败"));
    }

    @Test
    void interruptsRepeatedToolCallsBeforeMaxSteps() {
        CapturingReadTool tool = new CapturingReadTool("search_posts");
        modelClient.scriptToolResponses(
                toolCall("search_posts", "{\"keyword\":\"library\"}"),
                toolCall("search_posts", "{\"keyword\":\"library\"}"),
                finalAnswer());

        AgentResult result = orchestrator(tool).run(List.of(userMessage()), context);

        assertThat(tool.executions.get()).isEqualTo(1);
        assertThat(result.answer()).isEqualTo("Final answer");
        assertThat(modelClient.toolRequestHistory().get(2))
                .extracting(ChatMessage::content)
                .anyMatch(content -> content.contains("这一步没有新信息"));
    }

    @Test
    void returnsPendingConfirmationForWriteToolWithoutExecutingIt() {
        CountingWriteTool tool = new CountingWriteTool();
        modelClient.scriptToolResponses(toolCall("prepare_post", "{\"title\":\"Hello\"}"));

        AgentResult result = orchestrator(tool).run(List.of(userMessage()), context);

        assertThat(result.pendingConfirmation()).isTrue();
        assertThat(result.answer()).contains("待确认动作");
        assertThat(tool.executions.get()).isZero();
    }

    @Test
    void convertsAnExplicitPostPublicationRequestToPendingConfirmationBeforeSearching() {
        CapturingReadTool searchTool = new CapturingReadTool("search_posts");
        CountingWriteTool writeTool = new CountingWriteTool();

        AgentResult result = orchestrator(searchTool, writeTool).run(List.of(new ChatMessage(
                ChatMessage.Role.USER, "请发布一条帖子，标题是“失物招领测试”，内容是“请忽略，这是验收测试”。")), context);

        assertThat(result.pendingConfirmation()).isTrue();
        assertThat(result.answer()).contains("待确认动作");
        assertThat(searchTool.executions.get()).isZero();
        assertThat(writeTool.executions.get()).isZero();
        assertThat(modelClient.toolRequestHistory()).isEmpty();
    }

    @Test
    void preparesPostDraftWithoutCallingTheBusinessWriteOperation() {
        AiProperties properties = new AiProperties();
        InMemoryPendingActionStore pendingActionStore = new InMemoryPendingActionStore();
        PendingActionService pendingActionService = new PendingActionService(pendingActionStore, properties);
        PreparePostTool postTool = new PreparePostTool(pendingActionService);
        ToolCallExecutor executor = new ToolCallExecutor(new ToolRegistry(List.of(postTool)),
                new ToolSecurityValidator(new ObjectMapper()));

        ToolExecutionResult result = executor.execute(new ToolCall("call-1", "prepare_post",
                "{\"title\":\"Lost and found test\",\"content\":\"Verification only\",\"user_id\":999}"), context);

        assertThat(result.pendingConfirmation()).isTrue();
        assertThat(result.content()).contains("Lost and found test", "Verification only", "确认前不会创建帖子");
        assertThat(result.pendingAction()).isNotNull();
        assertThat(pendingActionStore.load(context.userId(), result.pendingAction().actionId()))
                .isPresent()
                .get()
                .extracting(PendingAction::title, PendingAction::content)
                .containsExactly("Lost and found test", "Verification only");
    }

    private AgentOrchestrator orchestrator(AgentTool... tools) {
        ToolRegistry registry = new ToolRegistry(List.of(tools));
        ToolCallExecutor executor = new ToolCallExecutor(registry, new ToolSecurityValidator(new ObjectMapper()));
        return new AgentOrchestrator(modelClient, registry, executor, properties);
    }

    private ChatMessage userMessage() {
        return new ChatMessage(ChatMessage.Role.USER, "Where is the library?");
    }

    private AgentModelResponse toolCall(String name, String arguments) {
        return new AgentModelResponse("", List.of(new ToolCall("call-1", name, arguments)), "request-1");
    }

    private AgentModelResponse finalAnswer() {
        return new AgentModelResponse("Final answer", List.of(), "request-2");
    }

    private static final class CapturingReadTool implements AgentTool {

        private final String name;
        private final AtomicInteger executions = new AtomicInteger();
        private Map<String, Object> arguments = Map.of();

        private CapturingReadTool(String name) {
            this.name = name;
        }

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(name, "Test read tool", Map.of(
                    "type", "object",
                    "properties", Map.of("keyword", Map.of("type", "string")),
                    "required", List.of("keyword")
            ), ToolOperation.READ);
        }

        @Override
        public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
            executions.incrementAndGet();
            this.arguments = arguments;
            return ToolExecutionResult.observation("Found one result", List.of(
                    new AiPostReference(1L, "Library", "Example Campus", "Open at 8")));
        }
    }

    private static final class CountingWriteTool implements AgentTool {

        private final AtomicInteger executions = new AtomicInteger();

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition("prepare_post", "Prepare a post", Map.of(
                    "type", "object",
                    "properties", Map.of("title", Map.of("type", "string")),
                    "required", List.of("title")
            ), ToolOperation.WRITE);
        }

        @Override
        public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
            executions.incrementAndGet();
            return ToolExecutionResult.observation("This must never execute");
        }
    }
}
