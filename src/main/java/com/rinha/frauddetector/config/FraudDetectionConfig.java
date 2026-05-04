package com.rinha.frauddetector.config;

import com.rinha.frauddetector.application.KnnFraudDetectionService;
import com.rinha.frauddetector.domain.FraudDetectionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FraudDetectionConfig {

  @Bean
  public FraudDetectionService fraudDetectionService() {
    return new KnnFraudDetectionService();
  }
}
