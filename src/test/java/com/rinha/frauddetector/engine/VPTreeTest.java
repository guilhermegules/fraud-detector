package com.rinha.frauddetector.engine;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class VPTreeTest {

  private final Distance<Integer> intDist = (a, b) -> Math.abs(a - b);

  @Test
  void buildEmptyTree() {
    VPTree<Integer> tree = new VPTree<>(new ArrayList<>(), intDist);
    List<VPTree.Neighbor<Integer>> neighbors = tree.search(item -> Math.abs(item - 5), 1);
    assertTrue(neighbors.isEmpty());
  }

  @Test
  void buildSingleItemTree() {
    List<Integer> items = Collections.singletonList(5);
    VPTree<Integer> tree = new VPTree<>(items, intDist);

    List<VPTree.Neighbor<Integer>> neighbors = tree.search(item -> Math.abs(item - 5), 1);

    assertEquals(1, neighbors.size());
    assertEquals(5, neighbors.get(0).item());
  }

  @Test
  void searchExactMatch() {
    List<Integer> items = Arrays.asList(1, 2, 3, 4, 5);
    VPTree<Integer> tree = new VPTree<>(items, intDist);

    List<VPTree.Neighbor<Integer>> neighbors = tree.search(item -> Math.abs(item - 3), 1);

    assertEquals(1, neighbors.size());
    assertEquals(3, neighbors.get(0).item());
    assertEquals(0.0, neighbors.get(0).distance(), 0.001);
  }

  @Test
  void searchK2Neighbors() {
    List<Integer> items = Arrays.asList(1, 2, 3, 4, 5);
    VPTree<Integer> tree = new VPTree<>(items, intDist);

    List<VPTree.Neighbor<Integer>> neighbors = tree.search(item -> Math.abs(item - 2.5), 2);

    assertEquals(2, neighbors.size());
    Set<Integer> neighborItems =
        neighbors.stream().map(VPTree.Neighbor::item).collect(Collectors.toSet());
    assertTrue(neighborItems.contains(2));
    assertTrue(neighborItems.contains(3));
  }

  @Test
  void searchKLargerThanItems() {
    List<Integer> items = Arrays.asList(10, 20, 30);
    VPTree<Integer> tree = new VPTree<>(items, intDist);

    List<VPTree.Neighbor<Integer>> neighbors = tree.search(item -> Math.abs(item - 25), 5);

    assertEquals(3, neighbors.size());
    neighbors.sort(Comparator.comparingDouble(VPTree.Neighbor::distance));
    assertEquals(20, neighbors.get(0).item());
    assertEquals(5.0, neighbors.get(0).distance(), 0.001);
    assertEquals(30, neighbors.get(1).item());
    assertEquals(5.0, neighbors.get(1).distance(), 0.001);
    assertEquals(10, neighbors.get(2).item());
    assertEquals(15.0, neighbors.get(2).distance(), 0.001);
  }

  @Test
  void searchVectors() {
    float[] v1 = {1f, 0f};
    float[] v2 = {0f, 1f};
    float[] v3 = {0f, 0f};
    List<float[]> vectors = Arrays.asList(v1, v2, v3);

    Distance<float[]> vecDist =
        (a, b) -> {
          float sum = 0;
          for (int i = 0; i < a.length; i++) {
            float diff = a[i] - b[i];
            sum += diff * diff;
          }
          return sum;
        };

    VPTree<float[]> tree = new VPTree<>(vectors, vecDist);

    List<VPTree.Neighbor<float[]>> neighbors =
        tree.search(
            item -> {
              float sum = 0;
              for (int i = 0; i < item.length; i++) {
                float diff = item[i] - 0.5f;
                sum += diff * diff;
              }
              return sum;
            },
            2);

    assertEquals(2, neighbors.size());
    neighbors.sort(Comparator.comparingDouble(VPTree.Neighbor::distance));
    assertEquals(0.5, neighbors.get(0).distance(), 0.001);
    assertEquals(0.5, neighbors.get(1).distance(), 0.001);
  }

  @Test
  void exactSearchGuarantee() {
    List<Integer> items = new ArrayList<>();
    for (int i = 0; i < 100; i++) items.add(i);

    VPTree<Integer> tree = new VPTree<>(items, intDist);

    List<VPTree.Neighbor<Integer>> neighbors = tree.search(item -> Math.abs(item - 50), 5);

    assertEquals(5, neighbors.size());
    Set<Integer> actual = neighbors.stream().map(VPTree.Neighbor::item).collect(Collectors.toSet());
    assertTrue(actual.contains(50));
    assertTrue(actual.contains(49));
    assertTrue(actual.contains(51));
    assertTrue(actual.contains(48));
    assertTrue(actual.contains(52));
  }

  @Test
  void searchEmptyTree() {
    VPTree<Integer> tree = new VPTree<>(new ArrayList<>(), intDist);
    List<VPTree.Neighbor<Integer>> neighbors = tree.search(item -> Math.abs(item - 5), 3);
    assertTrue(neighbors.isEmpty());
  }

  @Test
  void searchSingleItemTree() {
    List<Integer> items = Collections.singletonList(42);
    VPTree<Integer> tree = new VPTree<>(items, intDist);

    List<VPTree.Neighbor<Integer>> neighbors = tree.search(item -> Math.abs(item - 100), 1);

    assertEquals(1, neighbors.size());
    assertEquals(42, neighbors.get(0).item());
    assertEquals(58.0, neighbors.get(0).distance(), 0.001);
  }

  @Test
  void searchWithDuplicates() {
    List<Integer> items = Arrays.asList(1, 1, 2, 2, 3, 3);
    VPTree<Integer> tree = new VPTree<>(items, intDist);

    List<VPTree.Neighbor<Integer>> neighbors = tree.search(item -> Math.abs(item - 2), 2);

    assertEquals(2, neighbors.size());
    neighbors.sort(Comparator.comparingDouble(VPTree.Neighbor::distance));
    assertEquals(2, neighbors.get(0).item());
    assertEquals(0.0, neighbors.get(0).distance(), 0.001);
  }

  @Test
  void searchKZero() {
    List<Integer> items = Arrays.asList(1, 2, 3, 4, 5);
    VPTree<Integer> tree = new VPTree<>(items, intDist);

    List<VPTree.Neighbor<Integer>> neighbors = tree.search(item -> Math.abs(item - 3), 0);

    assertTrue(neighbors.isEmpty());
  }

  @Test
  void neighborRecord() {
    VPTree.Neighbor<Integer> neighbor = new VPTree.Neighbor<>(5.0, 10);
    assertEquals(5.0, neighbor.distance(), 0.001);
    assertEquals(10, neighbor.item());
  }

  @Test
  void distanceInterface() {
    Distance<String> strDist = (a, b) -> Math.abs(a.length() - b.length());
    List<String> items = Arrays.asList("a", "bb", "ccc", "dddd");
    VPTree<String> tree = new VPTree<>(items, strDist);

    List<VPTree.Neighbor<String>> neighbors = tree.search(item -> Math.abs(item.length() - 2), 2);

    assertEquals(2, neighbors.size());
    assertTrue(neighbors.stream().anyMatch(n -> n.item().equals("bb")));
    assertTrue(neighbors.stream().anyMatch(n -> n.item().equals("a") || n.item().equals("ccc")));
  }

  @Test
  void searchAllItemsWithLargeK() {
    List<Integer> items = Arrays.asList(5, 10, 15, 20);
    VPTree<Integer> tree = new VPTree<>(items, intDist);

    List<VPTree.Neighbor<Integer>> neighbors = tree.search(item -> Math.abs(item - 12), 10);

    assertEquals(4, neighbors.size());
    neighbors.sort(Comparator.comparingDouble(VPTree.Neighbor::distance));
    assertEquals(10, neighbors.get(0).item());
    assertEquals(2.0, neighbors.get(0).distance(), 0.001);
    assertEquals(15, neighbors.get(1).item());
    assertEquals(3.0, neighbors.get(1).distance(), 0.001);
    assertEquals(5, neighbors.get(2).item());
    assertEquals(7.0, neighbors.get(2).distance(), 0.001);
  }

  @Test
  void treeStructureWithTwoItems() {
    List<Integer> items = Arrays.asList(10, 20);
    VPTree<Integer> tree = new VPTree<>(items, intDist);

    List<VPTree.Neighbor<Integer>> neighbors = tree.search(item -> Math.abs(item - 15), 2);

    assertEquals(2, neighbors.size());
    neighbors.sort(Comparator.comparingDouble(VPTree.Neighbor::distance));
    assertEquals(10, neighbors.get(0).item());
    assertEquals(20, neighbors.get(1).item());
  }

  @Test
  void searchReturnsSortedByDistance() {
    List<Integer> items = Arrays.asList(1, 5, 10, 20, 50);
    VPTree<Integer> tree = new VPTree<>(items, intDist);

    List<VPTree.Neighbor<Integer>> neighbors = tree.search(item -> Math.abs(item - 11), 3);

    assertEquals(3, neighbors.size());
    assertTrue(neighbors.get(0).distance() <= neighbors.get(1).distance());
    assertTrue(neighbors.get(1).distance() <= neighbors.get(2).distance());
    assertEquals(10, neighbors.get(0).item());
  }
}
