package com.rinha.frauddetector.config;

import com.rinha.frauddetector.adapter.loader.ReferenceLoader;
import com.rinha.frauddetector.application.KnnFraudDetectionService;
import com.rinha.frauddetector.domain.FraudDetectionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FraudDetectionConfig {

  @Bean
  public ReferenceLoader referenceLoader() throws Exception {
    ReferenceLoader referenceLoader = new ReferenceLoader();
    referenceLoader.loadNormalization();
    referenceLoader.loadMccRisk();
    return referenceLoader;
  }

  @Bean
  public FraudDetectionService fraudDetectionService(ReferenceLoader referenceLoader) {
    return new KnnFraudDetectionService(referenceLoader);
  }
}
