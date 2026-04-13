package com.squad.backend.repository;

import com.squad.backend.model.StripeCustomer;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StripeCustomerRepository extends MongoRepository<StripeCustomer, String> {
    
    Optional<StripeCustomer> findByStripeCustomerId(String stripeCustomerId);
    
    Optional<StripeCustomer> findByPlayerId(String playerId);
    
    Optional<StripeCustomer> findByUserId(String userId);
    
    List<StripeCustomer> findByClubId(String clubId);
    
    boolean existsByStripeCustomerId(String stripeCustomerId);
    
    Optional<StripeCustomer> findByPlayerIdAndClubId(String playerId, String clubId);
    
    Optional<StripeCustomer> findByUserIdAndClubId(String userId, String clubId);
}
