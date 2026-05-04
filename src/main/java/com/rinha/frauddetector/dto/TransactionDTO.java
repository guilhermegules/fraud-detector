package com.rinha.frauddetector.dto;

public record TransactionDTO(
    float amount,
    int installments,
    String requested_at
) {}
