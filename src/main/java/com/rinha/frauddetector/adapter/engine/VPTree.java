package com.rinha.frauddetector.adapter.engine;

import java.util.*;
import java.util.function.Function;

public class VPTree<T> {
  private final Distance<T> distance;
  private final VPTreeNode<T> root;

  public VPTree(List<T> items, Distance<T> distance) {
    this.distance = distance;
    this.root = build(new ArrayList<>(items));
  }

  public static class VPTreeNode<T> {
    T item;
    float threshold;
    VPTreeNode<T> left;
    VPTreeNode<T> right;

    VPTreeNode(T item) {
      this.item = item;
    }
  }

  public record Neighbor<T>(float distance, T item) {}

  public List<Neighbor<T>> search(Function<T, Float> queryDistance, int k) {
    if (k <= 0) return new ArrayList<>();
    PriorityQueue<Neighbor<T>> pq = new PriorityQueue<>(Math.max(k, 1), comparatorPriorityQueue);
    searchNode(root, queryDistance, pq, k);
    List<Neighbor<T>> result = new ArrayList<>(pq);
    result.sort(Comparator.comparing(n -> n.distance));
    return result;
  }

  private final Comparator<Neighbor<T>> comparatorPriorityQueue =
      (a, b) -> Float.compare(b.distance, a.distance);

  private VPTreeNode<T> build(List<T> items) {
    if (items == null || items.isEmpty()) {
      return null;
    }

    Random random = new Random();
    int vpIndex = random.nextInt(items.size());
    T vp = items.remove(vpIndex);

    VPTreeNode<T> node = new VPTreeNode<>(vp);

    if (items.isEmpty()) {
      return node;
    }

    List<Float> distances = new ArrayList<>(items.size());
    for (T item : items) {
      distances.add(distance.calculate(vp, item));
    }

    List<Float> sorted = new ArrayList<>(distances);
    Collections.sort(sorted);
    float median = sorted.get(sorted.size() / 2);
    node.threshold = median;

    List<T> inner = new ArrayList<>();
    List<T> outer = new ArrayList<>();
    for (int i = 0; i < items.size(); i++) {
      if (distances.get(i) < median) {
        inner.add(items.get(i));
      } else {
        outer.add(items.get(i));
      }
    }

    node.left = build(inner);
    node.right = build(outer);

    return node;
  }

  private void searchNode(
      VPTreeNode<T> node, Function<T, Float> queryDistance, PriorityQueue<Neighbor<T>> pq, int k) {
    if (node == null) return;

    float dist = queryDistance.apply(node.item);
    Neighbor<T> current = new Neighbor<>(dist, node.item);

    if (pq.size() < k) {
      pq.add(current);
    } else if (pq.peek() != null && dist < pq.peek().distance) {
      pq.poll();
      pq.add(current);
    }

    if (node.left == null && node.right == null) return;

    float threshold = node.threshold;
    float maxDist =
        (pq.size() >= k)
            ? (pq.peek() != null ? pq.peek().distance : Float.MAX_VALUE)
            : Float.MAX_VALUE;

    if (dist < threshold) {
      if (dist < threshold + maxDist) {
        searchNode(node.left, queryDistance, pq, k);
      }
      if (dist >= threshold - maxDist) {
        searchNode(node.right, queryDistance, pq, k);
      }
    } else {
      if (dist >= threshold - maxDist) {
        searchNode(node.right, queryDistance, pq, k);
      }
      if (dist < threshold + maxDist) {
        searchNode(node.left, queryDistance, pq, k);
      }
    }
  }
}
