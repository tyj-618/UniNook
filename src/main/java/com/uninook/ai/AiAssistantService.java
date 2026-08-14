package com.uninook.ai;

import com.uninook.auth.CurrentUserService;
import com.uninook.common.ErrorCode;
import com.uninook.exception.BusinessException;
import com.uninook.school.CampusScope;
import com.uninook.school.SchoolService;
import com.uninook.user.UserMapper;
import com.uninook.user.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiAssistantService {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantService.class);
    private static final int RETRIEVAL_LIMIT = 5;
    private final CurrentUserService currentUserService;
    private final UserMapper userMapper;
    private final SchoolService schoolService;
    private final PostRetriever postRetriever;
    private final PromptBuilder promptBuilder;
    private final AiModelClient aiModelClient;
    private final AiRequestRateLimiter aiRequestRateLimiter;
    private final ChatSessionStore chatSessionStore;
    private final ChatContextCompressor chatContextCompressor;
    private final AgentOrchestrator agentOrchestrator;

    public AiAssistantService(CurrentUserService currentUserService, UserMapper userMapper,
                              SchoolService schoolService, PostRetriever postRetriever,
                              PromptBuilder promptBuilder, AiModelClient aiModelClient,
                              AiRequestRateLimiter aiRequestRateLimiter, ChatSessionStore chatSessionStore,
                              ChatContextCompressor chatContextCompressor, AgentOrchestrator agentOrchestrator) {
        this.currentUserService = currentUserService;
        this.userMapper = userMapper;
        this.schoolService = schoolService;
        this.postRetriever = postRetriever;
        this.promptBuilder = promptBuilder;
        this.aiModelClient = aiModelClient;
        this.aiRequestRateLimiter = aiRequestRateLimiter;
        this.chatSessionStore = chatSessionStore;
        this.chatContextCompressor = chatContextCompressor;
        this.agentOrchestrator = agentOrchestrator;
    }

    public AiAssistantResponse ask(String authorization, AiAssistantRequest request) {
        String requestId = AiRequestContext.begin(null);
        try {
            log.info("assistant requestId={} mode=agent sessionPresent={}", requestId,
                    request.sessionId() != null && !request.sessionId().isBlank());
            return askInternal(authorization, request, requestId);
        } finally {
            AiRequestContext.clear();
        }
    }

    private AiAssistantResponse askInternal(String authorization, AiAssistantRequest request, String requestId) {
        Long userId = currentUserService.requireUserId(authorization);
        aiRequestRateLimiter.check(userId);
        UserProfile user = userMapper.findProfileById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));

        List<ChatMessage> history = loadHistory(userId, request.sessionId());
        CampusScope scope = CampusScope.resolve(request.scope(), request.radiusKm());
        AgentResult result = agentOrchestrator.run(promptBuilder.buildAgent(request.question(), history).messages(),
                new ToolExecutionContext(userId, user, scope));
        AiAssistantResponse response = new AiAssistantResponse(
                result.answer(),
                result.references(),
                !result.pendingConfirmation() && result.references().isEmpty(),
                requestId);
        saveHistory(userId, request.sessionId(), history, request.question(), response.answer());
        log.info("assistant requestId={} stage=response references={} pendingConfirmation={}", requestId,
                response.references().size(), result.pendingConfirmation());
        return response;
    }

    public AiAssistantResponse stream(String authorization, AiAssistantRequest request,
                                      AiStreamChunkConsumer chunkConsumer) throws java.io.IOException {
        String requestId = AiRequestContext.begin(null);
        try {
            return streamInternal(authorization, request, chunkConsumer, requestId);
        } finally {
            AiRequestContext.clear();
        }
    }

    private AiAssistantResponse streamInternal(String authorization, AiAssistantRequest request,
                                               AiStreamChunkConsumer chunkConsumer, String requestId) throws java.io.IOException {
        Long userId = currentUserService.requireUserId(authorization);
        aiRequestRateLimiter.check(userId);
        UserProfile user = userMapper.findProfileById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));

        CampusScope scope = CampusScope.resolve(request.scope(), request.radiusKm());
        List<Long> allowedSchoolIds = schoolService.listScopeSchoolIds(user.schoolId(), scope);
        List<RetrievedPost> posts = postRetriever.retrieve(
                new RetrievalQuery(request.question(), allowedSchoolIds, RETRIEVAL_LIMIT));
        List<ChatMessage> history = loadHistory(userId, request.sessionId());
        if (posts.isEmpty()) {
            String answer = "在当前查看范围内暂未找到相关校园帖子。";
            chunkConsumer.accept(answer);
            AiAssistantResponse response = new AiAssistantResponse(answer, List.of(), true, requestId);
            saveHistory(userId, request.sessionId(), history, request.question(), response.answer());
            return response;
        }

        StringBuilder answer = new StringBuilder();
        aiModelClient.generateStream(promptBuilder.buildStreaming(request.question(), posts, history), chunk -> {
            answer.append(chunk);
            chunkConsumer.accept(chunk);
        });
        if (answer.toString().isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "智能问答服务未返回有效内容");
        }
        List<AiPostReference> references = posts.stream()
                .limit(3)
                .map(AiPostReference::from)
                .toList();
        AiAssistantResponse response = new AiAssistantResponse(
                answer.toString().trim(), references, references.isEmpty(), requestId);
        saveHistory(userId, request.sessionId(), history, request.question(), response.answer());
        log.info("assistant requestId={} stage=stream-response references={}", requestId, references.size());
        return response;
    }

    private List<ChatMessage> loadHistory(Long userId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        return chatContextCompressor.compress(chatSessionStore.load(userId, sessionId));
    }

    private void saveHistory(Long userId, String sessionId, List<ChatMessage> history,
                             String question, String answer) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        List<ChatMessage> updated = new java.util.ArrayList<>(history);
        updated.add(new ChatMessage(ChatMessage.Role.USER, question));
        updated.add(new ChatMessage(ChatMessage.Role.ASSISTANT, answer));
        chatSessionStore.save(userId, sessionId, chatContextCompressor.compress(updated));
    }
}
