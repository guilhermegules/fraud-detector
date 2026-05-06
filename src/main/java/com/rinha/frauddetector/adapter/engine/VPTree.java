package com.rinha.frauddetector.adapter.engine;

import java.util.*;

public class VPTree {

  private final short[] vectors;
  private final boolean[] labels;
  private final int dim;

  private final VPTreeNode root;

  public VPTree(short[] vectors, boolean[] labels, int dim) {
    this.vectors = vectors;
    this.labels = labels;
    this.dim = dim;

    int size = labels.length;
    int[] indices = new int[size];
    for (int i = 0; i < size; i++) indices[i] = i;

    this.root = build(indices, 0, size);
  }

  private VPTreeNode build(int[] indices, int start, int end) {
    if (start >= end) return null;

    VPTreeNode node = new VPTreeNode();
    int vpIndex = indices[start];
    node.index = vpIndex;
    node.point = getVector(vpIndex);
    node.label = labels[vpIndex];

    if (end - start > 1) {
      int median = (start + end) / 2;

      for (int i = start + 1; i < end - 1; i++) {
        int bestIdx = i;

        for (int j = i + 1; j < end; j++) {
          int distJ = distance(node.point, getVector(indices[j]));
          int distBest = distance(node.point, getVector(indices[bestIdx]));

          if (distJ < distBest) {
            bestIdx = j;
          }
        }

        // swap
        int tmp = indices[i];
        indices[i] = indices[bestIdx];
        indices[bestIdx] = tmp;
      }

      node.threshold = distance(node.point, getVector(indices[median]));

      node.left = build(indices, start + 1, median);
      node.right = build(indices, median, end);
    }

    return node;
  }

  static int distance(short[] a, short[] b) {
    int sum = 0;
    for (int i = 0; i < 14; i++) {
      int d = a[i] - b[i];
      sum += d * d;
    }
    return sum;
  }

  private short[] getVector(int index) {
    short[] v = new short[dim];
    System.arraycopy(vectors, index * dim, v, 0, dim);
    return v;
  }

  public List<Neighbor> search(short[] target, int k) {
    PriorityQueue<Neighbor> heap =
            new PriorityQueue<>((a, b) -> b.distance - a.distance);

    search(root, target, k, heap);
    return new ArrayList<>(heap);
  }

  private void search(VPTreeNode node, short[] target, int k,
                      PriorityQueue<Neighbor> heap) {
    if (node == null) return;

    int dist = distance(target, node.point);

    if (heap.size() < k) {
      heap.add(new Neighbor(dist, node.label));
    } else if (dist < heap.peek().distance) {
      heap.poll();
      heap.add(new Neighbor(dist, node.label));
    }

    if (node.left == null && node.right == null) return;

    if (dist < node.threshold) {
      search(node.left, target, k, heap);
      if (heap.size() < k || Math.abs(dist - node.threshold) < heap.peek().distance) {
        search(node.right, target, k, heap);
      }
    } else {
      search(node.right, target, k, heap);
      if (heap.size() < k || Math.abs(dist - node.threshold) < heap.peek().distance) {
        search(node.left, target, k, heap);
      }
    }
  }

  private static class VPTreeNode {
    short[] point;
    boolean label;
    int index;

    double threshold;
    VPTreeNode left;
    VPTreeNode right;
  }

  public record Neighbor(int distance, boolean label) {

  }
}
