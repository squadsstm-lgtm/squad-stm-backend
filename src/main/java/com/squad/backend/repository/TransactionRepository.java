package com.squad.backend.repository;

import com.squad.backend.model.Transaction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends MongoRepository<Transaction, String> {
    
    List<Transaction> findByClubId(String clubId);
    
    List<Transaction> findByClubIdAndSeasonId(String clubId, String seasonId);
    
    List<Transaction> findByPlayerId(String playerId);
    
    List<Transaction> findBySessionId(String sessionId);
    
    List<Transaction> findByStripeCustomerId(String stripeCustomerId);
    
    Optional<Transaction> findByStripeTransactionId(String stripeTransactionId);
    
    List<Transaction> findByStatus(String status);
    
    List<Transaction> findByType(String type);
    
    List<Transaction> findByClubIdAndStatus(String clubId, String status);
    
    List<Transaction> findByCreatedAtBetween(Instant start, Instant end);
    
    List<Transaction> findByClubIdAndTypeAndStatusAndMoneyLockedTrue(String clubId, String type, String status);
    
    Optional<Transaction> findBySessionIdAndPlayerIdAndStatus(String sessionId, String playerId, String status);
}
