package com.squad.backend.repository;

import com.squad.backend.model.Session;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface SessionRepository extends MongoRepository<Session, String> {
    
    List<Session> findByClubId(String clubId);
    
    List<Session> findByClubIdAndSeasonId(String clubId, String seasonId);
    
    List<Session> findByClubIdAndSeasonIdAndIsActive(String clubId, String seasonId, Boolean isActive);
    
    @Query("{ 'clubId': ?0, 'seasonId': ?1, 'isActive': ?2, 'teamList': { $regex: ?3 } }")
    List<Session> findByClubIdAndSeasonIdAndIsActiveAndTeamListRegex(String clubId, String seasonId, Boolean isActive, String teamId);
    
    @Query("{ 'clubId': ?0, 'seasonId': ?1, 'isActive': ?2, 'additionalPlayers': { $regex: ?3 } }")
    List<Session> findByClubIdAndSeasonIdAndIsActiveAndAdditionalPlayersRegex(String clubId, String seasonId, Boolean isActive, String playerId);
    
    @Query(value = "{ 'clubId': ?0, 'seasonId': ?1, 'isActive': ?2, 'teamList': { $regex: ?3 }, 'sessionType': ?4 }", count = true)
    Long countByClubIdAndSeasonIdAndIsActiveAndTeamListRegexAndSessionType(String clubId, String seasonId, Boolean isActive, String teamId, String sessionType);
    
    @Query(value = "{ 'clubId': ?0, 'seasonId': ?1, 'isActive': ?2 }", count = true)
    Long countByClubIdAndSeasonIdAndIsActive(String clubId, String seasonId, Boolean isActive);

    long countByClubIdAndSeasonIdAndCreatedAtGreaterThanEqual(String clubId, String seasonId, Instant startOfMonth);
    
    List<Session> findByClubIdAndIdIn(String clubId, List<String> sessionIds);
}
