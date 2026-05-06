package com.rinha.frauddetector.domain;

public record FraudScore(boolean approved, float score) {

  public static final FraudScore SAFE = new FraudScore(true, 0.0f);
  private static final float THRESHOLD = 0.6f;

  public static FraudScore fromScore(float score) {
    score = clamp(score);
    return new FraudScore(score > THRESHOLD, score);
  }

  private static float clamp(float v) {
    return Math.clamp(v, 0.0f, 1.0f);
  }

  public boolean isFraudulent() {
    return !approved;
  }

  public boolean isHighRisk() {
    return score >= 0.8;
  }

  public boolean isMediumRisk() {
    return score >= 0.5 && score < 0.8;
  }

  public boolean isLowRisk() {
    return score < 0.5;
  }
}
