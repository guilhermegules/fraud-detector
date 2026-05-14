package com.rinha.frauddetector.app;

import com.rinha.frauddetector.fixture.FraudJsonFixture;
import com.rinha.frauddetector.http.JsonCodec;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationTest {

  @Test
  void shouldParseValidTransactionJson() {
    byte[] json = FraudJsonFixture.validTransaction.getBytes(StandardCharsets.UTF_8);
    var request = JsonCodec.parseFraudRequest(json);
    assertNotNull(request);
    assertEquals("tx-1329056812", request.id());
    assertNotNull(request.transaction());
    assertEquals(41.12f, request.transaction().amount(), 0.001f);
    assertEquals(2, request.transaction().installments());
    assertNull(request.last_transaction());
  }

  @Test
  void shouldParseFraudulentTransactionJson() {
    byte[] json = FraudJsonFixture.fraudulentTransaction.getBytes(StandardCharsets.UTF_8);
    var request = JsonCodec.parseFraudRequest(json);
    assertNotNull(request);
    assertEquals("tx-3330991687", request.id());
    assertNotNull(request.merchant());
    assertEquals("7802", request.merchant().mcc());
  }

  @Test
  void shouldParseTransactionWithLastTransaction() {
    byte[] json = FraudJsonFixture.validTransactionWithLastTransaction.getBytes(StandardCharsets.UTF_8);
    var request = JsonCodec.parseFraudRequest(json);
    assertNotNull(request);
    assertNotNull(request.last_transaction());
    assertEquals(8.2f, request.last_transaction().km_from_current(), 0.001f);
    assertNotNull(request.last_transaction().timestamp());
  }

  @Test
  void shouldLoadReferenceDataOnStartup() throws Exception {
    Bootstrap bootstrap = new Bootstrap();
    assertNotNull(bootstrap.fraudDetectionService());
  }

  @Test
  void shouldHaveReadyResponse() {
    byte[] ready = JsonCodec.readyResponse();
    String s = new String(ready, StandardCharsets.UTF_8);
    assertTrue(s.contains("Ready"));
  }
}
