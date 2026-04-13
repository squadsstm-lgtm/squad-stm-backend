package com.squad.backend.repository;

import com.squad.backend.model.Club;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClubRepository extends MongoRepository<Club, String> {
    
    Optional<Club> findByClubName(String clubName);
    
    Optional<Club> findByClubNameIgnoreCase(String clubName);
    
    List<Club> findBySeasonId(String seasonId);
}
