package com.rinha.frauddetector.engine;

import com.rinha.frauddetector.adapter.engine.VPTree;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class VPTreeTest {

  @Test
  void buildEmptyTree() {
    VPTree tree = new VPTree(new short[0], new boolean[0], 16);
    List<VPTree.Neighbor> neighbors = tree.search(new short[16], 1);
    assertTrue(neighbors.isEmpty());
  }

  @Test
  void buildSingleItemTree() {
    short[] vectors = {0, 0, 0, 0, 0, 0, 0, 0, 0, 5000, 0, 0, 0, 0, 0, 0};
    boolean[] labels = {false};
    VPTree tree = new VPTree(vectors, labels, 16);

    short[] query = {0, 0, 0, 0, 0, 0, 0, 0, 0, 5000, 0, 0, 0, 0, 0, 0};
    List<VPTree.Neighbor> neighbors = tree.search(query, 1);

    assertEquals(1, neighbors.size());
    assertEquals(0, neighbors.get(0).distance());
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
    List<VPTree.Neighbor> neighbors = tree.search(query, 1);

    assertEquals(1, neighbors.size());
    assertEquals(0, neighbors.get(0).distance());
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
    List<VPTree.Neighbor> neighbors = tree.search(query, 2);

    assertEquals(2, neighbors.size());
    neighbors.sort(Comparator.comparingInt(VPTree.Neighbor::distance));
    assertEquals(2500, neighbors.get(0).distance());
    assertEquals(2500, neighbors.get(1).distance());
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
    List<VPTree.Neighbor> neighbors = tree.search(query, 2);

    assertEquals(2, neighbors.size());
    neighbors.sort(Comparator.comparingInt(VPTree.Neighbor::distance));
    assertEquals(12500000, neighbors.get(0).distance());
    assertEquals(12500000, neighbors.get(1).distance());
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
    List<VPTree.Neighbor> neighbors = tree.search(query, 5);

    assertEquals(5, neighbors.size());
    neighbors.sort(Comparator.comparingInt(VPTree.Neighbor::distance));
    assertTrue(neighbors.get(0).distance() <= neighbors.get(1).distance());
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
    List<VPTree.Neighbor> neighbors = tree.search(query, 2);

    assertEquals(2, neighbors.size());
    long fraudCount = neighbors.stream().filter(VPTree.Neighbor::label).count();
    assertEquals(1, fraudCount);
  }
}