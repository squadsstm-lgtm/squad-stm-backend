package com.squad.backend.controller;

import com.squad.backend.constants.ErrorMessages;
import com.squad.backend.dto.response.ApiResponse;
import com.squad.backend.dto.response.blog.BlogPostResponse;
import com.squad.backend.service.BlogPostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/blog")
@RequiredArgsConstructor
@Slf4j
public class BlogController {

    private final BlogPostService blogPostService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BlogPostResponse>>> listPublished() {
        try {
            return ResponseEntity.ok(ApiResponse.success(blogPostService.listPublished()));
        } catch (Exception e) {
            log.error("List published blog posts error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/{idOrSlug}")
    public ResponseEntity<ApiResponse<BlogPostResponse>> getPublished(@PathVariable String idOrSlug) {
        try {
            return ResponseEntity.ok(ApiResponse.success(blogPostService.getPublished(idOrSlug)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Get published blog post error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }
}
