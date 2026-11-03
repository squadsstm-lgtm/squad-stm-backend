package com.squad.backend.controller;

import com.squad.backend.constants.ErrorMessages;
import com.squad.backend.dto.request.blog.UpsertBlogPostRequest;
import com.squad.backend.dto.response.ApiResponse;
import com.squad.backend.dto.response.blog.BlogPostResponse;
import com.squad.backend.model.Auth;
import com.squad.backend.service.BlogPostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/master-panel/blog")
@RequiredArgsConstructor
@Slf4j
public class MasterPanelBlogController {

    private final BlogPostService blogPostService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BlogPostResponse>>> list(
            @AuthenticationPrincipal Auth auth) {
        try {
            return ResponseEntity.ok(ApiResponse.success(blogPostService.listAllForController(auth)));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Master panel list blog posts error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BlogPostResponse>> get(
            @PathVariable String id,
            @AuthenticationPrincipal Auth auth) {
        try {
            return ResponseEntity.ok(ApiResponse.success(blogPostService.getForController(id, auth)));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Master panel get blog post error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BlogPostResponse>> create(
            @Valid @RequestBody UpsertBlogPostRequest request,
            @AuthenticationPrincipal Auth auth) {
        try {
            BlogPostResponse data = blogPostService.create(request, auth);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(data, "Blog post saved."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Master panel create blog post error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BlogPostResponse>> update(
            @PathVariable String id,
            @Valid @RequestBody UpsertBlogPostRequest request,
            @AuthenticationPrincipal Auth auth) {
        try {
            BlogPostResponse data = blogPostService.update(id, request, auth);
            return ResponseEntity.ok(ApiResponse.success(data, "Blog post updated."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Master panel update blog post error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<BlogPostResponse>> setStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal Auth auth) {
        try {
            String status = body != null ? body.get("status") : null;
            BlogPostResponse data = blogPostService.setStatus(id, status, auth);
            return ResponseEntity.ok(ApiResponse.success(data, "Blog post status updated."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Master panel blog status error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable String id,
            @AuthenticationPrincipal Auth auth) {
        try {
            blogPostService.delete(id, auth);
            return ResponseEntity.ok(ApiResponse.success(null, "Blog post deleted."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Master panel delete blog post error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }
}
