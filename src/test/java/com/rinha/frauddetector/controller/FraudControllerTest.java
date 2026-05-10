package com.rinha.frauddetector.controller;

import com.rinha.frauddetector.domain.FraudDetectionService;
import com.rinha.frauddetector.domain.FraudScore;
import com.rinha.frauddetector.dto.FraudRequest;
import com.rinha.frauddetector.dto.TransactionDTO;
import com.rinha.frauddetector.dto.CustomerDTO;
import com.rinha.frauddetector.dto.MerchantDTO;
import com.rinha.frauddetector.dto.TerminalDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FraudControllerTest {

  @Mock
  private FraudDetectionService fraudDetectionService;

  @InjectMocks
  private FraudController fraudController;

  private FraudRequest createMockRequest() {
    return new FraudRequest(
        "tx-123",
        new TransactionDTO(100.0f, 1, "2026-03-11T18:45:53Z"),
        new CustomerDTO(500.0f, 2, List.of("MERC-001")),
        new MerchantDTO("MERC-001", "5411", 300.0f),
        new TerminalDTO(false, true, 10.0f),
        null
    );
  }

  @Test
  void shouldApproveLowRiskTransaction() {
    when(fraudDetectionService.evaluate(any(FraudRequest.class)))
        .thenReturn(new FraudScore(true, 0.2f));

    ResponseEntity<byte[]> response = fraudController.fraudScore(createMockRequest());
    String body = new String(response.getBody(), StandardCharsets.UTF_8);
    assertTrue(body.contains("\"approved\":true"));
    assertTrue(body.contains("\"fraud_score\":0.2"));
  }

  @Test
  void shouldRejectHighRiskTransaction() {
    when(fraudDetectionService.evaluate(any(FraudRequest.class)))
        .thenReturn(new FraudScore(false, 0.8f));

    ResponseEntity<byte[]> response = fraudController.fraudScore(createMockRequest());
    String body = new String(response.getBody(), StandardCharsets.UTF_8);
    assertFalse(body.contains("\"approved\":true"));
    assertTrue(body.contains("\"fraud_score\":0.8"));
  }

  @Test
  void shouldReturnSafeOnError() {
    when(fraudDetectionService.evaluate(any(FraudRequest.class)))
        .thenReturn(FraudScore.SAFE);

    ResponseEntity<byte[]> response = fraudController.fraudScore(createMockRequest());
    String body = new String(response.getBody(), StandardCharsets.UTF_8);
    assertTrue(body.contains("\"approved\":true"));
    assertTrue(body.contains("\"fraud_score\":0.0"));
  }
}
