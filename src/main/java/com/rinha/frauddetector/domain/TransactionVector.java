package com.rinha.frauddetector.domain;

public record TransactionVector(float[] features) {

  private static final int VECTOR_SIZE = 14;

  public TransactionVector {
    if (features == null || features.length != VECTOR_SIZE) {
      throw new IllegalArgumentException("Vector must have exactly " + VECTOR_SIZE + " features");
    }
  }

  public double distanceTo(TransactionVector other) {
    double sum = 0;
    for (int i = 0; i < VECTOR_SIZE; i++) {
      double diff = this.features[i] - other.features[i];
      sum += diff * diff;
    }
    return sum;
  }

  public static TransactionVector fromRequest(
      double amount,
      int installments,
      double customerAvgAmount,
      int txCount24h,
      double merchantAvgAmount,
      double kmFromHome,
      boolean isOnline,
      boolean cardPresent,
      boolean hasLastTransaction,
      double lastTxKmFromCurrent,
      String mcc,
      boolean merchantKnown,
      int hour,
      double amountRatio) {

    float[] v = new float[VECTOR_SIZE];
    v[0] = clamp((float) (amount / 10000.0));
    v[1] = clamp(installments / 12.0f);
    v[2] = clamp((float) (customerAvgAmount / 10000.0));
    v[3] = clamp(txCount24h / 100.0f);
    v[4] = clamp((float) (merchantAvgAmount / 10000.0));
    v[5] = clamp((float) (kmFromHome / 1000.0));
    v[6] = isOnline ? 1.0f : 0.0f;
    v[7] = cardPresent ? 1.0f : 0.0f;
    v[8] = hasLastTransaction ? 1.0f : 0.0f;
    v[9] = hasLastTransaction ? clamp((float) (lastTxKmFromCurrent / 1000.0)) : 0.0f;
    v[10] = encodeMcc(mcc);
    v[11] = merchantKnown ? 1.0f : 0.0f;
    v[12] = hour / 24.0f;
    v[13] = clamp((float) amountRatio);

    return new TransactionVector(v);
  }

  private static float clamp(float v) {
    return Math.clamp(v, 0f, 1f);
  }

  private static float encodeMcc(String mcc) {
    if (mcc == null) return 0.5f;
    try {
      int code = Integer.parseInt(mcc);
      return clamp(code / 10000.0f);
    } catch (NumberFormatException e) {
      return 0.5f;
    }
  }
}
