package com.rinha.frauddetector.controller;

import com.rinha.frauddetector.domain.FraudDetectionService;
import com.rinha.frauddetector.domain.FraudScore;
import com.rinha.frauddetector.domain.TransactionVector;
import com.rinha.frauddetector.dto.FraudRequest;
import com.rinha.frauddetector.dto.FraudResponse;
import com.rinha.frauddetector.adapter.loader.ReferenceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FraudController {

    private static final Logger log = LoggerFactory.getLogger(FraudController.class);

    private final FraudDetectionService fraudDetectionService;
    private final ReferenceLoader referenceLoader;

    public FraudController(FraudDetectionService fraudDetectionService, ReferenceLoader referenceLoader) {
        this.fraudDetectionService = fraudDetectionService;
        this.referenceLoader = referenceLoader;
    }

    @PostMapping("/fraud-score")
    public ResponseEntity<FraudResponse> fraudScore(@RequestBody FraudRequest request) {
        long start = System.currentTimeMillis();

        log.debug("Processing fraud-score request for transaction: {}", request.id());

        TransactionVector vector = TransactionVector.fromRequest(
            request,
            referenceLoader.getNormalizationConstants(),
            referenceLoader.getMccRiskMap()
        );

        log.debug("Vectorization completed in {}ms", System.currentTimeMillis() - start);

        FraudScore score = fraudDetectionService.evaluate(vector);

        long totalTime = System.currentTimeMillis() - start;
        log.info("Fraud-score request completed: id={}, approved={}, score={}, time={}ms",
            request.id(), score.approved(), score.score(), totalTime);

        if (totalTime > 500) {
            log.warn("SLOW REQUEST: transaction {} took {}ms", request.id(), totalTime);
        }

        return ResponseEntity.ok(new FraudResponse(score.approved(), score.score()));
    }
}
