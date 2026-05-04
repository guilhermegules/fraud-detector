package com.rinha.frauddetector.dto;

public record FraudResponse(
    boolean approved,
    double fraud_score
) {}
