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
    short[] features = new short[16];
    for (int i = 0; i < 16; i++) features[i] = 5000;

    TransactionVector vector = new TransactionVector(features);
    assertNotNull(vector);
    assertEquals(16, vector.features().length);
  }

  @Test
  void shouldRejectInvalidSize() {
    short[] features = new short[10];
    assertThrows(IllegalArgumentException.class, () -> new TransactionVector(features));
  }

  @Test
  void shouldCalculateDistance() {
    short[] f1 = new short[16];
    short[] f2 = new short[16];
    for (int i = 0; i < 14; i++) {
      f1[i] = 0;
      f2[i] = 10000;
    }

    TransactionVector v1 = new TransactionVector(f1);
    TransactionVector v2 = new TransactionVector(f2);

    int distance = v1.distanceTo(v2);
    assertEquals(14 * 10000 * 10000, distance);
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
    assertEquals(16, vector.features().length);
    assertTrue(vector.features()[0] >= 0 && vector.features()[0] <= 10000);
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

    short[] features = vector.features();
    assertEquals(41, features[0], 1);
    assertEquals(1667, features[1], 1);
    assertEquals(500, features[2], 10);
    assertEquals(7826, features[3], 1);
    assertEquals(5000, features[4], 1);
    assertEquals(0, features[5], 1);
    assertEquals(0, features[6], 1);
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
    
    short[] features = vector.features();
    assertEquals(0, features[5], 1);
    assertEquals(0, features[6], 1);
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
    
    short[] features = vector.features();
    assertEquals(14142, features[11], 1);
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
    
    short[] features = vector.features();
    assertEquals(6124, features[12], 1);
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
    
    short[] features = vector.features();
    assertEquals(10000, features[0], 1);
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
    
    short[] features = vector.features();
    assertEquals(10000, features[1], 1);
  }
}
