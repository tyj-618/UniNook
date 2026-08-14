package com.uninook.ai;

import com.uninook.auth.CurrentUserService;
import com.uninook.school.CampusScope;
import com.uninook.school.SchoolService;
import com.uninook.user.UserMapper;
import com.uninook.user.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class AiAssistantServiceTests {

    private CurrentUserService currentUserService;
    private UserMapper userMapper;
    private SchoolService schoolService;
    private PostRetriever postRetriever;
    private MockAiModelClient modelClient;
    private InMemoryChatSessionStore sessionStore;
    private AiAssistantService service;

    @BeforeEach
    void setUp() {
        currentUserService = mock(CurrentUserService.class);
        userMapper = mock(UserMapper.class);
        schoolService = mock(SchoolService.class);
        postRetriever = mock(PostRetriever.class);
        modelClient = new MockAiModelClient();
        AiProperties properties = new AiProperties();
        properties.setChatSessionMaxMessages(12);
        sessionStore = new InMemoryChatSessionStore(properties);
        service = new AiAssistantService(
                currentUserService, userMapper, schoolService, postRetriever,
                new PromptBuilder(), modelClient,
                new AiRequestRateLimiter(properties, (userId, limit) -> true), sessionStore,
                new SlidingWindowChatContextCompressor(properties), agentOrchestrator(modelClient, properties),
                new InMemoryChatSessionLockManager());

        when(currentUserService.requireUserId(anyString())).thenReturn(7L);
        when(userMapper.findProfileById(7L)).thenReturn(Optional.of(userProfile()));
        when(schoolService.listScopeSchoolIds(10L, CampusScope.CAMPUS)).thenReturn(List.of(10L));
        when(postRetriever.retrieve(any())).thenReturn(List.of(new RetrievedPost(
                101L, "校园信息", "图书馆开放时间", "示例校区", null)));
    }

    @Test
    void includesPriorTurnInSecondRequestForSameSession() {
        service.ask("Bearer token", new AiAssistantRequest("图书馆几点开门", CampusScope.CAMPUS, null, "session-1"));
        service.ask("Bearer token", new AiAssistantRequest("那周末呢", CampusScope.CAMPUS, null, "session-1"));

        assertThat(modelClient.lastGeneratedMessages())
                .extracting(ChatMessage::content)
                .anyMatch(content -> content.contains("那周末呢"))
                .contains("图书馆几点开门");
        assertThat(modelClient.lastGeneratedMessages())
                .extracting(ChatMessage::role)
                .containsSequence(ChatMessage.Role.SYSTEM, ChatMessage.Role.USER,
                        ChatMessage.Role.ASSISTANT, ChatMessage.Role.USER);
    }

    @Test
    void keepsSingleTurnPathUnchangedWhenSessionIdIsAbsent() {
        ChatSessionStore store = mock(ChatSessionStore.class);
        AiProperties properties = new AiProperties();
        MockAiModelClient localModelClient = new MockAiModelClient();
        AiAssistantService singleTurnService = new AiAssistantService(
                currentUserService, userMapper, schoolService, postRetriever,
                new PromptBuilder(), localModelClient,
                new AiRequestRateLimiter(properties, (userId, limit) -> true), store,
                new SlidingWindowChatContextCompressor(properties), agentOrchestrator(localModelClient, properties),
                new InMemoryChatSessionLockManager());

        singleTurnService.ask("Bearer token", new AiAssistantRequest("图书馆几点开门", CampusScope.CAMPUS, null, null));

        assertThat(localModelClient.lastGeneratedMessages())
                .extracting(ChatMessage::role)
                .contains(ChatMessage.Role.SYSTEM, ChatMessage.Role.USER,
                        ChatMessage.Role.ASSISTANT, ChatMessage.Role.TOOL);
        verifyNoInteractions(store);
    }

    @Test
    void streamsInMultipleChunksAndKeepsSessionHistory() throws Exception {
        List<String> firstChunks = new ArrayList<>();
        service.stream("Bearer token", new AiAssistantRequest("图书馆几点开门", CampusScope.CAMPUS, null, "stream-1"),
                firstChunks::add);
        service.stream("Bearer token", new AiAssistantRequest("那周末呢", CampusScope.CAMPUS, null, "stream-1"),
                chunk -> { });

        assertThat(firstChunks).hasSizeGreaterThan(1);
        assertThat(modelClient.lastGeneratedMessages())
                .extracting(ChatMessage::role)
                .contains(ChatMessage.Role.TOOL);
        assertThat(modelClient.toolRequestHistory()).hasSize(4);
        assertThat(modelClient.lastGeneratedMessages())
                .extracting(ChatMessage::content)
                .contains("图书馆几点开门");
    }

    @Test
    void replacesUngroundedAnswersWithAnEvidenceMessageForBothResponseModes() throws Exception {
        when(postRetriever.retrieve(any())).thenReturn(List.of());

        AiAssistantResponse response = service.ask("Bearer token",
                new AiAssistantRequest("library closing time", CampusScope.CAMPUS, null, "evidence-1"));
        List<String> chunks = new ArrayList<>();
        AiAssistantResponse streamedResponse = service.stream("Bearer token",
                new AiAssistantRequest("library closing time", CampusScope.CAMPUS, null, "evidence-2"), chunks::add);

        assertThat(response.answer()).isEqualTo(AiAssistantService.INSUFFICIENT_EVIDENCE_MESSAGE);
        assertThat(response.insufficientEvidence()).isTrue();
        assertThat(streamedResponse.answer()).isEqualTo(AiAssistantService.INSUFFICIENT_EVIDENCE_MESSAGE);
        assertThat(chunks).containsExactly(AiAssistantService.INSUFFICIENT_EVIDENCE_MESSAGE);
    }

    private UserProfile userProfile() {
        return new UserProfile(7L, "student", "学生", 10L, 1L,
                "示例大学", "示例校区", "示例城市", null, null, 0, 1,
                true, null, null);
    }

    private AgentOrchestrator agentOrchestrator(AiModelClient client, AiProperties properties) {
        ToolRegistry registry = new ToolRegistry(List.of(new SearchPostsTool(postRetriever, schoolService)));
        ToolCallExecutor executor = new ToolCallExecutor(registry, new ToolSecurityValidator(new ObjectMapper()));
        return new AgentOrchestrator(client, registry, executor, properties);
    }
}
