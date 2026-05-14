package com.rinha.frauddetector.domain;

import java.nio.charset.StandardCharsets;

public record FraudScore(boolean approved, float score) {

  public static final int K = 5;
  private static final float THRESHOLD = 0.4f;

  public static final FraudScore SAFE = new FraudScore(true, 0.0f);

  private static final FraudScore[] SCORES = new FraudScore[K + 1];
  static {
    for (int n = 0; n <= K; n++) {
      SCORES[n] = new FraudScore((float) n / K < THRESHOLD, (float) n / K);
    }
  }

  private static final byte[][] PRECOMPUTED_RESPONSES = buildResponses();
  private static byte[][] buildResponses() {
    byte[][] arr = new byte[K + 1][];
    for (int n = 0; n <= K; n++) {
      float s = (float) n / K;
      arr[n] = ("{\"approved\":" + (s < THRESHOLD) + ",\"fraud_score\":" + s + "}").getBytes(StandardCharsets.UTF_8);
    }
    return arr;
  }

  public static FraudScore fromFraudCount(int fraudNeighbors) {
    return SCORES[Math.min(fraudNeighbors, K)];
  }

  public byte[] responseBytes() {
    int n = Math.round(score * K);
    if (n < 0) n = 0;
    if (n > K) n = K;
    return PRECOMPUTED_RESPONSES[n];
  }

}
