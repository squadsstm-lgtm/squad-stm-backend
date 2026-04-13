package com.squad.backend.repository;

import com.squad.backend.model.Player;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerRepository extends MongoRepository<Player, String> {
    
    List<Player> findByClubId(String clubId);
    
    List<Player> findByClubIdAndSeasonId(String clubId, String seasonId);
    
    List<Player> findByClubIdAndSeasonIdAndIsActive(String clubId, String seasonId, Boolean isActive);
    
    // Query players by teamId in teams Map (using dot notation for Map keys)
    @Query("{ 'teams': { $exists: true, $ne: null }, 'teams.?0': { $exists: true } }")
    List<Player> findByTeamsContainingKey(String teamId);
    
    @Query("{ 'teams': { $exists: true, $ne: null }, 'teams.?0': { $exists: true }, 'isActive': ?1 }")
    List<Player> findByTeamsContainingKeyAndIsActive(String teamId, Boolean isActive);
    
    Optional<Player> findByEmail(String email);
    
    List<Player> findByPhone(String phone);
    
    @Query("{ 'clubId': ?0, 'phone': ?1 }")
    Optional<Player> findByClubIdAndPhone(String clubId, String phone);
    
    @Query("{ $or: [ { 'clubId': null }, { 'clubId': '' }, { 'clubId': { $exists: false } } ], 'email': ?0 }")
    Optional<Player> findUncategorizedByEmail(String email);
    
    long countByClubIdAndSeasonIdAndIsActive(String clubId, String seasonId, Boolean isActive);
    
    @Query(value = "{ 'clubId': ?0, 'seasonId': ?1, 'firstName': { $ne: '' }, 'lastName': { $ne: '' }, 'isActive': ?2 }", count = true)
    Long countPlayersWithNames(String clubId, String seasonId, Boolean isActive);
}
