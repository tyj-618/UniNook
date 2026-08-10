package com.uninook.ai;

import com.uninook.post.PostDetail;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class PostSearchDocumentBuilder {

    public String buildSearchText(PostDetail post) {
        return Stream.of(
                        post.title(),
                        post.categoryName(),
                        post.schoolName(),
                        post.campusName(),
                        post.schoolCity(),
                        post.content()
                )
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining("\n"));
    }

    public PostSearchDocument build(PostDetail post, List<Float> embedding) {
        return new PostSearchDocument(
                post.id(), post.schoolId(), post.categoryId(), post.title(), post.content(),
                post.categoryName(), post.schoolName(), post.campusName(), post.schoolCity(),
                buildSearchText(post), post.updatedAt(), embedding
        );
    }
}
