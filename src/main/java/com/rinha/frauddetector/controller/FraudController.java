package com.rinha.frauddetector.controller;

import com.rinha.frauddetector.domain.FraudDetectionService;
import com.rinha.frauddetector.domain.FraudScore;
import com.rinha.frauddetector.domain.TransactionVector;
import com.rinha.frauddetector.dto.FraudRequest;
import com.rinha.frauddetector.dto.FraudResponse;
import com.rinha.frauddetector.adapter.loader.ReferenceLoader;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FraudController {

    private final FraudDetectionService fraudDetectionService;
    private final ReferenceLoader referenceLoader;

    public FraudController(FraudDetectionService fraudDetectionService, ReferenceLoader referenceLoader) {
        this.fraudDetectionService = fraudDetectionService;
        this.referenceLoader = referenceLoader;
    }

    @PostMapping("/fraud-score")
    public ResponseEntity<FraudResponse> fraudScore(@RequestBody FraudRequest request) {
        TransactionVector vector = TransactionVector.fromRequest(
            request,
            referenceLoader.getNormalizationConstants(),
            referenceLoader.getMccRiskMap()
        );
        FraudScore score = fraudDetectionService.evaluate(vector);
        return ResponseEntity.ok(new FraudResponse(score.approved(), score.score()));
    }
}
