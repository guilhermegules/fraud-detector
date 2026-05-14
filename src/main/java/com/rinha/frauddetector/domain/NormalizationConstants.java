package com.rinha.frauddetector.domain;

public record NormalizationConstants(
    float max_amount,
    float max_installments,
    float amount_vs_avg_ratio,
    float max_minutes,
    float max_km,
    float max_tx_count_24h,
    float max_merchant_avg_amount) {}
