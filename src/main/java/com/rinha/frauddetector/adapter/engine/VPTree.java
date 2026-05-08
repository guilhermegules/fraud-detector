package com.rinha.frauddetector.adapter.engine;

import java.util.*;

public class VPTree {

  private final short[] vectors;
  private final boolean[] labels;
  private final int dim;

  private final VPTreeNode root;
  private final short[] searchBuffer;
  private final short[] buildBuffer;

  private static final Random RANDOM = new Random();

  public VPTree(short[] vectors, boolean[] labels, int dim) {
    this.vectors = vectors;
    this.labels = labels;
    this.dim = dim;
    this.searchBuffer = new short[dim];
    this.buildBuffer = new short[dim];

    int size = labels.length;
    int[] indices = new int[size];
    for (int i = 0; i < size; i++) indices[i] = i;

    this.root = build(indices, 0, size);
  }

  private VPTreeNode build(int[] indices, int start, int end) {
    if (start >= end) return null;

    if (end - start <= 8) return null;

    VPTreeNode node = new VPTreeNode();

    int vpIdx = start + RANDOM.nextInt(end - start);
    node.index = indices[vpIdx];
    node.label = labels[node.index];

    if (end - start > 1) {
      int sampleSize = Math.min(32, end - start - 1);
      int[] distances = new int[sampleSize];

      System.arraycopy(vectors, node.index * dim, buildBuffer, 0, dim);

      for (int i = 0; i < sampleSize; i++) {
        int idx = start + RANDOM.nextInt(end - start);
        if (idx == vpIdx) { i--; continue; }
        distances[i] = distance(buildBuffer, indices[idx]);
      }

      Arrays.sort(distances);
      node.threshold = distances[sampleSize / 2];

      int leftSize = 0;
      for (int i = start; i < end; i++) {
        if (i == vpIdx) continue;
        if (distance(buildBuffer, indices[i]) < node.threshold) {
          leftSize++;
        }
      }

      int[] leftIndices = new int[leftSize];
      int[] rightIndices = new int[end - start - 1 - leftSize];
      int li = 0, ri = 0;

      for (int i = start; i < end; i++) {
        if (i == vpIdx) continue;
        int d = distance(buildBuffer, indices[i]);
        if (d < node.threshold) {
          leftIndices[li++] = indices[i];
        } else {
          rightIndices[ri++] = indices[i];
        }
      }

      node.left = build(leftIndices, 0, li);
      node.right = build(rightIndices, 0, ri);
    }

    return node;
  }

  private int distance(short[] a, int bIndex) {
    int sum = 0;
    int base = bIndex * dim;
    for (int i = 0; i < dim; i++) {
      int d = a[i] - vectors[base + i];
      sum += d * d;
    }
    return sum;
  }

  private int distanceFromIndex(short[] target, int index) {
    int sum = 0;
    int base = index * dim;
    for (int i = 0; i < dim; i++) {
      int d = target[i] - vectors[base + i];
      sum += d * d;
    }
    return sum;
  }

  private short[] getVector(int index) {
    System.arraycopy(vectors, index * dim, searchBuffer, 0, dim);
    return searchBuffer;
  }

  public List<Neighbor> search(short[] target, int k) {
    PriorityQueue<Neighbor> heap =
            new PriorityQueue<>(k, (a, b) -> b.distance - a.distance);

    search(root, target, k, heap);
    return new ArrayList<>(heap);
  }

  private void search(VPTreeNode node, short[] target, int k,
                      PriorityQueue<Neighbor> heap) {
    if (node == null) return;

    int dist = distanceFromIndex(target, node.index);

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
    int index;
    boolean label;
    double threshold;
    VPTreeNode left;
    VPTreeNode right;
  }

  public record Neighbor(int distance, boolean label) {}
}
