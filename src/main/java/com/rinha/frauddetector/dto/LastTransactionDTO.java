package com.rinha.frauddetector.dto;

public record LastTransactionDTO(
    String timestamp,
    double km_from_current
) {}
