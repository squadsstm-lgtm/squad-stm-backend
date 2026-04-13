package com.squad.backend.repository;

import com.squad.backend.model.ConfirmationRequest;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConfirmationRequestRepository extends MongoRepository<ConfirmationRequest, String> {
    // Basic scoped reads
    List<ConfirmationRequest> findByClubId(String clubId);

    List<ConfirmationRequest> findByClubIdAndSeasonId(String clubId, String seasonId);

    List<ConfirmationRequest> findBySessionId(String sessionId);

    List<ConfirmationRequest> findByPlayerId(String playerId);

    // Uniqueness checks
    Optional<ConfirmationRequest> findBySessionIdAndPlayerId(String sessionId, String playerId);

    Optional<ConfirmationRequest> findBySessionIdAndPlayerIdAndClubId(String sessionId, String playerId, String clubId);

    // Attendance reporting helper
    List<ConfirmationRequest> findByClubIdAndSessionIdInAndAttendanceMarkedAtNotNull(String clubId, List<String> sessionIds);

    List<ConfirmationRequest> findByClubIdAndSeasonIdAndAttendanceMarkedAtNotNull(String clubId, String seasonId);
}
