package com.uninook.ai;

import com.uninook.auth.CurrentUserService;
import com.uninook.common.ErrorCode;
import com.uninook.exception.BusinessException;
import com.uninook.school.CampusScope;
import com.uninook.school.SchoolService;
import com.uninook.user.UserMapper;
import com.uninook.user.UserProfile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AiAssistantService {

    private static final int RETRIEVAL_LIMIT = 5;
    private final CurrentUserService currentUserService;
    private final UserMapper userMapper;
    private final SchoolService schoolService;
    private final PostRetriever postRetriever;
    private final PromptBuilder promptBuilder;
    private final AiModelClient aiModelClient;
    private final AiRequestRateLimiter aiRequestRateLimiter;

    public AiAssistantService(CurrentUserService currentUserService, UserMapper userMapper,
                              SchoolService schoolService, PostRetriever postRetriever,
                              PromptBuilder promptBuilder, AiModelClient aiModelClient,
                              AiRequestRateLimiter aiRequestRateLimiter) {
        this.currentUserService = currentUserService;
        this.userMapper = userMapper;
        this.schoolService = schoolService;
        this.postRetriever = postRetriever;
        this.promptBuilder = promptBuilder;
        this.aiModelClient = aiModelClient;
        this.aiRequestRateLimiter = aiRequestRateLimiter;
    }

    public AiAssistantResponse ask(String authorization, AiAssistantRequest request) {
        Long userId = currentUserService.requireUserId(authorization);
        aiRequestRateLimiter.check(userId);
        UserProfile user = userMapper.findProfileById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));

        CampusScope scope = CampusScope.resolve(request.scope(), request.radiusKm());
        List<Long> allowedSchoolIds = schoolService.listScopeSchoolIds(user.schoolId(), scope);
        List<RetrievedPost> posts = postRetriever.retrieve(
                new RetrievalQuery(request.question(), allowedSchoolIds, RETRIEVAL_LIMIT));
        if (posts.isEmpty()) {
            return new AiAssistantResponse(
                    "在当前查看范围内暂未找到相关校园帖子。",
                    List.of(), true, UUID.randomUUID().toString());
        }

        AiModelResult result = aiModelClient.generate(promptBuilder.build(request.question(), posts));
        Map<Long, RetrievedPost> postsById = posts.stream()
                .collect(Collectors.toMap(RetrievedPost::id, Function.identity()));
        Set<Long> validPostIds = result.citedPostIds().stream()
                .filter(postsById::containsKey)
                .collect(Collectors.toSet());
        List<AiPostReference> references = posts.stream()
                .filter(post -> validPostIds.contains(post.id()))
                .map(AiPostReference::from)
                .toList();

        return new AiAssistantResponse(
                result.answer(),
                references,
                result.insufficientEvidence() || references.isEmpty(),
                result.requestId());
    }
}
