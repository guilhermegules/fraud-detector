package com.rinha.frauddetector.application;

import com.rinha.frauddetector.adapter.engine.VPTree;
import com.rinha.frauddetector.adapter.loader.ReferenceLoader;
import com.rinha.frauddetector.domain.FraudScore;
import com.rinha.frauddetector.domain.NormalizationConstants;
import com.rinha.frauddetector.domain.TransactionVector;
import com.rinha.frauddetector.dto.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public final class KnnFraudDetectionService {

  private VPTree tree;
  private final ReferenceLoader referenceLoader;
  private final AtomicBoolean ready = new AtomicBoolean(false);

  private static final ThreadLocal<short[]> VECTOR_BUFFER = ThreadLocal.withInitial(() -> new short[16]);
  private static final ThreadLocal<VPTree.Neighbor[]> HEAP_BUFFER =
      ThreadLocal.withInitial(() -> {
        var h = new VPTree.Neighbor[FraudScore.K];
        for (int i = 0; i < FraudScore.K; i++) h[i] = new VPTree.Neighbor();
        return h;
      });

  public KnnFraudDetectionService(ReferenceLoader referenceLoader) {
    this.referenceLoader = referenceLoader;
  }

  public void initialize() throws IOException {
    var ref = referenceLoader.loadFraudReference();
    tree = new VPTree(ref.vectors(), ref.labels(), 16);
    warmup();
    ready.set(true);
  }

  public boolean isReady() {
    return ready.get();
  }

  public FraudScore evaluate(FraudRequest request) {
    if (!ready.get()) return FraudScore.SAFE;
    short[] vector = VECTOR_BUFFER.get();
    TransactionVector.toArray(request,
        referenceLoader.getNormalizationConstants(),
        referenceLoader.getMccRiskMap(),
        vector);

    var heap = HEAP_BUFFER.get();
    tree.search(vector, FraudScore.K, heap);

    int fraudNeighbors = 0;
    for (int i = 0; i < FraudScore.K; i++) {
      if (heap[i].distance() == Long.MAX_VALUE) break;
      if (heap[i].label()) fraudNeighbors++;
    }

    return FraudScore.fromFraudCount(fraudNeighbors);
  }

  private void warmup() {
    var random = new Random(42);
    var vector = new short[16];
    var heap = new VPTree.Neighbor[FraudScore.K];
    for (int i = 0; i < FraudScore.K; i++) heap[i] = new VPTree.Neighbor();

    var constants = referenceLoader.getNormalizationConstants();
    var mccRiskMap = referenceLoader.getMccRiskMap();

    var payloads = new FraudRequest[] {
      new FraudRequest("warmup-1",
          new TransactionDTO(384.88f, 3, "2026-03-11T20:23:35Z"),
          new CustomerDTO(769.76f, 3, List.of("MERC-009", "MERC-001")),
          new MerchantDTO("MERC-001", "5912", 298.95f),
          new TerminalDTO(false, true, 13.709f),
          new LastTransactionDTO("2026-03-11T14:58:35Z", 18.862f)),
      new FraudRequest("warmup-2",
          new TransactionDTO(9500f, 12, "2026-03-11T03:15:00Z"),
          new CustomerDTO(500f, 15, List.of()),
          new MerchantDTO("MERC-999", "1234", 8000f),
          new TerminalDTO(true, false, 999f),
          new LastTransactionDTO("2026-03-10T03:00:00Z", 998f)),
      new FraudRequest("warmup-3",
          new TransactionDTO(45.50f, 1, "2026-03-11T12:00:00Z"),
          new CustomerDTO(50f, 5, List.of("MERC-001")),
          new MerchantDTO("MERC-001", "5411", 40f),
          new TerminalDTO(false, true, 2.5f),
          new LastTransactionDTO("2026-03-11T10:30:00Z", 0.5f)),
      new FraudRequest("warmup-4",
          new TransactionDTO(200f, 2, "2026-03-11T08:00:00Z"),
          new CustomerDTO(100f, 1, List.of()),
          new MerchantDTO("MERC-555", "1234", 150f),
          new TerminalDTO(true, false, 500f),
          null),
      new FraudRequest("warmup-5",
          new TransactionDTO(5000f, 6, "2026-03-11T22:30:00Z"),
          new CustomerDTO(0f, 20, List.of()),
          new MerchantDTO("MERC-999", "7995", 6000f),
          new TerminalDTO(true, true, 900f),
          null)
    };

    for (int p = 0; p < 2000; p++) {
      var req = payloads[p % 5];
      TransactionVector.toArray(req, constants, mccRiskMap, vector);
      tree.search(vector, FraudScore.K, heap);
      countFraud(heap);
    }

    for (int q = 0; q < 10000; q++) {
      for (int j = 0; j < 16; j++) {
        vector[j] = (short) random.nextInt(10001);
      }
      tree.search(vector, FraudScore.K, heap);
      countFraud(heap);
    }
  }

  private static int countFraud(VPTree.Neighbor[] heap) {
    int n = 0;
    for (int i = 0; i < FraudScore.K; i++) {
      if (heap[i].distance() == Long.MAX_VALUE) break;
      if (heap[i].label()) n++;
    }
    return n;
  }
}
