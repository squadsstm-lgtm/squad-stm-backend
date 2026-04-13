package com.squad.backend.repository;

import com.squad.backend.model.Team;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamRepository extends MongoRepository<Team, String> {

    /** Find teams whose playersList contains the given player id (for active-session check). */
    @Query("{ 'playersList': { $regex: ?0 } }")
    List<Team> findByPlayersListContainingPlayerId(String playerId);

    List<Team> findByClubId(String clubId);
    
    List<Team> findByClubIdAndSeasonId(String clubId, String seasonId);
    
    List<Team> findByClubIdAndSeasonIdAndIsActive(String clubId, String seasonId, Boolean isActive);
    
    Optional<Team> findByTeamNameAndClubId(String teamName, String clubId);
    
    long countByClubIdAndSeasonIdAndIsActive(String clubId, String seasonId, Boolean isActive);
    
    // Search teams by teamName (case-insensitive regex, like Node.js)
    @Query("{ 'clubId': ?0, 'seasonId': ?1, 'isActive': ?2, 'teamName': { $regex: ?3, $options: 'i' } }")
    List<Team> findByClubIdAndSeasonIdAndIsActiveAndTeamNameRegex(String clubId, String seasonId, Boolean isActive, String search);
}
