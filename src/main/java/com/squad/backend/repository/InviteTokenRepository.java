package com.squad.backend.repository;

import com.squad.backend.model.InviteToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InviteTokenRepository extends MongoRepository<InviteToken, String> {

    Optional<InviteToken> findByCode(String code);

    @Query("{ 'purpose': ?0, 'entityId': ?1, 'usedAt': null, 'revokedAt': null }")
    List<InviteToken> findActiveByPurposeAndEntityId(String purpose, String entityId);
}
