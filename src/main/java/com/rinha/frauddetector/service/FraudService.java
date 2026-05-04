package com.rinha.frauddetector.service;

import com.rinha.frauddetector.engine.FraudDetectionEngine;
import org.springframework.stereotype.Service;

@Service
public class FraudService {
  private final FraudDetectionEngine fraudDetectionEngine;

  public FraudService(FraudDetectionEngine fraudDetectionEngine) {
    this.fraudDetectionEngine = fraudDetectionEngine;
  }
}
