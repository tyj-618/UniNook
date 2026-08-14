package com.uninook.ai;

import com.uninook.school.CampusScope;
import com.uninook.school.SchoolService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class SearchPostsTool implements AgentTool {

    private static final int RETRIEVAL_LIMIT = 5;
    private static final List<String> GENERIC_QUERY_TERMS = List.of(
            "\u8bf7\u95ee", "\u9644\u8fd1", "\u6700\u8fd1", "\u6709\u54ea\u4e9b", "\u6709\u6ca1\u6709", "\u662f\u5426",
            "\u4ec0\u4e48", "\u54ea\u91cc", "\u600e\u4e48", "\u5982\u4f55", "\u53ef\u4ee5", "\u6821\u533a", "\u5927\u5b66",
            "\u5b66\u6821", "\u5f00\u653e", "\u5f00\u95e8", "\u5173\u95e8", "\u51e0\u70b9", "\u65f6\u95f4", "\u5468\u672b",
            "\u5de5\u4f5c\u65e5", "\u90a3\u4e2a", "\u8fd9\u4e2a", "\u90a3\u95f4", "\u8fd9\u95f4", "\u5417", "\u5462");
    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "search_posts",
            "Search public campus posts within the current user's permitted campus scope.",
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "keyword", Map.of("type", "string"),
                            "scope", Map.of("type", "string", "enum", List.of("CAMPUS", "UNIVERSITY", "NEARBY_10", "NEARBY_20", "CITY")),
                            "user_id", Map.of("type", "integer"),
                            "campus_id", Map.of("type", "integer")
                    ),
                    "required", List.of("keyword")
            ),
            ToolOperation.READ
    );

    private final PostRetriever postRetriever;
    private final SchoolService schoolService;

    public SearchPostsTool(PostRetriever postRetriever, SchoolService schoolService) {
        this.postRetriever = postRetriever;
        this.schoolService = schoolService;
    }

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        List<Long> allowedSchoolIds = schoolService.listScopeSchoolIds(context.userProfile().schoolId(), context.scope());
        String keyword = String.valueOf(arguments.get("keyword"));
        List<RetrievedPost> posts = postRetriever.retrieve(new RetrievalQuery(
                        keyword, allowedSchoolIds, RETRIEVAL_LIMIT)).stream()
                .filter(post -> hasTopicEvidence(keyword, post))
                .toList();
        String observation = posts.stream()
                .map(post -> "postId: %d\ntitle: %s\nschool: %s\ncontent: %s".formatted(
                        post.id(), post.title(), post.schoolName(), post.excerpt()))
                .reduce((left, right) -> left + "\n---\n" + right)
                .orElse("No matching public campus posts were found.");
        return ToolExecutionResult.observation(observation, posts.stream().map(AiPostReference::from).toList());
    }

    private boolean hasTopicEvidence(String keyword, RetrievedPost post) {
        String postText = normalize(post.title() + " " + post.content());
        return topicTerms(keyword).stream().anyMatch(postText::contains);
    }

    private List<String> topicTerms(String keyword) {
        String meaningful = normalize(keyword);
        for (String genericTerm : GENERIC_QUERY_TERMS) {
            meaningful = meaningful.replace(genericTerm, "");
        }
        if (meaningful.length() < 2) {
            return List.of();
        }

        Set<String> terms = new LinkedHashSet<>();
        int minimumTermLength = meaningful.length() >= 3 ? 3 : 2;
        for (int length = Math.min(4, meaningful.length()); length >= minimumTermLength; length--) {
            for (int start = 0; start + length <= meaningful.length(); start++) {
                terms.add(meaningful.substring(start, start + length));
            }
        }
        return List.copyOf(terms);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsHan}a-z0-9]", "");
    }

}
