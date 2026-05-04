package com.rinha.frauddetector.dto;

public record MerchantDTO(
    String id,
    String mcc,
    double avg_amount
) {}
