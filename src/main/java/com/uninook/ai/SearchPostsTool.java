package com.uninook.ai;

import com.uninook.school.CampusScope;
import com.uninook.school.SchoolService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SearchPostsTool implements AgentTool {

    private static final int RETRIEVAL_LIMIT = 5;
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
        List<RetrievedPost> posts = postRetriever.retrieve(new RetrievalQuery(
                String.valueOf(arguments.get("keyword")), allowedSchoolIds, RETRIEVAL_LIMIT));
        String observation = posts.stream()
                .map(post -> "postId: %d\ntitle: %s\nschool: %s\ncontent: %s".formatted(
                        post.id(), post.title(), post.schoolName(), post.excerpt()))
                .reduce((left, right) -> left + "\n---\n" + right)
                .orElse("No matching public campus posts were found.");
        return ToolExecutionResult.observation(observation, posts.stream().map(AiPostReference::from).toList());
    }

}
