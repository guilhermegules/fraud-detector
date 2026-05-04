package com.rinha.frauddetector.adapter.loader;

import com.rinha.frauddetector.domain.FraudReference;
import com.rinha.frauddetector.domain.NormalizationConstants;
import com.rinha.frauddetector.domain.TransactionVector;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReferenceLoaderTest {

  @Test
  void shouldLoadNormalizationConstants() throws Exception {
    ReferenceLoader loader = new ReferenceLoader();
    loader.loadAll();

    NormalizationConstants constants = loader.getNormalizationConstants();

    assertNotNull(constants);
    assertEquals(10000, constants.max_amount(), 0.001);
    assertEquals(12, constants.max_installments(), 0.001);
    assertEquals(10, constants.amount_vs_avg_ratio(), 0.001);
    assertEquals(1440, constants.max_minutes(), 0.001);
    assertEquals(1000, constants.max_km(), 0.001);
    assertEquals(20, constants.max_tx_count_24h(), 0.001);
    assertEquals(10000, constants.max_merchant_avg_amount(), 0.001);
  }

  @Test
  void shouldLoadMccRiskMap() throws Exception {
    ReferenceLoader loader = new ReferenceLoader();
    loader.loadAll();

    Map<String, Float> mccRiskMap = loader.getMccRiskMap();

    assertNotNull(mccRiskMap);
    assertEquals(0.15f, mccRiskMap.get("5411"), 0.001f);
    assertEquals(0.30f, mccRiskMap.get("5812"), 0.001f);
    assertEquals(0.20f, mccRiskMap.get("5912"), 0.001f);
    assertEquals(0.50f, mccRiskMap.getOrDefault("9999", 0.5f), 0.001f);
  }

  @Test
  void shouldLoadFraudReference() throws Exception {
    ReferenceLoader loader = new ReferenceLoader();
    loader.loadAll();

    FraudReference fraudReference = loader.getFraudReference();

    assertNotNull(fraudReference);
    assertNotNull(fraudReference.vectors());
    assertNotNull(fraudReference.labels());
    assertEquals(fraudReference.vectors().length, fraudReference.labels().length);
    assertTrue(fraudReference.vectors().length > 0);
  }

  @Test
  void shouldHaveCorrectVectorSize() throws Exception {
    ReferenceLoader loader = new ReferenceLoader();
    loader.loadAll();

    FraudReference fraudReference = loader.getFraudReference();
    TransactionVector[] vectors = fraudReference.vectors();

    assertEquals(14, vectors[0].features().length);
  }
}
