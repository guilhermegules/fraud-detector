package com.rinha.frauddetector.domain;

import java.nio.charset.StandardCharsets;

public record FraudScore(boolean approved, float score) {

  private static final float THRESHOLD = 0.6f;

  public static final FraudScore SAFE = new FraudScore(true, 0.0f);

  private static final byte[][] PRECOMPUTED_RESPONSES = buildResponses();

  private static byte[][] buildResponses() {
    byte[][] arr = new byte[6][];
    for (int n = 0; n <= 5; n++) {
      float s = n / 5.0f;
      boolean app = s < THRESHOLD;
      arr[n] = ("{\"approved\":" + app + ",\"fraud_score\":" + s + "}").getBytes(StandardCharsets.UTF_8);
    }
    return arr;
  }

  private static final FraudScore[] SCORES = new FraudScore[6];
  static {
    for (int n = 0; n <= 5; n++) {
      float s = n / 5.0f;
      SCORES[n] = new FraudScore(s < THRESHOLD, s);
    }
  }

  public static FraudScore fromFraudCount(int fraudNeighbors) {
    return SCORES[Math.min(fraudNeighbors, 5)];
  }

  public byte[] responseBytes() {
    int n = Math.round(score * 5);
    if (n < 0) n = 0;
    if (n > 5) n = 5;
    return PRECOMPUTED_RESPONSES[n];
  }

  public static FraudScore fromScore(float score) {
    score = Math.clamp(score, 0.0f, 1.0f);
    return new FraudScore(score < THRESHOLD, score);
  }

  public boolean isFraudulent() {
    return !approved;
  }

  public boolean isLowRisk() {
    return score < 0.4f;
  }

  public boolean isMediumRisk() {
    return score >= 0.4f && score <= 0.7f;
  }

  public boolean isHighRisk() {
    return score > 0.7f;
  }
}
