package com.rinha.frauddetector.domain;

import java.nio.charset.StandardCharsets;

public record FraudScore(boolean approved, float score) {

  public static final int K = 5;
  private static final float THRESHOLD = 0.6f;

  public static final FraudScore SAFE = new FraudScore(true, 0.0f);

  private static final byte[][] PRECOMPUTED_RESPONSES = buildResponses();

  private static byte[][] buildResponses() {
    byte[][] arr = new byte[K + 1][];
    for (int n = 0; n <= K; n++) {
      float s = (float) n / K;
      boolean app = s < THRESHOLD;
      arr[n] = ("{\"approved\":" + app + ",\"fraud_score\":" + s + "}").getBytes(StandardCharsets.UTF_8);
    }
    return arr;
  }

  private static final FraudScore[] SCORES = new FraudScore[K + 1];
  static {
    for (int n = 0; n <= K; n++) {
      float s = (float) n / K;
      SCORES[n] = new FraudScore(s < THRESHOLD, s);
    }
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
