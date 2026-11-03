package com.squad.backend.dto.response.blog;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlogPostResponse {
    private String id;
    private String title;
    private String slug;
    private String summary;
    private String body;
    private String coverImage;
    private String status;
    private Instant publishedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private String updatedByName;
}
