package com.rinha.frauddetector.domain;

import com.rinha.frauddetector.dto.FraudRequest;

import java.util.Arrays;
import java.util.Map;

public record TransactionVector(short[] features) {

  private static final int VECTOR_SIZE = 16;
  private static final int RAW_SIZE = 14;
  private static final int SCALE = 10_000;
  private static final short MISSING = -1;
  private static final short BOOL_SCALE = 3000;

  private static final int[] DAYS_IN_MONTH = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

  public TransactionVector {
    if (features == null || features.length != VECTOR_SIZE) {
      throw new IllegalArgumentException("Vector must have exactly " + VECTOR_SIZE + " features");
    }
  }

  public int distanceTo(TransactionVector other) {
    int sum = 0;

    for (int i = 0; i < RAW_SIZE; i++) {
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

    final String currentTs = transaction.requested_at();
    final int hour = parseHour(currentTs);
    final int dayOfWeek = parseDayOfWeek(currentTs);

    float amountRatio = customer.avg_amount() > 0
            ? (float) Math.log1p(transaction.amount() / customer.avg_amount())
            : 0f;

    float minutesSinceLastTx = 0f;
    if (lastTransaction != null) {
      long currentEpoch = parseEpochSecond(currentTs);
      long lastEpoch = parseEpochSecond(lastTransaction.timestamp());
      minutesSinceLastTx = (currentEpoch - lastEpoch) / 60f;
    }

    short[] v = new short[VECTOR_SIZE];

    v[0] = (short) (norm(transaction.amount() / constants.max_amount()) * SCALE);
    v[1] = (short) (norm(transaction.installments() / constants.max_installments()) * SCALE);
    v[2] = (short) (norm(amountRatio / constants.amount_vs_avg_ratio()) * SCALE);
    v[3] = (short) (norm(hour / 23.0f) * SCALE);
    v[4] = (short) (norm(dayOfWeek / 6.0f) * SCALE);

    if (lastTransaction != null) {
      v[5] = (short) (norm(minutesSinceLastTx / constants.max_minutes()) * SCALE);
      v[6] = (short) (norm(lastTransaction.km_from_current() / constants.max_km()) * SCALE);
    } else {
      v[5] = MISSING;
      v[6] = MISSING;
    }

    v[7] = (short) (norm(terminal.km_from_home() / constants.max_km()) * SCALE);
    v[8] = (short) (norm(customer.tx_count_24h() / constants.max_tx_count_24h()) * SCALE);

    v[9]  = terminal.is_online() ? BOOL_SCALE : 0;
    v[10] = terminal.card_present() ? BOOL_SCALE : 0;
    v[11] = customer.known_merchants().contains(merchant.id()) ? 0 : BOOL_SCALE;

    v[12] = (short) (norm(mccRiskMap.getOrDefault(merchant.mcc(), 0.5f)) * SCALE);
    v[13] = (short) (norm(merchant.avg_amount() / constants.max_merchant_avg_amount()) * SCALE);

    v[14] = 0;
    v[15] = 0;

    return v;
  }

  private static int parseHour(String ts) {
    return (ts.charAt(11) - '0') * 10 + (ts.charAt(12) - '0');
  }

  private static int parseDayOfWeek(String ts) {
    int year = parseInt(ts, 0, 4);
    int month = parseInt(ts, 5, 7);
    int day = parseInt(ts, 8, 10);

    int dayOfYear = day;
    for (int m = 1; m < month; m++) {
      dayOfYear += DAYS_IN_MONTH[m - 1];
    }
    if (month > 2 && isLeapYear(year)) {
      dayOfYear++;
    }

    int dow = (year + year / 4 - year / 100 + year / 400 + dayOfYear) % 7;
    return dow;
  }

  private static boolean isLeapYear(int year) {
    return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
  }

  private static int parseInt(String s, int start, int end) {
    int result = 0;
    for (int i = start; i < end; i++) {
      result = result * 10 + (s.charAt(i) - '0');
    }
    return result;
  }

  private static long parseEpochSecond(String ts) {
    int year = parseInt(ts, 0, 4);
    int month = parseInt(ts, 5, 7);
    int day = parseInt(ts, 8, 10);
    int hour = parseInt(ts, 11, 13);
    int minute = parseInt(ts, 14, 16);
    int second = parseInt(ts, 17, 19);

    int days = daysSinceEpoch(year, month, day);
    return (((long) days * 24 + hour) * 60 + minute) * 60 + second;
  }

  private static int daysSinceEpoch(int year, int month, int day) {
    int y = year - 1;
    int days = y * 365 + y / 4 - y / 100 + y / 400;

    for (int m = 1; m < month; m++) {
      days += DAYS_IN_MONTH[m - 1];
    }
    if (month > 2 && isLeapYear(year)) {
      days++;
    }

    days += day - 1;
    return days;
  }

  private static float norm(float value) {
    if (value < 0f) return 0f;
    return Math.min(value, 1f);
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
