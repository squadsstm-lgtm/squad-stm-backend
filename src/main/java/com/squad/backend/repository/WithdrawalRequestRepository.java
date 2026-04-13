package com.squad.backend.repository;

import com.squad.backend.model.WithdrawalRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WithdrawalRequestRepository extends MongoRepository<WithdrawalRequest, String> {

    // Base list queries
    List<WithdrawalRequest> findByClubId(String clubId);
    Page<WithdrawalRequest> findByClubId(String clubId, Pageable pageable);

    List<WithdrawalRequest> findByStatus(String status);
    Page<WithdrawalRequest> findByStatus(String status, Pageable pageable);

    List<WithdrawalRequest> findByStatusOrderByRequestedAtDesc(String status);

    List<WithdrawalRequest> findAllByOrderByRequestedAtDesc();

    List<WithdrawalRequest> findByClubIdAndStatus(String clubId, String status);
    Page<WithdrawalRequest> findByClubIdAndStatus(String clubId, String status, Pageable pageable);

    // Status alias query (e.g. WAITING_FOR_APPROVAL + legacy pending)
    List<WithdrawalRequest> findByClubIdAndStatusIn(String clubId, List<String> statuses);
    Page<WithdrawalRequest> findByClubIdAndStatusIn(String clubId, List<String> statuses, Pageable pageable);

    // Search-compatible queries (createdBy ids resolved in service layer)
    Page<WithdrawalRequest> findByClubIdAndCreatedByIn(String clubId, List<String> createdByIds, Pageable pageable);
    Page<WithdrawalRequest> findByClubIdAndStatusAndCreatedByIn(String clubId, String status, List<String> createdByIds, Pageable pageable);
    Page<WithdrawalRequest> findByClubIdAndStatusInAndCreatedByIn(String clubId, List<String> statuses, List<String> createdByIds, Pageable pageable);
    
    // Unique external references
    Optional<WithdrawalRequest> findByStripeTransferId(String stripeTransferId);
    
    Optional<WithdrawalRequest> findByBankReference(String bankReference);
}
