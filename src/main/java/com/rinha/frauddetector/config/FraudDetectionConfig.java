package com.rinha.frauddetector.config;

import com.rinha.frauddetector.adapter.loader.ReferenceLoader;
import com.rinha.frauddetector.application.KnnFraudDetectionService;
import com.rinha.frauddetector.domain.FraudDetectionService;
import com.rinha.frauddetector.domain.FraudReference;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FraudDetectionConfig {

  @Bean
  public ReferenceLoader referenceLoader() throws Exception {
    ReferenceLoader loader = new ReferenceLoader();
    loader.loadAll();
    return loader;
  }

  @Bean
  public FraudDetectionService fraudDetectionService(ReferenceLoader referenceLoader) {
    KnnFraudDetectionService service = new KnnFraudDetectionService();
    FraudReference data = referenceLoader.getFraudReference();
    service.loadDataset(data.vectors(), data.labels());
    return service;
  }
}
