package com.squad.backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "blog_posts")
public class BlogPost {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    @Id
    private String id;

    private String title;

    @Indexed(unique = true)
    private String slug;

    private String summary;
    private String body;

    /** Optional cover image as a data URL (same approach as player profile photos). */
    private String coverImage;

    /** DRAFT or PUBLISHED */
    @Indexed
    private String status;

    private Instant publishedAt;
    private String createdBy;
    private String updatedBy;
    private Instant createdAt;
    private Instant updatedAt;
}
