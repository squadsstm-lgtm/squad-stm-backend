package com.squad.backend.repository;

import com.squad.backend.model.Season;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SeasonRepository extends MongoRepository<Season, String> {
    
    Optional<Season> findByActive(Boolean active);
    
    List<Season> findAllByOrderByStartDateDesc();
}
