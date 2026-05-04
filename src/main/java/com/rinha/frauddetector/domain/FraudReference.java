package com.rinha.frauddetector.domain;

public record FraudReference(TransactionVector[] vectors, boolean[] labels) {}
