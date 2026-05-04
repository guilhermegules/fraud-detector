package com.rinha.frauddetector.dto;

public record TerminalDTO(
    boolean is_online,
    boolean card_present,
    double km_from_home
) {}
