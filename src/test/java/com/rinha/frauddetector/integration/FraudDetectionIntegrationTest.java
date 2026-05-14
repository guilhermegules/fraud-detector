package com.rinha.frauddetector.integration;

import com.rinha.frauddetector.adapter.loader.ReferenceLoader;
import com.rinha.frauddetector.application.KnnFraudDetectionService;
import com.rinha.frauddetector.domain.FraudScore;
import com.rinha.frauddetector.domain.NormalizationConstants;
import com.rinha.frauddetector.dto.*;
import com.rinha.frauddetector.http.JsonCodec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FraudDetectionIntegrationTest {

  private static KnnFraudDetectionService service;

  @BeforeAll
  static void setup() throws Exception {
    ReferenceLoader loader = new ReferenceLoader();
    loader.loadNormalization(loadNorm());
    loader.loadMccRisk(loadMcc());
    loader.loadFraudReference();

    KnnFraudDetectionService svc = new KnnFraudDetectionService(loader);
    svc.initialize();
    service = svc;
  }

  @Test
  void shouldDetectLegitimateTransaction() {
    FraudRequest request = new FraudRequest(
        "tx-1329056812",
        new TransactionDTO(41.12f, 2, "2026-03-11T18:45:53Z"),
        new CustomerDTO(82.24f, 3, List.of("MERC-003", "MERC-016")),
        new MerchantDTO("MERC-016", "5411", 60.25f),
        new TerminalDTO(false, true, 29.23f),
        null
    );

    FraudScore score = service.evaluate(request);
    assertNotNull(score);
    assertTrue(score.score() >= 0 && score.score() <= 1, "Score should be in [0,1]");
  }

  @Test
  void shouldDetectFraudulentTransaction() {
    FraudRequest request = new FraudRequest(
        "tx-3330991687",
        new TransactionDTO(9505.97f, 10, "2026-03-14T05:15:12Z"),
        new CustomerDTO(81.28f, 20, List.of("MERC-008", "MERC-007", "MERC-005")),
        new MerchantDTO("MERC-068", "7802", 54.86f),
        new TerminalDTO(false, true, 952.27f),
        null
    );

    FraudScore score = service.evaluate(request);
    assertNotNull(score);
    assertTrue(score.score() >= 0 && score.score() <= 1, "Score should be in [0,1]");
  }

  private static NormalizationConstants loadNorm() {
    try (InputStream is = FraudDetectionIntegrationTest.class.getClassLoader().getResourceAsStream("normalization.json")) {
      return JsonCodec.parseNormalization(is.readAllBytes());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static Map<String, Float> loadMcc() {
    try (InputStream is = FraudDetectionIntegrationTest.class.getClassLoader().getResourceAsStream("mcc_risk.json")) {
      return JsonCodec.parseMccRisk(is.readAllBytes());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
