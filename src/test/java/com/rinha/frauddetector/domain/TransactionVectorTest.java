package com.rinha.frauddetector.domain;

import com.rinha.frauddetector.dto.FraudRequest;
import com.rinha.frauddetector.dto.TransactionDTO;
import com.rinha.frauddetector.dto.CustomerDTO;
import com.rinha.frauddetector.dto.MerchantDTO;
import com.rinha.frauddetector.dto.TerminalDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransactionVectorTest {

  private static final NormalizationConstants CONSTANTS =
      new NormalizationConstants(10000, 12, 10, 1440, 1000, 20, 10000);

  @Test
  void shouldCreateValidVector() {
    float[] features = new float[14];
    for (int i = 0; i < 14; i++) features[i] = 0.5f;

    TransactionVector vector = new TransactionVector(features);
    assertNotNull(vector);
    assertEquals(14, vector.features().length);
  }

  @Test
  void shouldRejectInvalidSize() {
    float[] features = new float[10];
    assertThrows(IllegalArgumentException.class, () -> new TransactionVector(features));
  }

  @Test
  void shouldCalculateDistance() {
    float[] f1 = new float[14];
    float[] f2 = new float[14];
    for (int i = 0; i < 14; i++) {
      f1[i] = 0.0f;
      f2[i] = 1.0f;
    }

    TransactionVector v1 = new TransactionVector(f1);
    TransactionVector v2 = new TransactionVector(f2);

    float distance = v1.distanceTo(v2);
    assertEquals(14.0f, distance, 0.001f);
  }

  @Test
  void shouldCreateFromRequest() {
    FraudRequest request =
        createSampleRequest(
            100.0f, 1, 500.0f, 2, 300.0f, 10.0f, false, true, "5912", "MERC-001", false);

    TransactionVector vector =
        TransactionVector.fromRequest(
            request, CONSTANTS, java.util.Map.of("5411", 0.15F, "5912", 0.20F));

    assertNotNull(vector);
    assertEquals(14, vector.features().length);
    assertTrue(vector.features()[0] >= 0.0f && vector.features()[0] <= 1.0f);
  }

  @Test
  void shouldMatchExampleFromDocumentation() {
    FraudRequest request =
        new FraudRequest(
            "tx-1329056812",
            new TransactionDTO(41.12F, 2, "2026-03-11T18:45:53Z"),
            new CustomerDTO(82.24f, 3, java.util.List.of("MERC-003", "MERC-016")),
            new MerchantDTO("MERC-016", "5411", 60.25f),
            new TerminalDTO(false, true, 29.23F),
            null);

    TransactionVector vector =
        TransactionVector.fromRequest(request, CONSTANTS, java.util.Map.of("5411", 0.15F));

    float[] features = vector.features();
    assertEquals(0.0041f, features[0], 0.001f);
    assertEquals(0.1667f, features[1], 0.001f);
    assertEquals(0.05f, features[2], 0.01f);
    assertEquals(0.7826f, features[3], 0.001f);
    assertEquals(0.3333f, features[4], 0.001f);
    assertEquals(-1.0f, features[5], 0.001f);
    assertEquals(-1.0f, features[6], 0.001f);
    assertEquals(0.0292f, features[7], 0.001f);
    assertEquals(0.15f, features[8], 0.001f);
    assertEquals(0.0f, features[9], 0.001f);
    assertEquals(1.0f, features[10], 0.001f);
    assertEquals(0.0f, features[11], 0.001f);
    assertEquals(0.15f, features[12], 0.001f);
    assertEquals(0.006f, features[13], 0.001f);
  }

  private FraudRequest createSampleRequest(
      float amount,
      int installments,
      float avgAmount,
      int txCount24h,
      float merchantAvgAmount,
      float kmFromHome,
      boolean isOnline,
      boolean cardPresent,
      String mcc,
      String merchantId,
      boolean lastTxNull) {
    return new FraudRequest(
        "tx-123",
        new TransactionDTO(amount, installments, "2026-03-11T18:45:53Z"),
        new CustomerDTO(avgAmount, txCount24h, List.of("MERC-001")),
        new MerchantDTO(merchantId, mcc, merchantAvgAmount),
        new TerminalDTO(isOnline, cardPresent, kmFromHome),
        null);
  }

  @Test
  void shouldHandleNullLastTransaction() {
    FraudRequest request =
        new FraudRequest(
            "tx-123",
             new TransactionDTO(100.0f, 1, "2026-03-11T18:45:53Z"),
             new CustomerDTO(500.0f, 2, List.of("MERC-001")),
             new MerchantDTO("MERC-001", "5411", 300.0f),
             new TerminalDTO(false, true, 10.0f),
             null);
    
    TransactionVector vector =
        TransactionVector.fromRequest(request, CONSTANTS, java.util.Map.of("5411", 0.15f));
    
    float[] features = vector.features();
    assertEquals(-1.0f, features[5], 0.001f);
    assertEquals(-1.0f, features[6], 0.001f);
  }

  @Test
  void shouldHandleUnknownMerchant() {
    FraudRequest request =
        new FraudRequest(
            "tx-123",
             new TransactionDTO(100.0f, 1, "2026-03-11T18:45:53Z"),
             new CustomerDTO(500.0f, 2, List.of("MERC-999")),
             new MerchantDTO("MERC-001", "5411", 300.0f),
             new TerminalDTO(false, true, 10.0f),
            null);

    TransactionVector vector =
        TransactionVector.fromRequest(request, CONSTANTS, java.util.Map.of("5411", 0.15f));
    
    float[] features = vector.features();
    assertEquals(1.0f, features[11], 0.001f);
  }

  @Test
  void shouldHandleDefaultMccRisk() {
    FraudRequest request =
        new FraudRequest(
            "tx-123",
             new TransactionDTO(100.0f, 1, "2026-03-11T18:45:53Z"),
             new CustomerDTO(500.0f, 2, List.of("MERC-001")),
             new MerchantDTO("MERC-001", "9999", 300.0f),
             new TerminalDTO(false, true, 10.0f),
            null);

    TransactionVector vector =
        TransactionVector.fromRequest(request, CONSTANTS, java.util.Map.of("5411", 0.15f));
    
    float[] features = vector.features();
    assertEquals(0.5f, features[12], 0.001f);
  }

  @Test
  void shouldClampAmount() {
    FraudRequest request =
        new FraudRequest(
            "tx-123",
             new TransactionDTO(50000.0f, 1, "2026-03-11T18:45:53Z"),
             new CustomerDTO(500.0f, 2, List.of("MERC-001")),
             new MerchantDTO("MERC-001", "5411", 300.0f),
             new TerminalDTO(false, true, 10.0f),
            null);

    TransactionVector vector =
        TransactionVector.fromRequest(request, CONSTANTS, java.util.Map.of("5411", 0.15f));
    
    float[] features = vector.features();
    assertEquals(1.0f, features[0], 0.001f);
  }

  @Test
  void shouldClampInstallments() {
    FraudRequest request =
        new FraudRequest(
            "tx-123",
             new TransactionDTO(100.0f, 24, "2026-03-11T18:45:53Z"),
             new CustomerDTO(500.0f, 2, List.of("MERC-001")),
             new MerchantDTO("MERC-001", "5411", 300.0f),
             new TerminalDTO(false, true, 10.0f),
            null);

    TransactionVector vector =
        TransactionVector.fromRequest(request, CONSTANTS, java.util.Map.of("5411", 0.15f));
    
    float[] features = vector.features();
    assertEquals(1.0f, features[1], 0.001f);
  }
}
