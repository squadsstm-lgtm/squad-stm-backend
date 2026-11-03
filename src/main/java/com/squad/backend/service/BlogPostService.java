package com.squad.backend.service;

import com.squad.backend.dto.request.blog.UpsertBlogPostRequest;
import com.squad.backend.dto.response.blog.BlogPostResponse;
import com.squad.backend.dto.response.masterpanel.ControllerPermissionsResponse;
import com.squad.backend.model.Auth;
import com.squad.backend.model.BlogPost;
import com.squad.backend.repository.AuthRepository;
import com.squad.backend.repository.BlogPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BlogPostService {

    private static final int MAX_COVER_CHARS = 1_800_000; // ~1.3MB base64
    private static final Pattern DATA_IMAGE = Pattern.compile("^data:image/(jpeg|jpg|png|webp|gif);base64,", Pattern.CASE_INSENSITIVE);

    private final BlogPostRepository blogPostRepository;
    private final AuthRepository authRepository;
    private final ControllerPermissionsService controllerPermissionsService;

    public List<BlogPostResponse> listPublished() {
        return blogPostRepository.findByStatusOrderByPublishedAtDesc(BlogPost.STATUS_PUBLISHED).stream()
                .map(this::toPublicResponse)
                .collect(Collectors.toList());
    }

    public BlogPostResponse getPublished(String idOrSlug) {
        BlogPost post = findByIdOrSlug(idOrSlug);
        if (!BlogPost.STATUS_PUBLISHED.equalsIgnoreCase(post.getStatus())) {
            throw new IllegalArgumentException("Blog post not found");
        }
        return toPublicResponse(post);
    }

    public List<BlogPostResponse> listAllForController(Auth actor) {
        requireManageBlog(actor);
        return blogPostRepository.findAllByOrderByUpdatedAtDesc().stream()
                .map(this::toAdminResponse)
                .collect(Collectors.toList());
    }

    public BlogPostResponse getForController(String id, Auth actor) {
        requireManageBlog(actor);
        return toAdminResponse(findById(id));
    }

    public BlogPostResponse create(UpsertBlogPostRequest request, Auth actor) {
        requireManageBlog(actor);
        String status = normalizeStatus(request.getStatus(), BlogPost.STATUS_DRAFT);
        validateCover(request.getCoverImage());

        BlogPost post = new BlogPost();
        post.setTitle(request.getTitle().trim());
        post.setSummary(request.getSummary().trim());
        post.setBody(request.getBody().trim());
        post.setCoverImage(blankToNull(request.getCoverImage()));
        post.setSlug(uniqueSlug(slugify(post.getTitle()), null));
        post.setStatus(status);
        post.setCreatedBy(actor.getId());
        post.setUpdatedBy(actor.getId());
        Instant now = Instant.now();
        post.setCreatedAt(now);
        post.setUpdatedAt(now);
        if (BlogPost.STATUS_PUBLISHED.equals(status)) {
            post.setPublishedAt(now);
        }
        return toAdminResponse(blogPostRepository.save(post));
    }

    public BlogPostResponse update(String id, UpsertBlogPostRequest request, Auth actor) {
        requireManageBlog(actor);
        BlogPost post = findById(id);
        String status = normalizeStatus(request.getStatus(), post.getStatus());
        validateCover(request.getCoverImage());

        post.setTitle(request.getTitle().trim());
        post.setSummary(request.getSummary().trim());
        post.setBody(request.getBody().trim());
        post.setCoverImage(blankToNull(request.getCoverImage()));
        post.setSlug(uniqueSlug(slugify(post.getTitle()), post.getId()));
        applyStatusChange(post, status);
        post.setUpdatedBy(actor.getId());
        post.setUpdatedAt(Instant.now());
        return toAdminResponse(blogPostRepository.save(post));
    }

    public BlogPostResponse setStatus(String id, String status, Auth actor) {
        requireManageBlog(actor);
        BlogPost post = findById(id);
        applyStatusChange(post, normalizeStatus(status, post.getStatus()));
        post.setUpdatedBy(actor.getId());
        post.setUpdatedAt(Instant.now());
        return toAdminResponse(blogPostRepository.save(post));
    }

    public void delete(String id, Auth actor) {
        requireManageBlog(actor);
        BlogPost post = findById(id);
        blogPostRepository.delete(post);
    }

    private void requireManageBlog(Auth actor) {
        controllerPermissionsService.requirePermission(actor, ControllerPermissionsResponse::getManageBlog);
    }

    private void applyStatusChange(BlogPost post, String status) {
        String previous = post.getStatus();
        post.setStatus(status);
        if (BlogPost.STATUS_PUBLISHED.equals(status)) {
            if (post.getPublishedAt() == null || !BlogPost.STATUS_PUBLISHED.equalsIgnoreCase(previous)) {
                post.setPublishedAt(Instant.now());
            }
        }
    }

    private BlogPost findById(String id) {
        return blogPostRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Blog post not found"));
    }

    private BlogPost findByIdOrSlug(String idOrSlug) {
        return blogPostRepository.findById(idOrSlug)
                .or(() -> blogPostRepository.findBySlug(idOrSlug))
                .orElseThrow(() -> new IllegalArgumentException("Blog post not found"));
    }

    private String normalizeStatus(String status, String fallback) {
        String value = status != null && !status.isBlank() ? status.trim().toUpperCase(Locale.ROOT) : fallback;
        if (!BlogPost.STATUS_DRAFT.equals(value) && !BlogPost.STATUS_PUBLISHED.equals(value)) {
            throw new IllegalArgumentException("Status must be DRAFT or PUBLISHED");
        }
        return value;
    }

    private void validateCover(String coverImage) {
        if (coverImage == null || coverImage.isBlank()) {
            return;
        }
        String trimmed = coverImage.trim();
        if (trimmed.length() > MAX_COVER_CHARS) {
            throw new IllegalArgumentException("Cover image is too large. Use an image under about 1 MB.");
        }
        if (!DATA_IMAGE.matcher(trimmed).find() && !trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw new IllegalArgumentException("Cover image must be an uploaded image or a valid image URL");
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String slugify(String title) {
        String base = title.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-")
                .replaceAll("-{2,}", "-");
        if (base.isBlank()) {
            base = "post";
        }
        if (base.length() > 80) {
            base = base.substring(0, 80).replaceAll("-+$", "");
        }
        return base;
    }

    private String uniqueSlug(String base, String excludeId) {
        String candidate = base;
        int i = 2;
        while (true) {
            boolean taken = excludeId == null
                    ? blogPostRepository.existsBySlug(candidate)
                    : blogPostRepository.existsBySlugAndIdNot(candidate, excludeId);
            if (!taken) {
                return candidate;
            }
            candidate = base + "-" + i;
            i++;
        }
    }

    private BlogPostResponse toPublicResponse(BlogPost post) {
        return BlogPostResponse.builder()
                .id(post.getId())
                .title(post.getTitle())
                .slug(post.getSlug())
                .summary(post.getSummary())
                .body(post.getBody())
                .coverImage(post.getCoverImage())
                .status(post.getStatus())
                .publishedAt(post.getPublishedAt())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }

    private BlogPostResponse toAdminResponse(BlogPost post) {
        BlogPostResponse response = toPublicResponse(post);
        response.setUpdatedByName(displayName(post.getUpdatedBy()));
        return response;
    }

    private String displayName(String authId) {
        if (authId == null || authId.isBlank()) {
            return "Unknown user";
        }
        return authRepository.findById(authId)
                .map(auth -> {
                    String name = ((auth.getFirstName() != null ? auth.getFirstName() : "") + " "
                            + (auth.getLastName() != null ? auth.getLastName() : "")).trim();
                    if (!name.isEmpty()) {
                        return name;
                    }
                    if (auth.getEmail() != null && !auth.getEmail().isBlank()) {
                        return auth.getEmail();
                    }
                    return "Unknown user";
                })
                .orElse("Unknown user");
    }
}
