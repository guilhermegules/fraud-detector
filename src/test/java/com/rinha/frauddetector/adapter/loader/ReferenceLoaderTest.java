package com.rinha.frauddetector.adapter.loader;

import com.rinha.frauddetector.domain.FraudReference;
import com.rinha.frauddetector.domain.NormalizationConstants;
import com.rinha.frauddetector.http.JsonCodec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReferenceLoaderTest {

  @Test
  void shouldLoadNormalizationConstants() {
    ReferenceLoader loader = new ReferenceLoader();
    NormalizationConstants constants = loadNorm();
    loader.loadNormalization(constants);

    NormalizationConstants result = loader.getNormalizationConstants();
    assertNotNull(result);
    assertEquals(10000, result.max_amount(), 0.001);
    assertEquals(12, result.max_installments(), 0.001);
    assertEquals(10, result.amount_vs_avg_ratio(), 0.001);
    assertEquals(1440, result.max_minutes(), 0.001);
    assertEquals(1000, result.max_km(), 0.001);
    assertEquals(20, result.max_tx_count_24h(), 0.001);
    assertEquals(10000, result.max_merchant_avg_amount(), 0.001);
  }

  @Test
  void shouldLoadMccRiskMap() {
    ReferenceLoader loader = new ReferenceLoader();
    Map<String, Float> mccRiskMap = loadMcc();
    loader.loadMccRisk(mccRiskMap);

    Map<String, Float> result = loader.getMccRiskMap();
    assertNotNull(result);
    assertEquals(0.15f, result.get("5411"), 0.001f);
    assertEquals(0.30f, result.get("5812"), 0.001f);
    assertEquals(0.20f, result.get("5912"), 0.001f);
    assertEquals(0.50f, result.getOrDefault("9999", 0.5f), 0.001f);
  }

  @Test
  void shouldLoadFraudReference() throws Exception {
    ReferenceLoader loader = new ReferenceLoader();
    loader.loadFraudReference();

    FraudReference fraudReference = loader.getFraudReference();
    assertNotNull(fraudReference);
    assertNotNull(fraudReference.vectors());
    assertNotNull(fraudReference.labels());
    assertEquals(fraudReference.vectors().length / 16, fraudReference.labels().length);
    assertTrue(fraudReference.vectors().length > 0);
  }

  @Test
  void shouldHaveCorrectVectorSize() throws Exception {
    ReferenceLoader loader = new ReferenceLoader();
    loader.loadFraudReference();

    FraudReference fraudReference = loader.getFraudReference();
    short[] vectors = fraudReference.vectors();
    assertEquals(0, vectors.length % 16);
    assertTrue(vectors.length >= 16);
  }

  private static NormalizationConstants loadNorm() {
    try (InputStream is = ReferenceLoaderTest.class.getClassLoader().getResourceAsStream("normalization.json")) {
      return JsonCodec.parseNormalization(is.readAllBytes());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static Map<String, Float> loadMcc() {
    try (InputStream is = ReferenceLoaderTest.class.getClassLoader().getResourceAsStream("mcc_risk.json")) {
      return JsonCodec.parseMccRisk(is.readAllBytes());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
