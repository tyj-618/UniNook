package com.uninook.ai;

import com.uninook.auth.CurrentUserService;
import com.uninook.common.ErrorCode;
import com.uninook.exception.BusinessException;
import com.uninook.school.CampusScope;
import com.uninook.school.SchoolService;
import com.uninook.user.UserMapper;
import com.uninook.user.UserProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class AiAssistantService {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantService.class);
    static final String INSUFFICIENT_EVIDENCE_MESSAGE = "当前范围内没有足够的帖子可作为可靠依据。";
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
    private final ChatSessionLockManager chatSessionLockManager;
    private final AiOperationalMetrics metrics;

    public AiAssistantService(CurrentUserService currentUserService, UserMapper userMapper,
                              SchoolService schoolService, PostRetriever postRetriever,
                              PromptBuilder promptBuilder, AiModelClient aiModelClient,
                              AiRequestRateLimiter aiRequestRateLimiter, ChatSessionStore chatSessionStore,
                              ChatContextCompressor chatContextCompressor, AgentOrchestrator agentOrchestrator,
                              ChatSessionLockManager chatSessionLockManager) {
        this(currentUserService, userMapper, schoolService, postRetriever, promptBuilder, aiModelClient,
                aiRequestRateLimiter, chatSessionStore, chatContextCompressor, agentOrchestrator,
                chatSessionLockManager, AiOperationalMetrics.noOp());
    }

    @Autowired
    public AiAssistantService(CurrentUserService currentUserService, UserMapper userMapper,
                              SchoolService schoolService, PostRetriever postRetriever,
                              PromptBuilder promptBuilder, AiModelClient aiModelClient,
                              AiRequestRateLimiter aiRequestRateLimiter, ChatSessionStore chatSessionStore,
                              ChatContextCompressor chatContextCompressor, AgentOrchestrator agentOrchestrator,
                              ChatSessionLockManager chatSessionLockManager, AiOperationalMetrics metrics) {
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
        this.chatSessionLockManager = chatSessionLockManager;
        this.metrics = metrics;
    }

    public AiAssistantResponse ask(String authorization, AiAssistantRequest request) {
        String requestId = AiRequestContext.begin(null);
        try {
            log.info("assistant requestId={} mode=agent sessionPresent={}", requestId,
                    request.sessionId() != null && !request.sessionId().isBlank());
            Long userId = currentUserService.requireUserId(authorization);
            try (ChatSessionLockManager.SessionLock ignored = chatSessionLockManager.acquire(userId, request.sessionId())) {
                return askInternal(userId, request, requestId);
            }
        } finally {
            AiRequestContext.clear();
        }
    }

    private AiAssistantResponse askInternal(Long userId, AiAssistantRequest request, String requestId) {
        aiRequestRateLimiter.check(userId);
        UserProfile user = userMapper.findProfileById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        List<ChatMessage> history = loadHistory(userId, request.sessionId());
        CampusScope scope = CampusScope.resolve(request.scope(), request.radiusKm());
        AgentResult result = agentOrchestrator.run(promptBuilder.buildAgent(request.question(), history).messages(),
                new ToolExecutionContext(userId, user, scope));
        String answer = groundedAnswer(result.answer(), result.references(), result.pendingConfirmation());
        AiAssistantResponse response = new AiAssistantResponse(
                answer, result.references(),
                !result.pendingConfirmation() && result.references().isEmpty(), requestId, result.pendingAction());
        saveHistory(userId, request.sessionId(), history, request.question(), response.answer());
        log.info("assistant requestId={} stage=response references={} pendingConfirmation={}", requestId,
                response.references().size(), result.pendingConfirmation());
        return response;
    }

    public AiAssistantResponse stream(String authorization, AiAssistantRequest request,
                                      AiStreamChunkConsumer chunkConsumer) throws IOException {
        String requestId = AiRequestContext.begin(null);
        try {
            Long userId = currentUserService.requireUserId(authorization);
            try (ChatSessionLockManager.SessionLock ignored = chatSessionLockManager.acquire(userId, request.sessionId())) {
                return streamInternal(userId, request, chunkConsumer, requestId);
            }
        } finally {
            AiRequestContext.clear();
        }
    }

    private AiAssistantResponse streamInternal(Long userId, AiAssistantRequest request,
                                               AiStreamChunkConsumer chunkConsumer, String requestId) throws IOException {
        aiRequestRateLimiter.check(userId);
        UserProfile user = userMapper.findProfileById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        List<ChatMessage> history = loadHistory(userId, request.sessionId());
        CampusScope scope = CampusScope.resolve(request.scope(), request.radiusKm());
        AgentStreamingPlan plan = agentOrchestrator.prepareForStreaming(
                promptBuilder.buildAgent(request.question(), history).messages(),
                new ToolExecutionContext(userId, user, scope));
        boolean insufficientEvidence = !plan.pendingConfirmation() && plan.references().isEmpty();
        chunkConsumer.acceptMetadata(new AiAssistantStreamMetadata(
                plan.references(), insufficientEvidence, plan.pendingAction()));

        String answer;
        if (insufficientEvidence) {
            answer = INSUFFICIENT_EVIDENCE_MESSAGE;
            chunkConsumer.accept(answer);
        } else if (plan.requiresModelStream()) {
            StringBuilder generated = new StringBuilder();
            long streamStartedAt = System.currentTimeMillis();
            try {
                aiModelClient.generateStream(plan.finalMessages(), chunk -> {
                    generated.append(chunk);
                    chunkConsumer.accept(chunk);
                });
                metrics.recordModelCall("stream", "success", System.currentTimeMillis() - streamStartedAt, null, null);
            } catch (IOException | BusinessException exception) {
                metrics.recordModelCall("stream", "failed", System.currentTimeMillis() - streamStartedAt, null, null);
                throw exception;
            }
            answer = generated.toString().trim();
            if (answer.isBlank()) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "智能问答服务未返回有效内容");
            }
        } else {
            answer = plan.immediateAnswer();
            chunkConsumer.accept(answer);
        }

        AiAssistantResponse response = new AiAssistantResponse(
                answer, plan.references(), insufficientEvidence, requestId,
                plan.pendingAction());
        saveHistory(userId, request.sessionId(), history, request.question(), response.answer());
        log.info("assistant requestId={} stage=stream-response references={} pendingConfirmation={} modelStream={}",
                requestId, response.references().size(), plan.pendingConfirmation(), plan.requiresModelStream());
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
        List<ChatMessage> updated = new ArrayList<>(history);
        updated.add(new ChatMessage(ChatMessage.Role.USER, question));
        updated.add(new ChatMessage(ChatMessage.Role.ASSISTANT, answer));
        chatSessionStore.save(userId, sessionId, chatContextCompressor.compress(updated));
    }

    private String groundedAnswer(String answer, List<AiPostReference> references, boolean pendingConfirmation) {
        if (!pendingConfirmation && references.isEmpty()) {
            return INSUFFICIENT_EVIDENCE_MESSAGE;
        }
        return answer;
    }
}
