package com.uninook.ai;

import com.uninook.school.CampusScope;
import com.uninook.user.UserProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PendingActionServiceTests {

    @Test
    void storesAUserBoundPostDraftWithConfiguredTtl() {
        AiProperties properties = new AiProperties();
        properties.setPendingActionTtlSeconds(600);
        InMemoryPendingActionStore store = new InMemoryPendingActionStore();
        PendingActionService service = new PendingActionService(store, properties);

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
        PendingActionService service = new PendingActionService(store, properties);

        PendingActionSummary summary = service.preparePostDraft(context(7L), Map.of(
                "title", "Lost item",
                "content", "A blue umbrella was found."
        ));

        assertThat(service.loadForUser(8L, summary.actionId())).isEmpty();
    }

    private ToolExecutionContext context(Long userId) {
        return new ToolExecutionContext(userId, new UserProfile(
                userId, "student", "Student", 10L, 1L,
                "Example University", "Example Campus", "Example City", null, null,
                0, 1, true, null, null), CampusScope.CAMPUS);
    }
}
