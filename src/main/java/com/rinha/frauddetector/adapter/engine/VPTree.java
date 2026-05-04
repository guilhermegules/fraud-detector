package com.rinha.frauddetector.adapter.engine;

import java.util.*;
import java.util.function.ToDoubleFunction;

public class VPTree<T> {
  private final Distance<T> distance;
  private final VPTreeNode<T> root;

  public VPTree(List<T> items, Distance<T> distance) {
    this.distance = distance;
    this.root = build(new ArrayList<>(items));
  }

  public static class VPTreeNode<T> {
    T item;
    double threshold;
    VPTreeNode<T> left;
    VPTreeNode<T> right;

    VPTreeNode(T item) {
      this.item = item;
    }
  }

  public record Neighbor<T>(double distance, T item) {}

  public List<Neighbor<T>> search(ToDoubleFunction<T> queryDistance, int k) {
    if (k <= 0) return new ArrayList<>();
    PriorityQueue<Neighbor<T>> pq = new PriorityQueue<>(Math.max(k, 1), comparatorPriorityQueue);
    searchNode(root, queryDistance, pq, k);
    List<Neighbor<T>> result = new ArrayList<>(pq);
    result.sort(Comparator.comparingDouble(n -> n.distance));
    return result;
  }

  private final Comparator<Neighbor<T>> comparatorPriorityQueue =
      (a, b) -> Double.compare(b.distance, a.distance);

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

    List<Double> distances = new ArrayList<>(items.size());
    for (T item : items) {
      distances.add(distance.calculate(vp, item));
    }

    List<Double> sorted = new ArrayList<>(distances);
    Collections.sort(sorted);
    double median = sorted.get(sorted.size() / 2);
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
      VPTreeNode<T> node, ToDoubleFunction<T> queryDistance, PriorityQueue<Neighbor<T>> pq, int k) {
    if (node == null) return;

    double dist = queryDistance.applyAsDouble(node.item);
    Neighbor<T> current = new Neighbor<>(dist, node.item);

    if (pq.size() < k) {
      pq.add(current);
    } else if (pq.peek() != null && dist < pq.peek().distance) {
      pq.poll();
      pq.add(current);
    }

    if (node.left == null && node.right == null) return;

    double threshold = node.threshold;
    double maxDist =
        (pq.size() >= k)
            ? (pq.peek() != null ? pq.peek().distance : Double.MAX_VALUE)
            : Double.MAX_VALUE;

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
