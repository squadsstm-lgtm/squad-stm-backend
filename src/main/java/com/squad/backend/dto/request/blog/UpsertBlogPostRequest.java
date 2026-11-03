package com.squad.backend.dto.request.blog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpsertBlogPostRequest {
    @NotBlank(message = "Title is required")
    @Size(max = 160)
    private String title;

    @NotBlank(message = "Summary is required")
    @Size(max = 400)
    private String summary;

    @NotBlank(message = "Body is required")
    @Size(max = 50000)
    private String body;

    /** Optional data URL, e.g. data:image/jpeg;base64,... */
    private String coverImage;

    /** DRAFT or PUBLISHED. Defaults to DRAFT on create. */
    private String status;
}
