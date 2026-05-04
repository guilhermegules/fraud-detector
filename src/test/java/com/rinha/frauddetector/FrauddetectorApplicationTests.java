package com.rinha.frauddetector;

import com.rinha.frauddetector.adapter.loader.ReferenceLoader;
import com.rinha.frauddetector.dto.FraudResponse;
import com.rinha.frauddetector.fixture.FraudJsonFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FrauddetectorApplicationTests {

  @LocalServerPort private int port;

  @Autowired private ReferenceLoader referenceLoader;

  private final RestTemplate restTemplate = new RestTemplate();

  @Test
  void shouldApproveLegitimateTransaction() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<String> request = new HttpEntity<>(FraudJsonFixture.validTransaction, headers);

    ResponseEntity<FraudResponse> response =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/fraud-score", request, FraudResponse.class);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertTrue(response.getBody().approved());
    assertTrue(response.getBody().fraud_score() < 0.6);
  }

  @Test
  void shouldRejectFraudulentTransaction() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<String> request = new HttpEntity<>(FraudJsonFixture.fraudulentTransaction, headers);

    ResponseEntity<FraudResponse> response =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/fraud-score", request, FraudResponse.class);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertFalse(response.getBody().approved());
    assertTrue(response.getBody().fraud_score() >= 0.6);
  }

  @Test
  void shouldReturnResponseForTransactionWithLastTransaction() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<String> request = new HttpEntity<>(FraudJsonFixture.validTransactionWithLastTransaction, headers);

    ResponseEntity<FraudResponse> response =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/fraud-score", request, FraudResponse.class);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertTrue(response.getBody().approved());
    assertTrue(response.getBody().fraud_score() < 0.6);
  }

  @Test
  void shouldLoadReferenceDataOnStartup() {
    assertNotNull(referenceLoader.getNormalizationConstants());
    assertNotNull(referenceLoader.getMccRiskMap());
    assertNotNull(referenceLoader.getFraudReference());
    assertTrue(referenceLoader.getFraudReference().vectors().length > 0);
  }
}
