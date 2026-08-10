package com.campuscircle.post;

import com.campuscircle.auth.CurrentUserService;
import com.campuscircle.common.AfterCommitExecutor;
import com.campuscircle.common.CursorPageResponse;
import com.campuscircle.common.ErrorCode;
import com.campuscircle.common.PageResponse;
import com.campuscircle.exception.BusinessException;
import com.campuscircle.event.DomainEventPublisher;
import com.campuscircle.event.PostSearchIndexEvent;
import com.campuscircle.question.QuestionSourceCleanupService;
import com.campuscircle.school.CampusScope;
import com.campuscircle.school.SchoolService;
import com.campuscircle.user.UserProfile;
import com.campuscircle.user.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PostService {

    private final CurrentUserService currentUserService;
    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final SchoolService schoolService;
    private final HotPostRankStore hotPostRankStore;
    private final ViewCountService viewCountService;
    private final AfterCommitExecutor afterCommitExecutor;
    private final DomainEventPublisher domainEventPublisher;
    private final QuestionSourceCleanupService questionSourceCleanupService;

    public PostService(CurrentUserService currentUserService, PostMapper postMapper, UserMapper userMapper,
                       SchoolService schoolService, HotPostRankStore hotPostRankStore, ViewCountService viewCountService,
                       AfterCommitExecutor afterCommitExecutor, DomainEventPublisher domainEventPublisher,
                       QuestionSourceCleanupService questionSourceCleanupService) {
        this.currentUserService = currentUserService;
        this.postMapper = postMapper;
        this.userMapper = userMapper;
        this.schoolService = schoolService;
        this.hotPostRankStore = hotPostRankStore;
        this.viewCountService = viewCountService;
        this.afterCommitExecutor = afterCommitExecutor;
        this.domainEventPublisher = domainEventPublisher;
        this.questionSourceCleanupService = questionSourceCleanupService;
    }

    @Transactional
    public CreatePostResponse createPost(String authorization, CreatePostRequest request) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        UserProfile currentUser = findExistingUser(currentUserId);
        ensureEnabledCategory(request.categoryId());

        Long postId = postMapper.savePost(currentUserId, currentUser.requireSchoolId(), request);
        if (postId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "帖子创建失败");
        }

        postMapper.savePostStat(postId);
        afterCommitExecutor.execute(() -> domainEventPublisher.publishPostSearchIndex(PostSearchIndexEvent.forPost(postId)));
        return new CreatePostResponse(postId);
    }

    public PageResponse<PostListItemResponse> listPosts(String authorization, int page, int size,
                                                         CampusScope scope, Long categoryId, String keyword, String sort) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        UserProfile currentUser = findExistingUser(currentUserId);
        if (categoryId != null) {
            ensureEnabledCategory(categoryId);
        }

        List<Long> schoolIds = schoolService.listScopeSchoolIds(currentUser.requireSchoolId(), scope);
        PageQueryResult<PostListItem> result = postMapper.findPostsBySchoolIds(
                schoolIds, page, size, categoryId, keyword, sort);
        List<PostListItemResponse> records = result.records().stream()
                .map(PostListItemResponse::from)
                .toList();
        return PageResponse.of(page, size, result.total(), records);
    }

    public PageResponse<PostListItemResponse> listNearbyFeed(String authorization, int page, int size,
                                                             CampusScope scope, Long categoryId, String sort) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        UserProfile currentUser = findExistingUser(currentUserId);
        if (categoryId != null) {
            ensureEnabledCategory(categoryId);
        }

        List<Long> schoolIds = schoolService.listScopeSchoolIds(currentUser.requireSchoolId(), scope);
        PageQueryResult<PostListItem> result = postMapper.findPostsBySchoolIds(schoolIds, page, size, categoryId, null, sort);
        List<PostListItemResponse> records = result.records().stream()
                .map(PostListItemResponse::from)
                .toList();
        return PageResponse.of(page, size, result.total(), records);
    }

    public CursorPageResponse<PostListItemResponse> listNearbyFeedByCursor(
            String authorization, int size, CampusScope scope, Long categoryId, String cursor) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        UserProfile currentUser = findExistingUser(currentUserId);
        if (categoryId != null) {
            ensureEnabledCategory(categoryId);
        }

        FeedCursor feedCursor = cursor == null || cursor.isBlank() ? null : FeedCursorCodec.decode(cursor);
        List<Long> schoolIds = schoolService.listScopeSchoolIds(currentUser.requireSchoolId(), scope);
        List<PostListItem> fetchedRecords = postMapper.findPostsBySchoolIdsAfterCursor(
                schoolIds, size + 1, categoryId, feedCursor);

        boolean hasMore = fetchedRecords.size() > size;
        List<PostListItem> pageItems = hasMore ? fetchedRecords.subList(0, size) : fetchedRecords;
        List<PostListItemResponse> records = pageItems.stream()
                .map(PostListItemResponse::from)
                .toList();

        String nextCursor = null;
        if (hasMore) {
            PostListItem lastItem = pageItems.get(pageItems.size() - 1);
            nextCursor = FeedCursorCodec.encode(new FeedCursor(lastItem.createdAt(), lastItem.id()));
        }
        return new CursorPageResponse<>(records, nextCursor, hasMore);
    }

    public PostDetailResponse getPostDetail(Long postId, String authorization, double radiusKm) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        PostDetail postDetail = findNormalPost(postId);
        viewCountService.recordView(postId, postDetail.categoryId());

        boolean liked = postMapper.existsLike(postId, currentUserId);
        return PostDetailResponse.from(postDetail, liked);
    }

    public PageResponse<PostListItemResponse> listUserPosts(String authorization, Long userId, int page, int size,
                                                             double radiusKm) {
        currentUserService.requireUserId(authorization);
        findExistingUser(userId);
        PageQueryResult<PostListItem> result = postMapper.findPostsByUserId(userId, page, size);
        List<PostListItemResponse> records = result.records().stream()
                .map(PostListItemResponse::from)
                .toList();
        return PageResponse.of(page, size, result.total(), records);
    }

    public List<PostHotItemResponse> listHotPosts(String authorization, int limit, CampusScope scope, Long categoryId) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        UserProfile currentUser = findExistingUser(currentUserId);
        if (categoryId != null) {
            ensureEnabledCategory(categoryId);
        }

        List<Long> schoolIds = schoolService.listScopeSchoolIds(currentUser.requireSchoolId(), scope);
        return postMapper.findHotPostsBySchoolIds(schoolIds, limit, categoryId)
                .stream()
                .map(PostHotItemResponse::from)
                .toList();
    }

    @Transactional
    public void updatePost(Long postId, String authorization, UpdatePostRequest request) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        PostDetail postDetail = findNormalPost(postId);
        ensureCanManagePost(currentUserId, postDetail.userId());
        ensureEnabledCategory(request.categoryId());
        postMapper.updatePost(postId, request);
        afterCommitExecutor.execute(() -> hotPostRankStore.moveCategory(
                postId, postDetail.categoryId(), request.categoryId(), postDetail.hotScore()
        ));
        afterCommitExecutor.execute(() -> domainEventPublisher.publishPostSearchIndex(PostSearchIndexEvent.forPost(postId)));
    }

    @Transactional
    public void deletePost(Long postId, String authorization) {
        Long currentUserId = currentUserService.requireUserId(authorization);
        PostDetail postDetail = findNormalPost(postId);
        ensureCanManagePost(currentUserId, postDetail.userId());
        postMapper.softDeletePost(postId);
        questionSourceCleanupService.deleteByPostId(postId, currentUserId);
        afterCommitExecutor.execute(() -> hotPostRankStore.removePost(postId, postDetail.categoryId()));
        afterCommitExecutor.execute(() -> domainEventPublisher.publishPostSearchIndex(PostSearchIndexEvent.forPost(postId)));
    }

    private PostDetail findNormalPost(Long postId) {
        PostDetail postDetail = postMapper.findDetailById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在"));

        if (postDetail.status() != 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在");
        }

        return postDetail;
    }

    private void ensureEnabledCategory(Long categoryId) {
        if (!postMapper.existsEnabledCategory(categoryId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分类不存在或已禁用");
        }
    }

    private UserProfile findExistingUser(Long userId) {
        return userMapper.findProfileById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
    }

    private void ensureCanManagePost(Long currentUserId, Long postAuthorId) {
        if (currentUserId.equals(postAuthorId)) {
            return;
        }

        UserProfile currentUser = userMapper.findProfileById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        if (currentUser.role() != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能操作自己的帖子");
        }
    }
}
