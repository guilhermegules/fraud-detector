package com.rinha.frauddetector.config;

import com.rinha.frauddetector.adapter.loader.ReferenceLoader;
import com.rinha.frauddetector.application.KnnFraudDetectionService;
import com.rinha.frauddetector.domain.FraudDetectionService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class TestFraudDetectionConfig {

  @Bean
  public ReferenceLoader referenceLoader() {
    return new ReferenceLoader();
  }

  @Bean
  public FraudDetectionService fraudDetectionService(ReferenceLoader referenceLoader) {
    return new KnnFraudDetectionService(referenceLoader);
  }
}
