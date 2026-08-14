package com.uninook.ai;

import com.uninook.school.CampusScope;
import com.uninook.school.SchoolService;
import com.uninook.user.UserProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchPostsToolTests {

    @Test
    void excludesPostsThatOnlyMatchCampusContextButNotTheQuestionTopic() {
        PostRetriever retriever = mock(PostRetriever.class);
        SchoolService schoolService = mock(SchoolService.class);
        when(schoolService.listScopeSchoolIds(10L, CampusScope.CAMPUS)).thenReturn(List.of(10L));
        when(retriever.retrieve(any())).thenReturn(List.of(
                post(1L, "\u4ed9\u6797\u6821\u533a\u591c\u95f4\u73ed\u8f66", "\u56fe\u4e66\u9986\u4e1c\u95e8\u53d1\u8f66\u65f6\u95f4"),
                post(2L, "\u4ed9\u6797\u56fe\u4e66\u9986\u5468\u672b\u5f00\u653e", "\u5468\u672b\u5f00\u653e\u81f3 22:00")));

        ToolExecutionResult result = new SearchPostsTool(retriever, schoolService).execute(context(),
                Map.of("keyword", "\u4ed9\u6797\u6821\u533a\u6d77\u6d0b\u9986\u51e0\u70b9\u5f00\u95e8"));

        assertThat(result.references()).isEmpty();
        assertThat(result.content()).contains("No matching public campus posts were found.");
    }

    @Test
    void retainsPostsThatContainTheQuestionTopic() {
        PostRetriever retriever = mock(PostRetriever.class);
        SchoolService schoolService = mock(SchoolService.class);
        when(schoolService.listScopeSchoolIds(10L, CampusScope.CAMPUS)).thenReturn(List.of(10L));
        when(retriever.retrieve(any())).thenReturn(List.of(
                post(2L, "\u4ed9\u6797\u56fe\u4e66\u9986\u5468\u672b\u5f00\u653e", "\u5468\u672b\u5f00\u653e\u81f3 22:00")));

        ToolExecutionResult result = new SearchPostsTool(retriever, schoolService).execute(context(),
                Map.of("keyword", "\u56fe\u4e66\u9986\u5468\u672b\u51e0\u70b9\u5f00\u95e8"));

        assertThat(result.references()).extracting(AiPostReference::postId).containsExactly(2L);
    }

    private ToolExecutionContext context() {
        return new ToolExecutionContext(7L, new UserProfile(7L, "student", "Student", 10L, 1L,
                "Example University", "Example Campus", "Example City", null, null, 0, 1,
                true, null, null), CampusScope.CAMPUS);
    }

    private RetrievedPost post(Long id, String title, String content) {
        return new RetrievedPost(id, title, content, "Example University", null);
    }
}
