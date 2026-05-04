package com.rinha.frauddetector.dto;

import java.util.List;

public record CustomerDTO(
    float avg_amount,
    int tx_count_24h,
    List<String> known_merchants
) {}
