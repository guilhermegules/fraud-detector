package com.rinha.frauddetector.dto;

public record MerchantDTO(
    String id,
    String mcc,
    float avg_amount
) {}
