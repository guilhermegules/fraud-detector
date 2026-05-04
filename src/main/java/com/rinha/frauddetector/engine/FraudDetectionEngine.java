package com.rinha.frauddetector.engine;

import com.rinha.frauddetector.dto.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;

@Component
public class FraudDetectionEngine {

    private VPTree<float[]> vpTree;
    private byte[] labels;
    private float[][] vectors;
    private final int NEAREST_NEIGHBORS = 5;

    public void initialize(float[] vectors, byte[] labels, int n) {
        if (n <= 0) {
            this.vpTree = null;
            this.labels = new byte[0];
            this.vectors = new float[0][];
            return;
        }

        this.vectors = new float[n][];
        java.util.List<float[]> items = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            float[] v = new float[14];
            System.arraycopy(vectors, i * 14, v, 0, 14);
            this.vectors[i] = v;
            items.add(v);
        }
        this.labels = labels.clone();
        this.vpTree = new VPTree<>(items, this::vectorDistance);
    }

    public FraudResponse evaluate(FraudRequest request) {
        if (request == null) {
            return new FraudResponse(true, 0.0);
        }

        try {
            float[] vector = toVector(request);

            if (vpTree == null) {
                return new FraudResponse(true, 0.0);
            }

            java.util.List<VPTree.Neighbor<float[]>> neighbors = vpTree.search(
                    item -> vectorDistance(vector, item), NEAREST_NEIGHBORS);

            double fraudScore = calculateFraudScore(neighbors);
            boolean approved = fraudScore < 0.5;

            return new FraudResponse(approved, fraudScore);

        } catch (Exception e) {
            return new FraudResponse(true, 0.0);
        }
    }

    private float[] toVector(FraudRequest request) {
        float[] v = new float[14];

        TransactionDTO tx = request.transaction();
        CustomerDTO customer = request.customer();
        MerchantDTO merchant = request.merchant();
        TerminalDTO terminal = request.terminal();
        LastTransactionDTO lastTx = request.last_transaction();

        v[0] = clamp((float) (tx.amount() / 10000.0));
        v[1] = clamp(tx.installments() / 12.0f);
        v[2] = clamp((float) (customer.avg_amount() / 10000.0));
        v[3] = clamp(customer.tx_count_24h() / 100.0f);
        v[4] = clamp((float) (merchant.avg_amount() / 10000.0));
        v[5] = clamp((float) (terminal.km_from_home() / 1000.0));
        v[6] = terminal.is_online() ? 1.0f : 0.0f;
        v[7] = terminal.card_present() ? 1.0f : 0.0f;
        v[8] = lastTx != null ? 1.0f : 0.0f;
        v[9] = lastTx != null ? clamp((float) (lastTx.km_from_current() / 1000.0)) : 0.0f;
        v[10] = encodeMcc(merchant.mcc());
        v[11] = customer.known_merchants().contains(merchant.id()) ? 1.0f : 0.0f;

        int hour = 0;
        try {
            Instant instant = Instant.parse(tx.requested_at());
            hour = instant.atZone(ZoneOffset.UTC).getHour();
        } catch (Exception ignored) {}
        v[12] = hour / 24.0f;

        v[13] = customer.avg_amount() > 0
                ? clamp((float) (tx.amount() / customer.avg_amount()))
                : 0.0f;

        return v;
    }

    private double calculateFraudScore(java.util.List<VPTree.Neighbor<float[]>> neighbors) {
        if (neighbors.isEmpty()) return 0.0;

        int fraudVotes = 0;
        for (final var neighbor : neighbors) {
            float[] item = neighbor.item();
            int idx = findIndex(item);
            if (idx >= 0 && idx < labels.length && labels[idx] == 1) {
                fraudVotes++;
            }
        }

        return (double) fraudVotes / neighbors.size();
    }

    private int findIndex(float[] target) {
        for (int i = 0; i < vectors.length; i++) {
            if (vectors[i] == target) {
                return i;
            }
        }
        return -1;
    }

    private double vectorDistance(float[] a, float[] b) {
        double sum = 0;
        for (int i = 0; i < 14; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return sum;
    }

    static float clamp(float v) {
        return Math.clamp(v, 0f, 1f);
    }

    private float encodeMcc(String mcc) {
        if (mcc == null) return 0.5f;
        try {
            int code = Integer.parseInt(mcc);
            return clamp(code / 10000.0f);
        } catch (NumberFormatException e) {
            return 0.5f;
        }
    }
}
