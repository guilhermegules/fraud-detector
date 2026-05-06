package com.rinha.frauddetector.domain;

import com.rinha.frauddetector.dto.FraudRequest;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;

public record TransactionVector(short[] features) {

  private static final int VECTOR_SIZE = 14;
  private static final int SCALE = 10_000;
  private static final short MISSING = (short) (SCALE / 2);
  private static final short BOOL_SCALE = 3000;

  public TransactionVector {
    if (features == null || features.length != VECTOR_SIZE) {
      throw new IllegalArgumentException("Vector must have exactly " + VECTOR_SIZE + " features");
    }
  }

  public int distanceTo(TransactionVector other) {
    int sum = 0;

    for (int i = 0; i < VECTOR_SIZE; i++) {
      int diff = this.features[i] - other.features[i];
      sum += diff * diff;
    }

    return sum;
  }

  public static TransactionVector fromRequest(
          FraudRequest request,
          NormalizationConstants constants,
          Map<String, Float> mccRiskMap) {

    return new TransactionVector(toArray(request, constants, mccRiskMap));
  }

  public static short[] toArray(
          FraudRequest request,
          NormalizationConstants constants,
          Map<String, Float> mccRiskMap) {

    final var transaction = request.transaction();
    final var customer = request.customer();
    final var merchant = request.merchant();
    final var terminal = request.terminal();
    final var lastTransaction = request.last_transaction();

    final Instant instant = Instant.parse(transaction.requested_at());
    final int hour = instant.atZone(ZoneOffset.UTC).getHour();
    final int dayOfWeek = instant.atZone(ZoneOffset.UTC).getDayOfWeek().getValue() - 1;

    float amountRatio = customer.avg_amount() > 0
            ? (float) Math.log1p(transaction.amount() / customer.avg_amount())
            : 0f;

    float minutesSinceLastTx = 0f;
    if (lastTransaction != null) {
      final Instant lastInstant = Instant.parse(lastTransaction.timestamp());
      minutesSinceLastTx = Duration.between(lastInstant, instant).toMinutes();
    }

    short[] v = new short[VECTOR_SIZE];

    v[0] = norm(transaction.amount() / constants.max_amount());
    v[1] = norm(transaction.installments() / constants.max_installments());
    v[2] = norm(amountRatio / constants.amount_vs_avg_ratio());
    v[3] = (short) (norm(hour / 23.0f) * 1.5);
    v[4] = (short) (norm(dayOfWeek / 6.0f) * 1.5);

    v[5] = lastTransaction != null
            ? norm(minutesSinceLastTx / constants.max_minutes())
            : MISSING;

    v[6] = lastTransaction != null
            ? norm(lastTransaction.km_from_current() / constants.max_km())
            : MISSING;

    v[7] = norm(terminal.km_from_home() / constants.max_km());
    v[8] = norm(customer.tx_count_24h() / constants.max_tx_count_24h());

    v[9]  = terminal.is_online() ? BOOL_SCALE : 0;
    v[10] = terminal.card_present() ? BOOL_SCALE : 0;
    v[11] = customer.known_merchants().contains(merchant.id()) ? 0 : BOOL_SCALE;

    v[12] = norm(mccRiskMap.getOrDefault(merchant.mcc(), 0.5f));
    v[13] = norm(merchant.avg_amount() / constants.max_merchant_avg_amount());

    return v;
  }

  private static short norm(float value) {
    if (value <= 0f) return 0;
    if (value >= 1f) return (short) SCALE;
    return (short) (value * SCALE);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TransactionVector(short[] features1))) return false;
    return Arrays.equals(features, features1);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(features);
  }
}
