package com.rinha.frauddetector.domain;

import com.rinha.frauddetector.dto.FraudRequest;
import com.rinha.frauddetector.dto.TransactionDTO;
import com.rinha.frauddetector.dto.CustomerDTO;
import com.rinha.frauddetector.dto.MerchantDTO;
import com.rinha.frauddetector.dto.TerminalDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TransactionVectorTest {

  private static final NormalizationConstants CONSTANTS =
      new NormalizationConstants(10000, 12, 10, 1440, 1000, 20, 10000);

  @Test
  void shouldCreateFromRequest() {
    short[] buf = new short[16];
    FraudRequest request = createSampleRequest(100.0f, 1, 500.0f, 2, 300.0f, 10.0f, false, true, "5912", "MERC-001", true);
    TransactionVector.toArray(request, CONSTANTS, Map.of("5411", 0.15F, "5912", 0.20F), buf);
    assertTrue(buf[0] >= 0 && buf[0] <= 10000);
  }

  @Test
  void shouldMatchExampleFromDocumentation() {
    short[] buf = new short[16];
    FraudRequest request = new FraudRequest(
        "tx-1329056812",
        new TransactionDTO(41.12F, 2, "2026-03-11T18:45:53Z"),
        new CustomerDTO(82.24f, 3, List.of("MERC-003", "MERC-016")),
        new MerchantDTO("MERC-016", "5411", 60.25f),
        new TerminalDTO(false, true, 29.23F),
        null);

    TransactionVector.toArray(request, CONSTANTS, Map.of("5411", 0.15F), buf);
    assertEquals(41, buf[0], 1);
    assertEquals(1667, buf[1], 1);
    assertEquals(500, buf[2], 10);
    assertEquals(7826, buf[3], 1);
    assertEquals(3333, buf[4], 1);
    assertEquals(-10000, buf[5], 1);
    assertEquals(-10000, buf[6], 1);
  }

  @Test
  void shouldHandleNullLastTransaction() {
    short[] buf = new short[16];
    FraudRequest request = new FraudRequest(
        "tx-123",
        new TransactionDTO(100.0f, 1, "2026-03-11T18:45:53Z"),
        new CustomerDTO(500.0f, 2, List.of("MERC-001")),
        new MerchantDTO("MERC-001", "5411", 300.0f),
        new TerminalDTO(false, true, 10.0f),
        null);

    TransactionVector.toArray(request, CONSTANTS, Map.of("5411", 0.15f), buf);
    assertEquals(-10000, buf[5], 1);
    assertEquals(-10000, buf[6], 1);
  }

  @Test
  void shouldHandleUnknownMerchant() {
    short[] buf = new short[16];
    FraudRequest request = new FraudRequest(
        "tx-123",
        new TransactionDTO(100.0f, 1, "2026-03-11T18:45:53Z"),
        new CustomerDTO(500.0f, 2, List.of("MERC-999")),
        new MerchantDTO("MERC-001", "5411", 300.0f),
        new TerminalDTO(false, true, 10.0f),
        null);

    TransactionVector.toArray(request, CONSTANTS, Map.of("5411", 0.15f), buf);
    assertEquals(10000, buf[11], 1);
  }

  @Test
  void shouldHandleDefaultMccRisk() {
    short[] buf = new short[16];
    FraudRequest request = new FraudRequest(
        "tx-123",
        new TransactionDTO(100.0f, 1, "2026-03-11T18:45:53Z"),
        new CustomerDTO(500.0f, 2, List.of("MERC-001")),
        new MerchantDTO("MERC-001", "9999", 300.0f),
        new TerminalDTO(false, true, 10.0f),
        null);

    TransactionVector.toArray(request, CONSTANTS, Map.of("5411", 0.15f), buf);
    assertEquals(5000, buf[12], 1);
  }

  @Test
  void shouldClampAmount() {
    short[] buf = new short[16];
    FraudRequest request = new FraudRequest(
        "tx-123",
        new TransactionDTO(50000.0f, 1, "2026-03-11T18:45:53Z"),
        new CustomerDTO(500.0f, 2, List.of("MERC-001")),
        new MerchantDTO("MERC-001", "5411", 300.0f),
        new TerminalDTO(false, true, 10.0f),
        null);

    TransactionVector.toArray(request, CONSTANTS, Map.of("5411", 0.15f), buf);
    assertEquals(10000, buf[0], 1);
  }

  @Test
  void shouldClampInstallments() {
    short[] buf = new short[16];
    FraudRequest request = new FraudRequest(
        "tx-123",
        new TransactionDTO(100.0f, 24, "2026-03-11T18:45:53Z"),
        new CustomerDTO(500.0f, 2, List.of("MERC-001")),
        new MerchantDTO("MERC-001", "5411", 300.0f),
        new TerminalDTO(false, true, 10.0f),
        null);

    TransactionVector.toArray(request, CONSTANTS, Map.of("5411", 0.15f), buf);
    assertEquals(10000, buf[1], 1);
  }

  private static FraudRequest createSampleRequest(
      float amount, int installments, float avgAmount, int txCount24h,
      float merchantAvgAmount, float kmFromHome, boolean isOnline,
      boolean cardPresent, String mcc, String merchantId, boolean lastTxNull) {
    return new FraudRequest(
        "tx-123",
        new TransactionDTO(amount, installments, "2026-03-11T18:45:53Z"),
        new CustomerDTO(avgAmount, txCount24h, List.of("MERC-001")),
        new MerchantDTO(merchantId, mcc, merchantAvgAmount),
        new TerminalDTO(isOnline, cardPresent, kmFromHome),
        null);
  }
}
