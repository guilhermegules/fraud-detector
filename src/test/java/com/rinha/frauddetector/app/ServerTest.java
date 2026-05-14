package com.rinha.frauddetector.app;

import com.rinha.frauddetector.domain.FraudScore;
import com.rinha.frauddetector.http.JsonCodec;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ServerTest {

  @Test
  void shouldApproveLowRiskTransaction() {
    byte[] response = FraudScore.fromFraudCount(0).responseBytes();
    String body = new String(response, StandardCharsets.UTF_8);
    assertTrue(body.contains("\"approved\":true"));
  }

  @Test
  void shouldRejectHighRiskTransaction() {
    byte[] response = FraudScore.fromFraudCount(5).responseBytes();
    String body = new String(response, StandardCharsets.UTF_8);
    assertFalse(body.contains("\"approved\":true"));
    assertTrue(body.contains("\"fraud_score\":1.0"));
  }

  @Test
  void shouldReturnSafeOnError() {
    byte[] response = FraudScore.SAFE.responseBytes();
    String body = new String(response, StandardCharsets.UTF_8);
    assertTrue(body.contains("\"approved\":true"));
    assertTrue(body.contains("\"fraud_score\":0.0"));
  }
}
