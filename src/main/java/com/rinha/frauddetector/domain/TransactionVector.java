package com.rinha.frauddetector.domain;

import com.rinha.frauddetector.dto.FraudRequest;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.Map;

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
      FraudRequest request,
      NormalizationConstants constants,
      Map<String, Double> mccRiskMap) {

    final var transaction = request.transaction();
    final var customer = request.customer();
    final var merchant = request.merchant();
    final var terminal = request.terminal();
    final var lastTransaction = request.last_transaction();

    final Instant instant = Instant.parse(transaction.requested_at());
    final int hour = instant.atZone(ZoneOffset.UTC).getHour();
    final int dayOfWeek = instant.atZone(ZoneOffset.UTC).getDayOfWeek().getValue() - 1;

    final double amountRatio = customer.avg_amount() > 0
        ? transaction.amount() / customer.avg_amount()
        : 0.0;

    double minutesSinceLastTx = 0;
    if (lastTransaction != null) {
      final Instant lastInstant = Instant.parse(lastTransaction.timestamp());
      minutesSinceLastTx = Duration.between(lastInstant, instant).toMinutes();
    }

    float[] v = new float[VECTOR_SIZE];

    // 0: amount
    v[0] = clamp((float) (transaction.amount() / constants.max_amount()));

    // 1: installments
    v[1] = clamp(transaction.installments() / (float) constants.max_installments());

    // 2: amount_vs_avg
    v[2] = clamp((float) (amountRatio / constants.amount_vs_avg_ratio()));

    // 3: hour_of_day
    v[3] = clamp(hour / 23.0f);

    // 4: day_of_week
    v[4] = clamp(dayOfWeek / 6.0f);

    // 5: minutes_since_last_tx
    v[5] = lastTransaction != null
        ? clamp((float) (minutesSinceLastTx / constants.max_minutes()))
        : -1.0f;

    // 6: km_from_last_tx
    v[6] = lastTransaction != null
        ? clamp((float) (lastTransaction.km_from_current() / constants.max_km()))
        : -1.0f;

    // 7: km_from_home
    v[7] = clamp((float) (terminal.km_from_home() / constants.max_km()));

    // 8: tx_count_24h
    v[8] = clamp(customer.tx_count_24h() / (float) constants.max_tx_count_24h());

    // 9: is_online
    v[9] = terminal.is_online() ? 1.0f : 0.0f;

    // 10: card_present
    v[10] = terminal.card_present() ? 1.0f : 0.0f;

    // 11: unknown_merchant (1 = unknown)
    v[11] = customer.known_merchants().contains(merchant.id()) ? 0.0f : 1.0f;

    // 12: mcc_risk
    v[12] = mccRiskMap.getOrDefault(merchant.mcc(), 0.5).floatValue();

    // 13: merchant_avg_amount
    v[13] = clamp((float) (merchant.avg_amount() / constants.max_merchant_avg_amount()));

    return new TransactionVector(v);
  }

  private static float clamp(float v) {
    return Math.clamp(v, 0f, 1f);
  }
}
