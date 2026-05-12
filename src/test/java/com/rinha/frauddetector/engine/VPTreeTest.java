package com.rinha.frauddetector.engine;

import com.rinha.frauddetector.adapter.engine.VPTree;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class VPTreeTest {

  private static VPTree.Neighbor[] heap(int k) {
    VPTree.Neighbor[] h = new VPTree.Neighbor[k];
    for (int i = 0; i < k; i++) h[i] = new VPTree.Neighbor();
    return h;
  }

  @Test
  void buildEmptyTree() {
    VPTree tree = new VPTree(new short[0], new boolean[0], 16);
    VPTree.Neighbor[] h = heap(1);
    tree.search(new short[16], 1, h);
    assertEquals(Long.MAX_VALUE, h[0].distance());
  }

  @Test
  void buildSingleItemTree() {
    short[] vectors = {0, 0, 0, 0, 0, 0, 0, 0, 0, 5000, 0, 0, 0, 0, 0, 0};
    boolean[] labels = {false};
    VPTree tree = new VPTree(vectors, labels, 16);

    short[] query = {0, 0, 0, 0, 0, 0, 0, 0, 0, 5000, 0, 0, 0, 0, 0, 0};
    VPTree.Neighbor[] h = heap(1);
    tree.search(query, 1, h);

    assertEquals(0, h[0].distance());
  }

  @Test
  void searchExactMatch() {
    short[] vectors = {
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        1000, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        2000, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    };
    boolean[] labels = {false, false, false};
    VPTree tree = new VPTree(vectors, labels, 16);

    short[] query = {1000, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    VPTree.Neighbor[] h = heap(1);
    tree.search(query, 1, h);

    assertEquals(0, h[0].distance());
  }

  @Test
  void searchK2Neighbors() {
    short[] vectors = {
        100, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        200, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        300, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    };
    boolean[] labels = {false, false, false};
    VPTree tree = new VPTree(vectors, labels, 16);

    short[] query = {150, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    VPTree.Neighbor[] h = heap(2);
    tree.search(query, 2, h);

    assertEquals(2500, h[0].distance());
    assertEquals(2500, h[1].distance());
  }

  @Test
  void searchVectors() {
    short[] vectors = {
        5000, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 5000, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    };
    boolean[] labels = {false, false, false};
    VPTree tree = new VPTree(vectors, labels, 16);

    short[] query = {2500, 2500, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    VPTree.Neighbor[] h = heap(2);
    tree.search(query, 2, h);

    assertEquals(12500000, h[0].distance());
    assertEquals(12500000, h[1].distance());
  }

  @Test
  void exactSearchGuarantee() {
    short[] vectors = new short[100 * 16];
    boolean[] labels = new boolean[100];
    for (int i = 0; i < 100; i++) {
      vectors[i * 16] = (short) (i * 100);
      labels[i] = i % 2 == 0;
    }
    VPTree tree = new VPTree(vectors, labels, 16);

    short[] query = {2500, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    VPTree.Neighbor[] h = heap(5);
    tree.search(query, 5, h);

    assertTrue(h[0].distance() <= h[1].distance());
  }

  @Test
  void returnsFraudLabels() {
    short[] vectors = {
        1000, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        2000, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    };
    boolean[] labels = {true, false};
    VPTree tree = new VPTree(vectors, labels, 16);

    short[] query = {1000, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    VPTree.Neighbor[] h = heap(2);
    tree.search(query, 2, h);

    long fraudCount = Arrays.stream(h).filter(VPTree.Neighbor::label).count();
    assertEquals(1, fraudCount);
  }
}
