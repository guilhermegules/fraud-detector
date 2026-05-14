package com.rinha.frauddetector.dto;

public record TerminalDTO(
    boolean is_online,
    boolean card_present,
    float km_from_home
) {}
