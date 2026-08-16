package com.uninook.ai;

import com.uninook.auth.CurrentUserService;
import com.uninook.exception.BusinessException;
import com.uninook.post.CreatePostRequest;
import com.uninook.post.CreatePostResponse;
import com.uninook.post.PostService;
import com.uninook.school.CampusScope;
import com.uninook.user.UserProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PendingActionServiceTests {

    @Test
    void storesAUserBoundPostDraftWithConfiguredTtl() {
        AiProperties properties = new AiProperties();
        properties.setPendingActionTtlSeconds(600);
        InMemoryPendingActionStore store = new InMemoryPendingActionStore();
        PendingActionService service = service(store, properties);

        PendingActionSummary summary = service.preparePostDraft(context(7L), Map.of(
                "title", "Lost item",
                "content", "A blue umbrella was found."
        ));

        assertThat(summary.type()).isEqualTo(PendingActionType.CREATE_POST);
        assertThat(summary.expiresAt()).isAfter(java.time.Instant.now());
        assertThat(service.loadForUser(7L, summary.actionId()))
                .isPresent()
                .get()
                .extracting(PendingAction::title, PendingAction::content)
                .containsExactly("Lost item", "A blue umbrella was found.");
    }

    @Test
    void doesNotExposeAnotherUsersDraft() {
        AiProperties properties = new AiProperties();
        InMemoryPendingActionStore store = new InMemoryPendingActionStore();
        PendingActionService service = service(store, properties);

        PendingActionSummary summary = service.preparePostDraft(context(7L), Map.of(
                "title", "Lost item",
                "content", "A blue umbrella was found."
        ));

        assertThat(service.loadForUser(8L, summary.actionId())).isEmpty();
    }

    @Test
    void confirmsEachDraftOnlyOnceAndUsesTheCurrentUsersPostServicePath() {
        AiProperties properties = new AiProperties();
        InMemoryPendingActionStore store = new InMemoryPendingActionStore();
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        PostService postService = mock(PostService.class);
        when(currentUserService.requireUserId("Bearer owner")).thenReturn(7L);
        when(postService.createPost(eq("Bearer owner"), any(CreatePostRequest.class)))
                .thenReturn(new CreatePostResponse(99L));
        PendingActionService service = new PendingActionService(store, properties, currentUserService, postService);
        PendingActionSummary summary = service.preparePostDraft(context(7L), Map.of(
                "title", "Lost item",
                "content", "A blue umbrella was found."
        ));

        ConfirmPendingActionResponse response = service.confirmPost("Bearer owner", summary.actionId(), 3L);

        assertThat(response).isEqualTo(new ConfirmPendingActionResponse(summary.actionId(), 99L));
        verify(postService, times(1)).createPost("Bearer owner", new CreatePostRequest(
                3L, "Lost item", "A blue umbrella was found."));
        assertThat(service.loadForUser(7L, summary.actionId())).isEmpty();
        assertThatThrownBy(() -> service.confirmPost("Bearer owner", summary.actionId(), 3L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void cancelsOnlyTheCurrentUsersDraft() {
        AiProperties properties = new AiProperties();
        InMemoryPendingActionStore store = new InMemoryPendingActionStore();
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        when(currentUserService.requireUserId("Bearer owner")).thenReturn(7L);
        when(currentUserService.requireUserId("Bearer stranger")).thenReturn(8L);
        PendingActionService service = new PendingActionService(store, properties, currentUserService,
                mock(PostService.class));
        PendingActionSummary summary = service.preparePostDraft(context(7L), Map.of(
                "title", "Lost item",
                "content", "A blue umbrella was found."
        ));

        assertThatThrownBy(() -> service.cancel("Bearer stranger", summary.actionId()))
                .isInstanceOf(BusinessException.class);
        service.cancel("Bearer owner", summary.actionId());

        assertThat(service.loadForUser(7L, summary.actionId())).isEmpty();
    }

    @Test
    void expiredDraftIsRejectedForConfirmCancelAndLoad() {
        AiProperties properties = new AiProperties();
        InMemoryPendingActionStore store = new InMemoryPendingActionStore();
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        when(currentUserService.requireUserId("Bearer owner")).thenReturn(7L);
        PendingActionService service = new PendingActionService(store, properties, currentUserService,
                mock(PostService.class));
        PendingAction expired = new PendingAction("expired-action", PendingActionType.CREATE_POST, 7L,
                "Expired draft", "Expired content", java.time.Instant.now().minusSeconds(60));
        store.save(expired);

        assertThat(service.loadForUser(7L, "expired-action")).isEmpty();
        assertThatThrownBy(() -> service.confirmPost("Bearer owner", "expired-action", 3L))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.cancel("Bearer owner", "expired-action"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void zeroTtlDraftExpiresImmediately() {
        AiProperties properties = new AiProperties();
        properties.setPendingActionTtlSeconds(0);
        InMemoryPendingActionStore store = new InMemoryPendingActionStore();
        PendingActionService service = service(store, properties);

        PendingActionSummary summary = service.preparePostDraft(context(7L), Map.of(
                "title", "Lost item",
                "content", "A blue umbrella was found."
        ));

        assertThat(service.loadForUser(7L, summary.actionId())).isEmpty();
    }

    @Test
    void strangerCannotConfirmAnotherUsersDraft() {
        AiProperties properties = new AiProperties();
        InMemoryPendingActionStore store = new InMemoryPendingActionStore();
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        PostService postService = mock(PostService.class);
        when(currentUserService.requireUserId("Bearer owner")).thenReturn(7L);
        when(currentUserService.requireUserId("Bearer stranger")).thenReturn(8L);
        PendingActionService service = new PendingActionService(store, properties, currentUserService, postService);
        PendingActionSummary summary = service.preparePostDraft(context(7L), Map.of(
                "title", "Lost item",
                "content", "A blue umbrella was found."
        ));

        assertThatThrownBy(() -> service.confirmPost("Bearer stranger", summary.actionId(), 3L))
                .isInstanceOf(BusinessException.class);
        verify(postService, times(0)).createPost(any(), any(CreatePostRequest.class));
        assertThat(service.loadForUser(7L, summary.actionId())).isPresent();
    }

    private PendingActionService service(InMemoryPendingActionStore store, AiProperties properties) {
        return new PendingActionService(store, properties, mock(CurrentUserService.class), mock(PostService.class));
    }

    private ToolExecutionContext context(Long userId) {
        return new ToolExecutionContext(userId, new UserProfile(
                userId, "student", "Student", 10L, 1L,
                "Example University", "Example Campus", "Example City", null, null,
                0, 1, true, null, null), CampusScope.CAMPUS);
    }
}
