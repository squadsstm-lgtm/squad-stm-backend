package com.squad.backend.repository;

import com.squad.backend.model.BlogPost;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface BlogPostRepository extends MongoRepository<BlogPost, String> {
    List<BlogPost> findByStatusOrderByPublishedAtDesc(String status);

    List<BlogPost> findAllByOrderByUpdatedAtDesc();

    Optional<BlogPost> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, String id);
}
