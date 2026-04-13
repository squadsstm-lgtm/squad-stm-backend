package com.squad.backend.repository;

import com.squad.backend.model.ClubWallet;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClubWalletRepository extends MongoRepository<ClubWallet, String> {
    
    Optional<ClubWallet> findByClubId(String clubId);
    
    boolean existsByClubId(String clubId);
}
