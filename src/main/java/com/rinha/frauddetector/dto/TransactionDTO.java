package com.rinha.frauddetector.dto;

public record TransactionDTO(
    double amount,
    int installments,
    String requested_at
) {}
