package com.rinha.frauddetector.dto;

public record LastTransactionDTO(
    String timestamp,
    float km_from_current
) {}
