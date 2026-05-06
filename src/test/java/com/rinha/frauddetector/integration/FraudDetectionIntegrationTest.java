package com.rinha.frauddetector.integration;

import com.rinha.frauddetector.adapter.loader.ReferenceLoader;
import com.rinha.frauddetector.application.KnnFraudDetectionService;
import com.rinha.frauddetector.domain.FraudDetectionService;
import com.rinha.frauddetector.domain.FraudScore;
import com.rinha.frauddetector.dto.FraudRequest;
import com.rinha.frauddetector.dto.TransactionDTO;
import com.rinha.frauddetector.dto.CustomerDTO;
import com.rinha.frauddetector.dto.MerchantDTO;
import com.rinha.frauddetector.dto.TerminalDTO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FraudDetectionIntegrationTest {

  private static FraudDetectionService service;
  private static ReferenceLoader loader;

  @BeforeAll
  static void setup() throws Exception {
    loader = new ReferenceLoader();
    loader.loadNormalization();
    loader.loadMccRisk();
    loader.loadFraudReference();

    service = new KnnFraudDetectionService(loader);
    ((KnnFraudDetectionService) service).initialize();
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

    System.out.println("Legitimate tx score: " + score.score() + ", approved: " + score.approved());

    assertNotNull(score);
    assertTrue(score.score() < 0.9, "Score should be less than 0.9");
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

    System.out.println("Fraud score for fraudulent transaction: " + score.score() + ", approved: " + score.approved());

    assertNotNull(score);
    assertFalse(score.approved(), "Fraudulent transaction should not be approved");
    assertTrue(score.score() >= 0.6, "Score should be >= 0.6 for fraud");
  }
}
