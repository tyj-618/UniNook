package com.uninook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.URLEncoder;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UniNookApiIntegrationTests {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void campusCircleCoreApiFlow() throws Exception {
        register("alice", "123456", "小艾");
        register("bob", "123456", "小林");

        String aliceToken = login("alice", "123456");
        String bobToken = login("bob", "123456");
        JsonNode aliceProfile = get("/api/users/me", aliceToken);
        JsonNode bobProfile = get("/api/users/me", bobToken);

        Long categoryId = firstCategoryId();
        Long postId = createPost(aliceToken, categoryId);
        assertLocationFeed(aliceToken, postId);
        JsonNode postDetail = get("/api/posts/" + postId + "?radiusKm=30", aliceToken);
        assertThat(postDetail.at("/code").asInt()).isZero();
        assertThat(postDetail.at("/data/id").asLong()).isEqualTo(postId);
        Long commentId = createComment(bobToken, postId);

        likePost(bobToken, postId);

        JsonNode comments = get("/api/posts/" + postId + "/comments", bobToken);
        assertThat(comments.at("/code").asInt()).isZero();
        assertThat(comments.at("/data/total").asLong()).isEqualTo(1);
        assertThat(comments.at("/data/records/0/id").asLong()).isEqualTo(commentId);
        assertThat(comments.at("/data/records/0/author/id").asLong())
                .isEqualTo(bobProfile.at("/data/id").asLong());
        assertThat(comments.at("/data/records/0/author/nickname").asText())
                .isEqualTo(bobProfile.at("/data/nickname").asText());

        JsonNode sessionMismatch = post(
                "/api/posts/" + postId + "/comments",
                bobToken,
                Map.of("content", "This comment must be rejected because the client identity is stale."),
                Map.of("X-UniNook-User-Id", aliceProfile.at("/data/id").asText())
        );
        assertCode(sessionMismatch, 40900);
        assertThat(get("/api/posts/" + postId + "/comments", bobToken).at("/data/total").asLong()).isEqualTo(1);

        JsonNode likeStatus = get("/api/posts/" + postId + "/like", bobToken);
        assertThat(likeStatus.at("/code").asInt()).isZero();
        assertThat(likeStatus.at("/data/liked").asBoolean()).isTrue();

        JsonNode unreadCount = get("/api/notices/unread-count", aliceToken);
        assertThat(unreadCount.at("/code").asInt()).isZero();
        assertThat(unreadCount.at("/data/count").asLong()).isEqualTo(2);

        JsonNode notices = get("/api/notices", aliceToken);
        assertThat(notices.at("/code").asInt()).isZero();
        assertThat(notices.at("/data/total").asLong()).isEqualTo(2);
    }

    @Test
    void registrationRequiresNicknameConfirmationBeforeEnteringTheApplication() throws Exception {
        String username = "nickname_" + System.nanoTime();
        JsonNode registration = post("/api/auth/register", null, Map.of(
                "username", username,
                "password", "123456"
        ));
        assertCode(registration, 0);
        assertThat(registration.at("/data/nickname").asText()).startsWith("CampusUser_");

        String token = login(username, "123456");
        JsonNode profile = get("/api/users/me", token);
        assertCode(profile, 0);
        assertThat(profile.at("/data/nicknameSetupRequired").asBoolean()).isTrue();

        JsonNode updated = put("/api/users/me", token, Map.of("nickname", "新用户"));
        assertCode(updated, 0);
        assertThat(updated.at("/data/nicknameSetupRequired").asBoolean()).isFalse();
    }

    @Test
    void campusCircleCoreApiBoundaryFlow() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String authorUsername = "author_" + suffix;
        String readerUsername = "reader_" + suffix;

        Long categoryId = firstCategoryId();

        JsonNode unauthorizedCreatePost = post("/api/posts", null, Map.of(
                "categoryId", categoryId,
                "title", "No token post",
                "content", "This request should be rejected."
        ));
        assertCode(unauthorizedCreatePost, 40100);

        register(authorUsername, "123456", "Author");
        JsonNode duplicateRegister = post("/api/auth/register", null, Map.of(
                "username", authorUsername,
                "password", "123456",
                "nickname", "Author Again"
        ));
        assertCode(duplicateRegister, 40901);

        String authorToken = login(authorUsername, "123456");
        register(readerUsername, "123456", "Reader");
        String readerToken = login(readerUsername, "123456");

        Long postId = createPost(authorToken, categoryId);

        JsonNode invalidPage = get("/api/posts?page=0", null);
        assertCode(invalidPage, 40000);

        JsonNode forbiddenAdmin = put("/api/admin/posts/" + postId + "/hide", readerToken, null);
        assertCode(forbiddenAdmin, 40300);

        JsonNode firstLike = post("/api/posts/" + postId + "/like", readerToken, null);
        assertCode(firstLike, 0);
        assertThat(firstLike.at("/data/likeCount").asInt()).isEqualTo(1);

        JsonNode duplicateLike = post("/api/posts/" + postId + "/like", readerToken, null);
        assertCode(duplicateLike, 0);
        assertThat(duplicateLike.at("/data/likeCount").asInt()).isEqualTo(1);

        JsonNode firstUnlike = delete("/api/posts/" + postId + "/like", readerToken);
        assertCode(firstUnlike, 0);
        assertThat(firstUnlike.at("/data/likeCount").asInt()).isEqualTo(0);

        JsonNode duplicateUnlike = delete("/api/posts/" + postId + "/like", readerToken);
        assertCode(duplicateUnlike, 0);
        assertThat(duplicateUnlike.at("/data/likeCount").asInt()).isEqualTo(0);
    }

    @Test
    void nearbyFeedSupportsCursorPagination() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String username = "cursor_" + suffix;
        register(username, "123456", "Cursor User");
        String token = login(username, "123456");
        Long categoryId = firstCategoryId();

        Long firstPostId = createPost(token, categoryId, "Cursor post 1 " + suffix);
        Long secondPostId = createPost(token, categoryId, "Cursor post 2 " + suffix);
        Long thirdPostId = createPost(token, categoryId, "Cursor post 3 " + suffix);

        JsonNode firstPage = get("/api/posts/feed/cursor?radiusKm=30&size=2", token);
        assertThat(firstPage.at("/code").asInt()).isZero();
        assertThat(firstPage.at("/data/records").size()).isEqualTo(2);
        assertThat(firstPage.at("/data/hasMore").asBoolean()).isTrue();
        String nextCursor = firstPage.at("/data/nextCursor").asText();
        assertThat(nextCursor).isNotBlank();

        JsonNode secondPage = get("/api/posts/feed/cursor?radiusKm=30&size=2&cursor=" + nextCursor, token);
        assertThat(secondPage.at("/code").asInt()).isZero();

        Set<Long> firstPageIds = idsOf(firstPage);
        Set<Long> secondPageIds = idsOf(secondPage);
        assertThat(firstPageIds).doesNotContainAnyElementsOf(secondPageIds);
        Set<Long> seenIds = new HashSet<>(firstPageIds);
        seenIds.addAll(secondPageIds);
        assertThat(seenIds)
                .contains(firstPostId, secondPostId, thirdPostId);
    }

    @Test
    void aiAssistantUsesOnlyAuthorizedNearbyPosts() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String username = "assistant_" + suffix;
        String remoteUsername = "remote_assistant_" + suffix;
        String keyword = "studyspace" + suffix;
        register(username, "123456", "Assistant User");
        register(remoteUsername, "123456", "Remote User");
        String token = login(username, "123456");
        String remoteToken = login(remoteUsername, "123456");
        JsonNode remoteProfile = put("/api/users/me", remoteToken, Map.of(
                "nickname", "Remote User",
                "schoolId", 4
        ));
        assertThat(remoteProfile.at("/code").asInt()).isZero();

        Long postId = createPost(token, firstCategoryId(), keyword);
        Long remotePostId = createPost(remoteToken, firstCategoryId(), keyword);
        JsonNode response = post("/api/ai/assistant/ask", token, Map.of(
                "question", keyword,
                "radiusKm", 30
        ));

        assertThat(response.at("/code").asInt()).isZero();
        assertThat(response.at("/data/insufficientEvidence").asBoolean()).isFalse();
        assertThat(response.at("/data/requestId").asText()).isNotBlank();
        assertThat(response.at("/data/references/0/postId").asLong()).isEqualTo(postId);
        for (JsonNode reference : response.at("/data/references")) {
            assertThat(reference.at("/postId").asLong()).isNotEqualTo(remotePostId);
        }

        JsonNode noEvidence = post("/api/ai/assistant/ask", token, Map.of(
                "question", "unmatched" + suffix,
                "radiusKm", 30
        ));
        assertThat(noEvidence.at("/code").asInt()).isZero();
        assertThat(noEvidence.at("/data/insufficientEvidence").asBoolean()).isTrue();
        assertThat(noEvidence.at("/data/references").size()).isZero();

        JsonNode unauthorized = post("/api/ai/assistant/ask", null, Map.of(
                "question", keyword,
                "radiusKm", 30
        ));
        assertCode(unauthorized, 40100);
    }

    @Test
    void aiAssistantStreamEmitsMultipleChunksAndDoneEvent() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String username = "stream_assistant_" + suffix;
        String keyword = "streamspace" + suffix;
        register(username, "123456", "Stream Assistant");
        String token = login(username, "123456");
        createPost(token, firstCategoryId(), keyword);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url("/api/ai/assistant/stream")))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(Map.of(
                        "question", keyword,
                        "radiusKm", 30,
                        "sessionId", "stream-session-" + suffix
                ))))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE)
                .header("Authorization", bearer(token))
                .build();
        HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
        assertThat(response.statusCode()).isEqualTo(200);
        try (Stream<String> lines = response.body()) {
            List<String> events = lines.toList();
            assertThat(events).filteredOn(line -> line.equals("event:message")).hasSizeGreaterThan(1);
            assertThat(events).contains("event:done");
        }
    }

    @Test
    void aiAssistantStreamReturnsPendingPostDraftInDoneEvent() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String username = "stream_pending_" + suffix;
        String title = "Stream pending " + suffix;
        register(username, "123456", "Stream Pending User");
        String token = login(username, "123456");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url("/api/ai/assistant/stream")))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(Map.of(
                        "question", "帮我发布一条帖子，标题是“" + title + "”，内容是“仅用于流式草稿测试”。",
                        "radiusKm", 10,
                        "sessionId", "stream-pending-" + suffix
                ))))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE)
                .header("Authorization", bearer(token))
                .build();
        HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());

        assertThat(response.statusCode()).isEqualTo(200);
        try (Stream<String> lines = response.body()) {
            List<String> events = lines.toList();
            int doneIndex = events.indexOf("event:done");
            assertThat(doneIndex).isGreaterThanOrEqualTo(0);
            JsonNode done = objectMapper.readTree(events.get(doneIndex + 1).substring("data:".length()));
            assertThat(done.at("/pendingAction/type").asText()).isEqualTo("CREATE_POST");
            assertThat(done.at("/pendingAction/actionId").asText()).isNotBlank();
            assertThat(done.at("/pendingAction/title").asText()).isEqualTo(title);
        }
    }

    @Test
    void pendingPostDraftCreatesNothingUntilItIsConfirmed() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String username = "pending_post_" + suffix;
        String title = "Pending post " + suffix;
        register(username, "123456", "Pending Post User");
        String token = login(username, "123456");

        JsonNode draft = post("/api/ai/assistant/ask", token, Map.of(
                "question", "帮我发布一条帖子，标题是“" + title + "”，内容是“仅用于确认流程测试”。",
                "radiusKm", 10
        ));

        assertThat(draft.at("/code").asInt()).isZero();
        String actionId = draft.at("/data/pendingAction/actionId").asText();
        assertThat(actionId).isNotBlank();
        assertThat(draft.at("/data/pendingAction/type").asText()).isEqualTo("CREATE_POST");

        JsonNode confirmed = post("/api/ai/pending-actions/" + actionId + "/confirm", token, Map.of(
                "categoryId", firstCategoryId()
        ));
        assertThat(confirmed.at("/code").asInt()).isZero();
        Long postId = confirmed.at("/data/postId").asLong();
        assertThat(postId).isPositive();
        assertThat(get("/api/posts/" + postId + "?radiusKm=10", token).at("/data/title").asText()).isEqualTo(title);

        JsonNode repeatedConfirmation = post("/api/ai/pending-actions/" + actionId + "/confirm", token, Map.of(
                "categoryId", firstCategoryId()
        ));
        assertCode(repeatedConfirmation, 40400);
    }

    @Test
    void directPostInteractionsAllowPostsOutsideRecommendationScope() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String localUsername = "local_" + suffix;
        String remoteUsername = "remote_" + suffix;
        register(localUsername, "123456", "Local User");
        register(remoteUsername, "123456", "Remote User");

        String localToken = login(localUsername, "123456");
        String remoteToken = login(remoteUsername, "123456");
        JsonNode remoteProfile = put("/api/users/me", remoteToken, Map.of(
                "nickname", "Remote User",
                "schoolId", 4
        ));
        assertThat(remoteProfile.at("/code").asInt()).isZero();

        Long remotePostId = createPost(remoteToken, firstCategoryId(), "Remote post " + suffix);
        JsonNode feed = get("/api/posts/feed?radiusKm=30", localToken);
        assertCode(feed, 0);
        assertThat(feed.at("/data/records")).noneMatch(item -> item.at("/id").asLong() == remotePostId);

        assertCode(get("/api/posts/" + remotePostId + "?radiusKm=30", localToken), 0);
        assertCode(get("/api/posts/" + remotePostId + "/comments?radiusKm=30", localToken), 0);
        assertCode(post("/api/posts/" + remotePostId + "/comments?radiusKm=30", localToken, Map.of(
                "content", "Known posts remain available for normal interaction."
        )), 0);
        assertCode(post("/api/posts/" + remotePostId + "/like?radiusKm=30", localToken, null), 0);
    }

    @Test
    void userHomeEndpointsExposeInteractionRecordsAndStableSchoolInformation() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String authorUsername = "pa_" + suffix;
        String commenterUsername = "pc_" + suffix;
        String observerUsername = "po_" + suffix;
        register(authorUsername, "123456", "Profile Author");
        register(commenterUsername, "123456", "Profile Commenter");
        register(observerUsername, "123456", "Profile Observer");

        String authorToken = login(authorUsername, "123456");
        String commenterToken = login(commenterUsername, "123456");
        String observerToken = login(observerUsername, "123456");
        long authorId = get("/api/users/me", authorToken).at("/data/id").asLong();
        Long postId = createPost(authorToken, firstCategoryId(), "Profile home " + suffix);
        long rootCommentId = post("/api/posts/" + postId + "/comments", commenterToken,
                Map.of("content", "A root comment for the profile page.")).at("/data/commentId").asLong();
        long threadReplyId = post("/api/posts/" + postId + "/comments", authorToken,
                Map.of("content", "A follow-up comment in the same thread.", "parentCommentId", rootCommentId))
                .at("/data/commentId").asLong();
        long replyCommentId = post("/api/posts/" + postId + "/comments", observerToken,
                Map.of("content", "A nested reply with a target user.", "parentCommentId", threadReplyId))
                .at("/data/commentId").asLong();
        assertCode(post("/api/posts/" + postId + "/like", commenterToken, null), 0);
        assertCode(post("/api/comments/" + rootCommentId + "/like", observerToken, null), 0);

        JsonNode publicProfile = get("/api/users/" + authorId, observerToken);
        assertCode(publicProfile, 0);
        assertThat(publicProfile.at("/data/postCount").asLong()).isEqualTo(1L);
        assertThat(publicProfile.at("/data/commentCount").asLong()).isEqualTo(1L);
        assertThat(get("/api/users/" + authorId + "/posts", observerToken).at("/data/records/0/id").asLong())
                .isEqualTo(postId);

        JsonNode comments = get("/api/posts/" + postId + "/comments", observerToken);
        JsonNode reply = null;
        for (JsonNode item : comments.at("/data/records")) {
            if (item.at("/id").asLong() == replyCommentId) {
                reply = item;
                break;
            }
        }
        assertThat(reply).isNotNull();
        assertThat(reply.at("/replyToUserId").asLong())
                .isEqualTo(authorId);

        JsonNode myComments = get("/api/users/me/comments", commenterToken);
        assertCode(myComments, 0);
        assertThat(myComments.at("/data/records/0/postId").asLong()).isEqualTo(postId);
        assertThat(myComments.at("/data/records/0/id").asLong()).isEqualTo(rootCommentId);

        JsonNode postLikes = get("/api/users/me/likes", commenterToken);
        assertCode(postLikes, 0);
        assertThat(postLikes.at("/data/records/0/targetType").asText()).isEqualTo("POST");
        JsonNode commentLikes = get("/api/users/me/likes", observerToken);
        assertCode(commentLikes, 0);
        assertThat(commentLikes.at("/data/records/0/targetType").asText()).isEqualTo("COMMENT");
        assertThat(commentLikes.at("/data/records/0/commentId").asLong()).isEqualTo(rootCommentId);

        JsonNode updated = put("/api/users/me", authorToken, Map.of(
                "nickname", "Profile Author"
        ));
        assertCode(updated, 0);
        assertThat(updated.at("/data/schoolId").asLong()).isEqualTo(1L);
        JsonNode publicProfileAfterUpdate = get("/api/users/" + authorId, observerToken);
        assertThat(publicProfileAfterUpdate.at("/data/schoolId").asLong()).isEqualTo(1L);
        assertThat(publicProfileAfterUpdate.at("/data/schoolName").asText()).isNotBlank();
    }

    @Test
    void followUpNotifiesOnlyTheTargetCommentAuthor() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String postAuthorUsername = "post_author_" + suffix;
        String commentAuthorUsername = "comment_author_" + suffix;
        String followerUsername = "follower_" + suffix;
        register(postAuthorUsername, "123456", "Post Author");
        register(commentAuthorUsername, "123456", "Comment Author");
        register(followerUsername, "123456", "Follower");
        String postAuthorToken = login(postAuthorUsername, "123456");
        String commentAuthorToken = login(commentAuthorUsername, "123456");
        String followerToken = login(followerUsername, "123456");
        Long postId = createPost(postAuthorToken, firstCategoryId(), "Follow-up notice " + suffix);
        long rootCommentId = post("/api/posts/" + postId + "/comments", commentAuthorToken, Map.of("content", "Root thread comment"))
                .at("/data/commentId").asLong();

        assertThat(get("/api/notices", postAuthorToken).at("/data/total").asLong()).isEqualTo(1);
        JsonNode followUp = post("/api/posts/" + postId + "/comments", followerToken, Map.of(
                "content", "Follow-up comment", "parentCommentId", rootCommentId
        ));
        assertCode(followUp, 0);
        assertThat(get("/api/notices", postAuthorToken).at("/data/total").asLong()).isEqualTo(1);
        JsonNode targetNotices = get("/api/notices", commentAuthorToken);
        assertThat(targetNotices.at("/data/total").asLong()).isEqualTo(1);
        assertThat(targetNotices.at("/data/records/0/sender/nickname").asText()).isEqualTo("Follower");
    }

    @Test
    void commentsSupportSecondLevelRepliesAndThreadDeletion() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String authorUsername = "thread_author_" + suffix;
        String readerUsername = "thread_reader_" + suffix;
        register(authorUsername, "123456", "Thread Author");
        register(readerUsername, "123456", "Thread Reader");
        String authorToken = login(authorUsername, "123456");
        String readerToken = login(readerUsername, "123456");
        Long postId = createPost(authorToken, firstCategoryId(), "Thread post " + suffix);

        JsonNode root = post("/api/posts/" + postId + "/comments", authorToken, Map.of("content", "Root comment"));
        assertCode(root, 0);
        long rootId = root.at("/data/commentId").asLong();

        JsonNode reply = post("/api/posts/" + postId + "/comments", readerToken, Map.of(
                "content", "First reply", "parentCommentId", rootId
        ));
        assertCode(reply, 0);
        long replyId = reply.at("/data/commentId").asLong();

        JsonNode nestedReply = post("/api/posts/" + postId + "/comments", authorToken, Map.of(
                "content", "Second reply", "parentCommentId", replyId
        ));
        assertCode(nestedReply, 0);

        JsonNode comments = get("/api/posts/" + postId + "/comments", authorToken);
        assertThat(comments.at("/data/total").asLong()).isEqualTo(3);
        assertThat(comments.at("/data/records/1/rootCommentId").asLong()).isEqualTo(rootId);
        assertThat(comments.at("/data/records/1/replyToNickname").isNull()).isTrue();
        assertThat(comments.at("/data/records/2/rootCommentId").asLong()).isEqualTo(rootId);
        assertThat(comments.at("/data/records/2/replyToNickname").asText()).isEqualTo("Thread Reader");

        JsonNode firstLike = post("/api/comments/" + rootId + "/like", readerToken, null);
        assertCode(firstLike, 0);
        assertThat(firstLike.at("/data/liked").asBoolean()).isTrue();
        assertThat(firstLike.at("/data/likeCount").asInt()).isEqualTo(1);
        JsonNode duplicateLike = post("/api/comments/" + rootId + "/like", readerToken, null);
        assertCode(duplicateLike, 0);
        assertThat(duplicateLike.at("/data/likeCount").asInt()).isEqualTo(1);
        assertThat(get("/api/posts/" + postId + "/comments", readerToken)
                .at("/data/records/0/liked").asBoolean()).isTrue();
        JsonNode notices = get("/api/notices", authorToken);
        assertThat(notices.at("/data/records/0/type").asInt()).isEqualTo(3);
        assertThat(notices.at("/data/records/0/content").asText()).contains("Thread post").contains("Root comment");

        JsonNode deletedReply = delete("/api/comments/" + replyId, readerToken);
        assertCode(deletedReply, 0);
        JsonNode remainingAfterReplyDeletion = get("/api/posts/" + postId + "/comments", authorToken);
        assertThat(remainingAfterReplyDeletion.at("/data/total").asLong()).isEqualTo(2);
        assertThat(remainingAfterReplyDeletion.at("/data/records/0/id").asLong()).isEqualTo(rootId);
        assertThat(get("/api/posts/" + postId + "?radiusKm=10", authorToken)
                .at("/data/commentCount").asInt()).isEqualTo(2);

        JsonNode deleted = delete("/api/comments/" + rootId, authorToken);
        assertCode(deleted, 0);
        assertThat(get("/api/posts/" + postId + "/comments", authorToken).at("/data/total").asLong()).isZero();
        assertThat(get("/api/posts/" + postId + "?radiusKm=10", authorToken).at("/data/commentCount").asInt()).isZero();
    }

    @Test
    void commentKeepsSchoolSnapshotAndSchoolChangesAreMonthlyLimited() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String username = "snapshot_" + suffix;
        register(username, "123456", "Snapshot User");
        String token = login(username, "123456");
        Long postId = createPost(token, firstCategoryId(), "Snapshot post " + suffix);

        JsonNode comment = post("/api/posts/" + postId + "/comments", token, Map.of("content", "Snapshot comment"));
        assertCode(comment, 0);
        String originalSchoolName = get("/api/posts/" + postId + "/comments?radiusKm=10", token)
                .at("/data/records/0/author/schoolName").asText();
        assertThat(put("/api/users/me", token, Map.of("nickname", "Snapshot User", "schoolId", 2)).at("/code").asInt()).isZero();

        JsonNode quota = get("/api/users/me/school-change-quota", token);
        assertCode(quota, 0);
        assertThat(quota.at("/data/used").asInt()).isEqualTo(1);
        assertThat(quota.at("/data/limit").asInt()).isEqualTo(5);
        assertThat(quota.at("/data/remaining").asInt()).isEqualTo(4);
        assertThat(quota.at("/data/resetsOn").asText())
                .isEqualTo(YearMonth.now().plusMonths(1).atDay(1).toString());

        JsonNode comments = get("/api/posts/" + postId + "/comments?radiusKm=50", token);
        assertThat(comments.at("/data/records/0/author/schoolName").asText()).isEqualTo(originalSchoolName);

        for (int index = 0; index < 4; index++) {
            long schoolId = index % 2 == 0 ? 1 : 2;
            assertThat(put("/api/users/me", token, Map.of("nickname", "Snapshot User", "schoolId", schoolId)).at("/code").asInt()).isZero();
        }
        JsonNode limited = put("/api/users/me", token, Map.of("nickname", "Snapshot User", "schoolId", 1));
        assertCode(limited, 40900);
    }

    @Test
    void newUserMustBindSchoolBeforeUsingCampusScopedFeatures() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String username = "unbound_" + suffix;
        JsonNode registration = post("/api/auth/register", null, Map.of(
                "username", username,
                "password", "123456",
                "nickname", "Unbound User"
        ));
        assertThat(registration.at("/code").asInt()).isZero();

        String token = login(username, "123456");
        JsonNode profile = get("/api/users/me", token);
        assertThat(profile.at("/data/schoolId").isNull()).isTrue();
        assertCode(get("/api/posts/feed?radiusKm=10", token), 40900);
        assertCode(post("/api/posts", token, Map.of(
                "categoryId", firstCategoryId(),
                "title", "Unbound post",
                "content", "This request must be rejected before school binding."
        )), 40900);

        JsonNode boundProfile = put("/api/users/me", token, Map.of(
                "nickname", "Unbound User",
                "schoolId", 1
        ));
        assertThat(boundProfile.at("/code").asInt()).isZero();
        assertThat(boundProfile.at("/data/schoolId").asLong()).isEqualTo(1L);
        assertThat(get("/api/posts/feed?radiusKm=10", token).at("/code").asInt()).isZero();
    }

    @Test
    void campusScopesAndCampusSelectionEndpointsWorkTogether() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String xianlinUsername = "xianlin_" + suffix;
        String gulouUsername = "gulou_" + suffix;
        String cityUsername = "city_" + suffix;
        register(xianlinUsername, "123456", "Xianlin User");
        register(gulouUsername, "123456", "Gulou User");
        register(cityUsername, "123456", "City User");

        String xianlinToken = login(xianlinUsername, "123456");
        String gulouToken = login(gulouUsername, "123456");
        String cityToken = login(cityUsername, "123456");
        assertCode(put("/api/users/me", gulouToken, Map.of("nickname", "Gulou User", "schoolId", 5)), 0);
        assertCode(put("/api/users/me", cityToken, Map.of("nickname", "City User", "schoolId", 2)), 0);

        Long xianlinPostId = createPost(xianlinToken, firstCategoryId(), "Xianlin scope " + suffix);
        Long gulouPostId = createPost(gulouToken, firstCategoryId(), "Gulou scope " + suffix);
        Long cityPostId = createPost(cityToken, firstCategoryId(), "City scope " + suffix);

        JsonNode campusFeed = get("/api/posts/feed?scope=CAMPUS", xianlinToken);
        assertCode(campusFeed, 0);
        assertThat(idsOf(campusFeed)).contains(xianlinPostId).doesNotContain(gulouPostId, cityPostId);

        JsonNode universityFeed = get("/api/posts/feed?scope=UNIVERSITY", xianlinToken);
        assertCode(universityFeed, 0);
        assertThat(idsOf(universityFeed)).contains(xianlinPostId, gulouPostId).doesNotContain(cityPostId);

        JsonNode cityFeed = get("/api/posts/feed?scope=CITY", xianlinToken);
        assertCode(cityFeed, 0);
        assertThat(idsOf(cityFeed)).contains(xianlinPostId, gulouPostId, cityPostId);

        JsonNode provinces = get("/api/schools/provinces", null).at("/data");
        assertThat(provinces).hasSize(2);
        String province = URLEncoder.encode(provinces.get(0).asText(), StandardCharsets.UTF_8);
        JsonNode cities = get("/api/schools/cities?province=" + province, null).at("/data");
        assertThat(cities).isNotEmpty();
        String city = URLEncoder.encode(cities.get(0).asText(), StandardCharsets.UTF_8);
        JsonNode campuses = get("/api/schools/campuses?province=" + province + "&city=" + city, null);
        assertCode(campuses, 0);
        assertThat(campuses.at("/data")).isNotEmpty();
        assertThat(campuses.at("/data")).allMatch(item -> !item.at("/campusName").asText().isBlank());
    }

    @Test
    void refreshTokenRotatesSessionAndLogoutRevokesIt() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String username = "refresh_" + suffix;
        register(username, "123456", "Refresh User");

        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient sessionClient = HttpClient.newBuilder().cookieHandler(cookies).build();
        HttpResponse<String> loginResponse = sendPost(sessionClient, "/api/auth/login", null, Map.of(
                "username", username,
                "password", "123456"
        ), Map.of());
        JsonNode login = objectMapper.readTree(loginResponse.body());
        assertCode(login, 0);
        assertThat(login.at("/data/refreshToken").isMissingNode()).isTrue();
        assertThat(loginResponse.headers().firstValue("Set-Cookie"))
                .hasValueSatisfying(value -> assertThat(value).contains("HttpOnly", "SameSite=Lax", "Path=/api/auth"));

        String originalAccessToken = login.at("/data/token").asText();
        String originalRefreshToken = refreshCookieValue(cookies);
        assertThat(originalRefreshToken).isNotBlank();

        HttpResponse<String> refreshResponse = sendPost(sessionClient, "/api/auth/refresh", null, null, Map.of());
        JsonNode refresh = objectMapper.readTree(refreshResponse.body());
        assertCode(refresh, 0);
        String refreshedAccessToken = refresh.at("/data/token").asText();
        String refreshedRefreshToken = refreshCookieValue(cookies);
        assertThat(refreshedAccessToken).isNotEqualTo(originalAccessToken);
        assertThat(refreshedRefreshToken).isNotEqualTo(originalRefreshToken);
        assertCode(get("/api/users/me", originalAccessToken), 40100);
        assertCode(get("/api/users/me", refreshedAccessToken), 0);

        JsonNode reusedRefresh = objectMapper.readTree(sendPost(httpClient, "/api/auth/refresh", null, null,
                Map.of("Cookie", "campuscircle_refresh=" + originalRefreshToken)).body());
        assertCode(reusedRefresh, 40100);
        assertCode(get("/api/users/me", refreshedAccessToken), 0);

        JsonNode logout = objectMapper.readTree(sendPost(sessionClient, "/api/auth/logout", refreshedAccessToken, null, Map.of()).body());
        assertCode(logout, 0);
        assertCode(get("/api/users/me", refreshedAccessToken), 40100);
        JsonNode refreshAfterLogout = objectMapper.readTree(sendPost(httpClient, "/api/auth/refresh", null, null,
                Map.of("Cookie", "campuscircle_refresh=" + refreshedRefreshToken)).body());
        assertCode(refreshAfterLogout, 40100);
    }

    @Test
    void avatarUploadRejectsUnsupportedContentTypeAsClientError() throws Exception {
        HttpResponse<String> response = sendPost(httpClient, "/api/users/me/avatar", null, null, Map.of());

        assertThat(response.statusCode()).isEqualTo(415);
        assertCode(objectMapper.readTree(response.body()), 40000);
    }

    @Test
    void questionTrackingSupportsPostCommentSourcesSubscriptionsAndLifecycle() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String askerUsername = "asker_" + suffix;
        String subscriberUsername = "subscriber_" + suffix;
        String outsiderUsername = "outsider_" + suffix;
        register(askerUsername, "123456", "Question Asker");
        register(subscriberUsername, "123456", "Question Subscriber");
        register(outsiderUsername, "123456", "Question Outsider");

        String askerToken = login(askerUsername, "123456");
        String subscriberToken = login(subscriberUsername, "123456");
        String outsiderToken = login(outsiderUsername, "123456");
        Long postId = createPost(askerToken, firstCategoryId(), "Question tracking " + suffix);

        JsonNode created = post("/api/questions", askerToken, Map.of(
                "sourceType", "POST",
                "sourceId", postId,
                "questionText", "想持续追踪这条讨论的可靠结论"
        ));
        assertCode(created, 0);
        long questionId = created.at("/data/id").asLong();
        assertThat(questionId).isPositive();
        assertThat(created.at("/data/sourceType").asText()).isEqualTo("POST");
        assertThat(created.at("/data/status").asText()).isEqualTo("OPEN");
        assertThat(created.at("/data/subscriberCount").asLong()).isZero();
        assertThat(created.at("/data/subscribed").asBoolean()).isFalse();

        assertCode(post("/api/questions", askerToken, Map.of(
                "sourceType", "POST",
                "sourceId", postId,
                "questionText", "Duplicate question"
        )), 40900);
        assertCode(post("/api/questions", outsiderToken, Map.of(
                "sourceType", "POST",
                "sourceId", postId,
                "questionText", "Unauthorized question"
        )), 40300);

        JsonNode foundBySource = get("/api/questions/by-source?sourceType=POST&sourceId=" + postId, subscriberToken);
        assertCode(foundBySource, 0);
        assertThat(foundBySource.at("/data/id").asLong()).isEqualTo(questionId);
        assertThat(foundBySource.at("/data/subscribed").asBoolean()).isFalse();

        JsonNode firstSubscription = post("/api/questions/" + questionId + "/subscriptions", subscriberToken, null);
        assertCode(firstSubscription, 0);
        assertThat(firstSubscription.at("/data/subscribed").asBoolean()).isTrue();
        assertThat(firstSubscription.at("/data/subscriberCount").asLong()).isEqualTo(1);
        JsonNode repeatedSubscription = post("/api/questions/" + questionId + "/subscriptions", subscriberToken, null);
        assertCode(repeatedSubscription, 0);
        assertThat(repeatedSubscription.at("/data/subscriberCount").asLong()).isEqualTo(1);

        JsonNode asked = get("/api/users/me/questions?role=ASKED", askerToken);
        assertCode(asked, 0);
        assertThat(asked.at("/data/total").asLong()).isEqualTo(1);
        assertThat(asked.at("/data/records").size()).isEqualTo(1);
        JsonNode subscribed = get("/api/users/me/questions?role=SUBSCRIBED", subscriberToken);
        assertCode(subscribed, 0);
        assertThat(subscribed.at("/data/total").asLong()).isEqualTo(1);
        assertThat(subscribed.at("/data/records/0/subscribed").asBoolean()).isTrue();

        assertCode(post("/api/questions/" + questionId + "/subscriptions", askerToken, null), 40300);
        JsonNode stillSubscribed = get("/api/users/me/questions?role=SUBSCRIBED", subscriberToken);
        assertCode(stillSubscribed, 0);
        assertThat(stillSubscribed.at("/data/total").asLong()).isEqualTo(1);

        JsonNode candidateComment = post("/api/posts/" + postId + "/comments", outsiderToken, Map.of(
                "content", "候选答复：可以先查看图书馆开放时间，再按校区确认预约规则。",
                "answerQuestionId", questionId
        ));
        assertCode(candidateComment, 0);
        long candidateCommentId = candidateComment.at("/data/commentId").asLong();

        JsonNode answers = get("/api/questions/" + questionId + "/answers", askerToken);
        assertCode(answers, 0);
        assertThat(answers.at("/data").size()).isEqualTo(1);
        long answerId = answers.at("/data/0/id").asLong();
        assertThat(answers.at("/data/0/commentId").asLong()).isEqualTo(candidateCommentId);
        assertThat(answers.at("/data/0/status").asText()).isEqualTo("PENDING");
        assertCode(post("/api/questions/" + questionId + "/answers/" + answerId + "/accept", outsiderToken, null), 40300);

        JsonNode acceptedFirst = post("/api/questions/" + questionId + "/answers/" + answerId + "/accept", askerToken, null);
        assertCode(acceptedFirst, 0);
        assertThat(acceptedFirst.at("/data/status").asText()).isEqualTo("OPEN");
        assertThat(acceptedFirst.at("/data/approvedAnswerCount").asLong()).isEqualTo(1);
        assertThat(acceptedFirst.at("/data/approvedAnswers/0/id").asLong()).isEqualTo(answerId);
        assertThat(acceptedFirst.at("/data/approvedAnswers/0/commentId").asLong()).isEqualTo(candidateCommentId);
        JsonNode subscriberNoticesAfterAcceptance = get("/api/notices", subscriberToken);
        assertCode(subscriberNoticesAfterAcceptance, 0);
        assertThat(subscriberNoticesAfterAcceptance.at("/data/total").asLong()).isEqualTo(1);
        assertThat(subscriberNoticesAfterAcceptance.at("/data/records/0/type").asInt()).isEqualTo(9);
        assertThat(subscriberNoticesAfterAcceptance.at("/data/records/0/questionId").asLong()).isEqualTo(questionId);

        JsonNode secondCandidateComment = post("/api/posts/" + postId + "/comments", subscriberToken, Map.of(
                "content", "另一个可行地点是东南大学图书馆，暑期开放安排以当天公告为准。",
                "answerQuestionId", questionId
        ));
        assertCode(secondCandidateComment, 0);
        long secondCandidateCommentId = secondCandidateComment.at("/data/commentId").asLong();
        JsonNode answersAfterSecondSubmission = get("/api/questions/" + questionId + "/answers", askerToken);
        assertCode(answersAfterSecondSubmission, 0);
        long secondAnswerId = answersAfterSecondSubmission.at("/data/1/id").asLong();
        assertThat(answersAfterSecondSubmission.at("/data/1/commentId").asLong()).isEqualTo(secondCandidateCommentId);
        assertThat(answersAfterSecondSubmission.at("/data/1/status").asText()).isEqualTo("PENDING");

        JsonNode acceptedSecond = post("/api/questions/" + questionId + "/answers/" + secondAnswerId + "/accept", askerToken, null);
        assertCode(acceptedSecond, 0);
        assertThat(acceptedSecond.at("/data/status").asText()).isEqualTo("OPEN");
        assertThat(acceptedSecond.at("/data/approvedAnswerCount").asLong()).isEqualTo(2);
        assertThat(acceptedSecond.at("/data/approvedAnswers").size()).isEqualTo(2);

        assertCode(post("/api/questions/" + questionId + "/complete", outsiderToken, null), 40300);
        JsonNode completed = post("/api/questions/" + questionId + "/complete", askerToken, null);
        assertCode(completed, 0);
        assertThat(completed.at("/data/status").asText()).isEqualTo("COMPLETED");
        assertThat(completed.at("/data/approvedAnswerCount").asLong()).isEqualTo(2);
        assertThat(completed.at("/data/approvedAnswers").size()).isEqualTo(2);

        assertCode(post("/api/questions/" + questionId + "/reopen", outsiderToken, null), 40300);
        JsonNode reopened = post("/api/questions/" + questionId + "/reopen", askerToken, null);
        assertCode(reopened, 0);
        assertThat(reopened.at("/data/status").asText()).isEqualTo("OPEN");
        assertThat(reopened.at("/data/approvedAnswerCount").asLong()).isEqualTo(2);
        assertThat(reopened.at("/data/approvedAnswers").size()).isEqualTo(2);

        JsonNode reopenedCandidateComment = post("/api/posts/" + postId + "/comments", outsiderToken, Map.of(
                "content", "问题重新开启后补充的候选答复。",
                "answerQuestionId", questionId
        ));
        assertCode(reopenedCandidateComment, 0);
        JsonNode answersAfterReopen = get("/api/questions/" + questionId + "/answers", askerToken);
        assertCode(answersAfterReopen, 0);
        assertThat(answersAfterReopen.at("/data").size()).isEqualTo(3);
        assertThat(answersAfterReopen.at("/data/2/status").asText()).isEqualTo("PENDING");

        JsonNode completedAgain = post("/api/questions/" + questionId + "/complete", askerToken, null);
        assertCode(completedAgain, 0);
        assertThat(completedAgain.at("/data/status").asText()).isEqualTo("COMPLETED");

        JsonNode subscriptionAfterCompletion = post("/api/questions/" + questionId + "/subscriptions", outsiderToken, null);
        assertCode(subscriptionAfterCompletion, 0);
        assertThat(subscriptionAfterCompletion.at("/data/subscribed").asBoolean()).isTrue();
        assertThat(subscriptionAfterCompletion.at("/data/subscriberCount").asLong()).isEqualTo(2);
        JsonNode completedSubscriptions = get("/api/users/me/questions?role=SUBSCRIBED", outsiderToken);
        assertCode(completedSubscriptions, 0);
        assertThat(completedSubscriptions.at("/data/records/0/status").asText()).isEqualTo("COMPLETED");

        JsonNode comment = post("/api/posts/" + postId + "/comments", subscriberToken, Map.of("content", "我也想知道最终结论。"));
        assertCode(comment, 0);
        long commentId = comment.at("/data/commentId").asLong();
        JsonNode commentQuestion = post("/api/questions", subscriberToken, Map.of(
                "sourceType", "COMMENT",
                "sourceId", commentId,
                "questionText", "这条评论也需要等待后续答复"
        ));
        assertCode(commentQuestion, 0);
        assertThat(commentQuestion.at("/data/sourceType").asText()).isEqualTo("COMMENT");
        JsonNode commentReply = post("/api/posts/" + postId + "/comments", subscriberToken, Map.of(
                "content", "针对这条评论的补充回复", "parentCommentId", commentId
        ));
        assertCode(commentReply, 0);
        long commentReplyId = commentReply.at("/data/commentId").asLong();
        assertCode(post("/api/questions", subscriberToken, Map.of(
                "sourceType", "COMMENT",
                "sourceId", commentReplyId,
                "questionText", "二级回复不应创建问题"
        )), 40000);
        assertCode(post("/api/questions", subscriberToken, Map.of(
                "sourceType", "COMMENT",
                "sourceId", commentId,
                "questionText", "Duplicate comment question"
        )), 40900);
        assertThat(get("/api/questions/by-source?sourceType=COMMENT&sourceId=" + commentId, askerToken)
                .at("/data/id").asLong()).isEqualTo(commentQuestion.at("/data/id").asLong());
        JsonNode commentQuestionSummaries = get("/api/questions/by-sources?sourceType=COMMENT&sourceIds="
                + commentId + "&sourceIds=" + candidateCommentId, askerToken);
        assertCode(commentQuestionSummaries, 0);
        assertThat(commentQuestionSummaries.at("/data/" + commentId + "/id").asLong())
                .isEqualTo(commentQuestion.at("/data/id").asLong());
        assertThat(commentQuestionSummaries.at("/data/" + commentId + "/sourcePostId").asLong()).isEqualTo(postId);
        assertThat(commentQuestionSummaries.at("/data/" + candidateCommentId).isMissingNode()).isTrue();
        assertCode(delete("/api/questions/" + questionId, outsiderToken), 40300);
        assertCode(delete("/api/posts/" + postId, askerToken), 0);
        assertCode(get("/api/posts/" + postId, askerToken), 40400);
        assertCode(get("/api/questions/" + questionId, subscriberToken), 40400);
        assertCode(get("/api/questions/" + commentQuestion.at("/data/id").asLong(), subscriberToken), 40400);
        JsonNode removedSubscriptions = get("/api/users/me/questions?role=SUBSCRIBED", subscriberToken);
        assertCode(removedSubscriptions, 0);
        assertThat(removedSubscriptions.at("/data/total").asLong()).isZero();
        JsonNode removedOutsiderSubscriptions = get("/api/users/me/questions?role=SUBSCRIBED", outsiderToken);
        assertCode(removedOutsiderSubscriptions, 0);
        assertThat(removedOutsiderSubscriptions.at("/data/total").asLong()).isZero();
        JsonNode deletionNotices = get("/api/notices", subscriberToken);
        assertCode(deletionNotices, 0);
        assertThat(deletionNotices.at("/data/records")).anySatisfy(notice -> {
            assertThat(notice.at("/type").asInt()).isEqualTo(7);
            assertThat(notice.at("/questionId").asLong()).isEqualTo(questionId);
        });
    }

    private void register(String username, String password, String nickname) throws Exception {
        JsonNode response = post("/api/auth/register", null, Map.of(
                "username", username,
                "password", password
        ));
        assertThat(response.at("/code").asInt())
                .describedAs(response.toPrettyString())
                .isZero();
        String token = login(username, password);
        JsonNode profile = put("/api/users/me", token, Map.of(
                "nickname", nickname,
                "schoolId", 1
        ));
        assertThat(profile.at("/code").asInt())
                .describedAs(profile.toPrettyString())
                .isZero();
    }

    private String login(String username, String password) throws Exception {
        JsonNode response = post("/api/auth/login", null, Map.of(
                "username", username,
                "password", password
        ));

        assertThat(response.at("/code").asInt())
                .describedAs(response.toPrettyString())
                .isZero();
        return response.at("/data/token").asText();
    }

    private Long firstCategoryId() throws Exception {
        JsonNode response = get("/api/categories", null);
        assertThat(response.at("/code").asInt()).isZero();
        assertThat(response.at("/data").size()).isEqualTo(6);

        return response.at("/data/0/id").asLong();
    }

    private Long createPost(String token, Long categoryId) throws Exception {
        return createPost(token, categoryId, "高数复习资料怎么整理？");
    }

    private Long createPost(String token, Long categoryId, String title) throws Exception {
        JsonNode response = post("/api/posts", token, Map.of(
                "categoryId", categoryId,
                "title", title,
                "content", "想问问大家期末复习有什么方法。"
        ));

        assertThat(response.at("/code").asInt())
                .describedAs(response.toPrettyString())
                .isZero();
        Long postId = response.at("/data/postId").asLong();
        assertThat(postId).isPositive();
        return postId;
    }

    private Set<Long> idsOf(JsonNode page) {
        Set<Long> ids = new HashSet<>();
        for (JsonNode record : page.at("/data/records")) {
            ids.add(record.at("/id").asLong());
        }
        return ids;
    }

    private void assertLocationFeed(String token, Long postId) throws Exception {
        JsonNode nearbySchools = get("/api/schools/nearby?schoolId=1&radiusKm=30", null);
        assertThat(nearbySchools.at("/code").asInt()).isZero();
        assertThat(nearbySchools.at("/data/0/id").asLong()).isEqualTo(1);

        String schoolName = nearbySchools.at("/data/0/name").asText();
        JsonNode schools = get("/api/schools/search?keyword=" + URLEncoder.encode(schoolName, StandardCharsets.UTF_8), null);
        assertThat(schools.at("/code").asInt()).isZero();
        assertThat(schools.at("/data").size())
                .describedAs(schools.toPrettyString())
                .isGreaterThanOrEqualTo(1);

        JsonNode feed = get("/api/posts/feed?radiusKm=30", token);
        assertThat(feed.at("/code").asInt()).isZero();
        assertThat(feed.at("/data/total").asLong()).isGreaterThanOrEqualTo(1);
        JsonNode matchedPost = null;
        for (JsonNode record : feed.at("/data/records")) {
            if (record.at("/id").asLong() == postId) {
                matchedPost = record;
                break;
            }
        }
        assertThat(matchedPost)
                .describedAs(feed.toPrettyString())
                .isNotNull();
        assertThat(matchedPost.at("/school/id").asLong()).isEqualTo(1);
    }

    private Long createComment(String token, Long postId) throws Exception {
        JsonNode response = post("/api/posts/" + postId + "/comments", token, Map.of("content", "我一般先整理错题，再刷历年卷。"));
        assertThat(response.at("/code").asInt()).isZero();
        Long commentId = response.at("/data/commentId").asLong();
        assertThat(commentId).isPositive();
        return commentId;
    }

    private void likePost(String token, Long postId) throws Exception {
        JsonNode response = post("/api/posts/" + postId + "/like", token, null);
        assertThat(response.at("/code").asInt()).isZero();
        assertThat(response.at("/data/liked").asBoolean()).isTrue();
        assertThat(response.at("/data/likeCount").asInt()).isEqualTo(1);
    }

    private JsonNode get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url(path)))
                .GET()
                .header("Accept", MediaType.APPLICATION_JSON_VALUE);
        addAuthorization(builder, token);

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(response.body());
    }

    private JsonNode put(String path, String token, Object body) throws Exception {
        String requestBody = body == null ? "" : objectMapper.writeValueAsString(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url(path)))
                .PUT(HttpRequest.BodyPublishers.ofString(requestBody))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("Accept", MediaType.APPLICATION_JSON_VALUE);
        addAuthorization(builder, token);

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(response.body());
    }

    private JsonNode patch(String path, String token, Object body) throws Exception {
        String requestBody = body == null ? "" : objectMapper.writeValueAsString(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url(path)))
                .method("PATCH", HttpRequest.BodyPublishers.ofString(requestBody))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("Accept", MediaType.APPLICATION_JSON_VALUE);
        addAuthorization(builder, token);

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(response.body());
    }

    private JsonNode delete(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url(path)))
                .DELETE()
                .header("Accept", MediaType.APPLICATION_JSON_VALUE);
        addAuthorization(builder, token);

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(response.body());
    }

    private JsonNode post(String path, String token, Object body) throws Exception {
        return post(path, token, body, Map.of());
    }

    private JsonNode post(String path, String token, Object body, Map<String, String> headers) throws Exception {
        return objectMapper.readTree(sendPost(httpClient, path, token, body, headers).body());
    }

    private HttpResponse<String> sendPost(HttpClient client, String path, String token, Object body,
                                          Map<String, String> headers) throws Exception {
        String requestBody = body == null ? "" : objectMapper.writeValueAsString(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url(path)))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("Accept", MediaType.APPLICATION_JSON_VALUE);
        addAuthorization(builder, token);
        headers.forEach(builder::header);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String refreshCookieValue(CookieManager cookies) {
        return cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals("campuscircle_refresh"))
                .findFirst()
                .orElseThrow()
                .getValue();
    }

    private void assertCode(JsonNode response, int expectedCode) {
        assertThat(response.at("/code").asInt())
                .describedAs(response.toPrettyString())
                .isEqualTo(expectedCode);
    }

    private void addAuthorization(HttpRequest.Builder builder, String token) {
        if (token != null) {
            builder.header("Authorization", bearer(token));
        }
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
