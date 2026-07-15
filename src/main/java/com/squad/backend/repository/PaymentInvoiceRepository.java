package com.squad.backend.repository;

import com.squad.backend.model.PaymentInvoice;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PaymentInvoiceRepository extends MongoRepository<PaymentInvoice, String> {
    List<PaymentInvoice> findByPlayerIdAndClubIdAndStatus(String playerId, String clubId, String status);
}
