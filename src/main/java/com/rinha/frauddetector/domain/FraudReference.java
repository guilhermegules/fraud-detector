package com.rinha.frauddetector.domain;

public record FraudReference(short[] vectors, boolean[] labels) {}
