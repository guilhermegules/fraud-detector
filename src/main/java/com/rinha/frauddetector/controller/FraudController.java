package com.rinha.frauddetector.controller;

import com.rinha.frauddetector.domain.FraudDetectionService;
import com.rinha.frauddetector.domain.FraudScore;
import com.rinha.frauddetector.domain.TransactionVector;
import com.rinha.frauddetector.dto.FraudRequest;
import com.rinha.frauddetector.dto.FraudResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
public class FraudController {

    private final FraudDetectionService fraudDetectionService;

    public FraudController(FraudDetectionService fraudDetectionService) {
        this.fraudDetectionService = fraudDetectionService;
    }

    @PostMapping("/fraud-score")
    public ResponseEntity<FraudResponse> fraudScore(@RequestBody FraudRequest request) {
        FraudScore score = fraudDetectionService.evaluate(adaptRequest(request));
        return ResponseEntity.ok(new FraudResponse(score.approved(), score.score()));
    }

    private TransactionVector adaptRequest(FraudRequest request) {
        final var transaction = request.transaction();
        final var customer = request.customer();
        final var merchant = request.merchant();
        final var terminal = request.terminal();
        final var lastTransaction = request.last_transaction();

        final Instant instant = Instant.parse(transaction.requested_at());
        final int hour = instant.atZone(java.time.ZoneOffset.UTC).getHour();

        final double amountRatio = customer.avg_amount() > 0
                ? transaction.amount() / customer.avg_amount()
                : 0.0;

        return TransactionVector.fromRequest(
                transaction.amount(),
                transaction.installments(),
                customer.avg_amount(),
                customer.tx_count_24h(),
                merchant.avg_amount(),
                terminal.km_from_home(),
                terminal.is_online(),
                terminal.card_present(),
                lastTransaction != null,
                lastTransaction != null ? lastTransaction.km_from_current() : 0.0,
                merchant.mcc(),
                customer.known_merchants().contains(merchant.id()),
                hour,
                amountRatio
        );
    }
}
