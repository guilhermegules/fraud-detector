package com.rinha.frauddetector.domain;

import com.rinha.frauddetector.dto.FraudRequest;

import java.util.Map;

public final class TransactionVector {

  static final int SCALE = 8192;

  private static final short[] HOURLUT;
  private static final short[] DOWLUT;

  static {
    HOURLUT = new short[24];
    for (int i = 0; i < 24; i++) {
      HOURLUT[i] = (short) ((float) i / 23f * SCALE);
    }
    DOWLUT = new short[7];
    for (int i = 0; i < 7; i++) {
      DOWLUT[i] = (short) ((float) i / 6f * SCALE);
    }
  }

  private static final int[] DAYS_IN_MONTH = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

  private TransactionVector() {}

  public static void toArray(
          FraudRequest request,
          NormalizationConstants constants,
          Map<String, Float> mccRiskMap,
          short[] v) {

    final String ts = request.transaction().requested_at();
    final int hour = (ts.charAt(11) - '0') * 10 + (ts.charAt(12) - '0');
    final int dow = parseDayOfWeek(ts);

    v[0] = q(clamp(request.transaction().amount() / constants.max_amount()));
    v[1] = q(clamp(request.transaction().installments() / constants.max_installments()));

    float avgAmt = request.customer().avg_amount();
    v[2] = q(avgAmt > 0f ? clamp((request.transaction().amount() / avgAmt) / constants.amount_vs_avg_ratio()) : 1f);

    v[3] = HOURLUT[hour];
    v[4] = DOWLUT[dow];

    final var lastTx = request.last_transaction();
    if (lastTx != null && lastTx.timestamp() != null) {
      long currEpoch = parseEpochSecond(ts);
      long lastEpoch = parseEpochSecond(lastTx.timestamp());
      float minutes = (currEpoch - lastEpoch) / 60f;
      v[5] = q(clamp(minutes / constants.max_minutes()));
      v[6] = q(clamp(lastTx.km_from_current() / constants.max_km()));
    } else {
      v[5] = (short) -SCALE;
      v[6] = (short) -SCALE;
    }

    v[7] = q(clamp(request.terminal().km_from_home() / constants.max_km()));
    v[8] = q(clamp(request.customer().tx_count_24h() / constants.max_tx_count_24h()));

    v[9] = request.terminal().is_online() ? (short) SCALE : (short) 0;
    v[10] = request.terminal().card_present() ? (short) SCALE : (short) 0;
    v[11] = request.customer().known_merchants().contains(request.merchant().id()) ? (short) 0 : (short) SCALE;

    v[12] = q(clamp(mccRiskMap.getOrDefault(request.merchant().mcc(), 0.5f)));
    v[13] = q(clamp(request.merchant().avg_amount() / constants.max_merchant_avg_amount()));

    v[14] = 0;
    v[15] = 0;
  }

  private static double clamp(double x) {
    if (x < 0) return 0;
    if (x > 1) return 1;
    return x;
  }

  private static short q(double x) {
    double q = Math.round(x * SCALE);
    if (q > Short.MAX_VALUE) q = Short.MAX_VALUE;
    if (q < Short.MIN_VALUE) q = Short.MIN_VALUE;
    return (short) q;
  }

  private static int parseDayOfWeek(String ts) {
    int year = parseInt(ts, 0, 4);
    int month = parseInt(ts, 5, 7);
    int day = parseInt(ts, 8, 10);

    int m = month;
    if (m < 3) {
      m += 12;
      year--;
    }
    int k = year % 100;
    int j = year / 100;

    int h = (day + (13 * (m + 1)) / 5 + k + k / 4 + j / 4 + 5 * j) % 7;
    return (h + 5) % 7;
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

}
