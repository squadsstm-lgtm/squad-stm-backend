package com.squad.backend.repository;

import com.squad.backend.model.Count;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CountRepository extends MongoRepository<Count, String> {
    
    Optional<Count> findByClubIdAndSeasonId(String clubId, String seasonId);
}
