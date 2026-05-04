package com.rinha.frauddetector.dto;

public record FraudResponse(
    boolean approved,
    float fraud_score
) {}
