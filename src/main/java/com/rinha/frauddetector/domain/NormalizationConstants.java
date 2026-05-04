package com.rinha.frauddetector.domain;

public record NormalizationConstants(
    double max_amount,
    double max_installments,
    double amount_vs_avg_ratio,
    double max_minutes,
    double max_km,
    double max_tx_count_24h,
    double max_merchant_avg_amount) {}
