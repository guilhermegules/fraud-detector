package com.rinha.frauddetector.controller;

import com.rinha.frauddetector.domain.FraudDetectionService;
import com.rinha.frauddetector.domain.FraudScore;
import com.rinha.frauddetector.dto.FraudRequest;
import com.rinha.frauddetector.dto.FraudResponse;
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

    ResponseEntity<FraudResponse> response = fraudController.fraudScore(createMockRequest());
    assertTrue(response.getBody().approved());
    assertEquals(0.2f, response.getBody().fraud_score(), 0.001f);
  }

  @Test
  void shouldRejectHighRiskTransaction() {
    when(fraudDetectionService.evaluate(any(FraudRequest.class)))
        .thenReturn(new FraudScore(false, 0.8f));

    ResponseEntity<FraudResponse> response = fraudController.fraudScore(createMockRequest());
    assertFalse(response.getBody().approved());
    assertEquals(0.8f, response.getBody().fraud_score(), 0.001f);
  }

  @Test
  void shouldReturnSafeOnError() {
    when(fraudDetectionService.evaluate(any(FraudRequest.class)))
        .thenReturn(FraudScore.SAFE);

    ResponseEntity<FraudResponse> response = fraudController.fraudScore(createMockRequest());
    assertTrue(response.getBody().approved());
    assertEquals(0.0f, response.getBody().fraud_score(), 0.001f);
  }
}
