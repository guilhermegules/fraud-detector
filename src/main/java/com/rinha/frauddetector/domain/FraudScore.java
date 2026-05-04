package com.rinha.frauddetector.domain;

public record FraudScore(boolean approved, double score) {

  public static final FraudScore SAFE = new FraudScore(true, 0.0);

  public static FraudScore fromScore(double score) {
    return new FraudScore(score < 0.5, clamp(score));
  }

  private static double clamp(double v) {
    return Math.clamp(v, 0.0, 1.0);
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
