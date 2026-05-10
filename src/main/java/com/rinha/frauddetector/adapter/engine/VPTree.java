package com.rinha.frauddetector.adapter.engine;

import jdk.incubator.vector.*;

import java.util.*;

public class VPTree {

  private static final VectorSpecies<Short> SPECIES = ShortVector.SPECIES_256;
  private static final VectorSpecies<Integer> INT_SPECIES = IntVector.SPECIES_256;

  private final short[] vectors;
  private final boolean[] labels;
  private final int dim;

  private final VPTreeNode root;

  private static final Random RANDOM = new Random();
  private static final int LEAF_SIZE = 32;

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

    if (end - start <= LEAF_SIZE) {
      node.leafIndices = Arrays.copyOfRange(indices, start, end);
      return node;
    }

    int vpIdx = start + RANDOM.nextInt(end - start);
    node.index = indices[vpIdx];
    node.label = labels[node.index];

    if (end - start > 1) {
      int sampleSize = Math.min(32, end - start - 1);
      int[] distances = new int[sampleSize];

      var vpVec = ShortVector.fromArray(SPECIES, vectors, node.index * dim);

      for (int i = 0; i < sampleSize; i++) {
        int idx = start + RANDOM.nextInt(end - start);
        if (idx == vpIdx) { i--; continue; }
        distances[i] = distanceSIMD(vpVec, indices[idx]);
      }

      Arrays.sort(distances);
      node.threshold = distances[sampleSize / 2];

      int leftSize = 0;
      for (int i = start; i < end; i++) {
        if (i == vpIdx) continue;
        if (distanceSIMD(vpVec, indices[i]) < node.threshold) {
          leftSize++;
        }
      }

      int[] leftIndices = new int[leftSize];
      int[] rightIndices = new int[end - start - 1 - leftSize];
      int li = 0, ri = 0;

      for (int i = start; i < end; i++) {
        if (i == vpIdx) continue;
        int d = distanceSIMD(vpVec, indices[i]);
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

  private int distanceSIMD(short[] a, int bIndex) {
    int base = bIndex * dim;
    var va = ShortVector.fromArray(SPECIES, a, 0);
    var vb = ShortVector.fromArray(SPECIES, vectors, base);
    var vd = va.sub(vb);
    var vLo = (IntVector) vd.convertShape(VectorOperators.S2I, INT_SPECIES, 0);
    var vHi = (IntVector) vd.convertShape(VectorOperators.S2I, INT_SPECIES, 1);
    return vLo.mul(vLo).reduceLanes(VectorOperators.ADD)
         + vHi.mul(vHi).reduceLanes(VectorOperators.ADD);
  }

  private int distanceSIMD(ShortVector va, int bIndex) {
    int base = bIndex * dim;
    var vb = ShortVector.fromArray(SPECIES, vectors, base);
    var vd = va.sub(vb);
    var vLo = (IntVector) vd.convertShape(VectorOperators.S2I, INT_SPECIES, 0);
    var vHi = (IntVector) vd.convertShape(VectorOperators.S2I, INT_SPECIES, 1);
    return vLo.mul(vLo).reduceLanes(VectorOperators.ADD)
         + vHi.mul(vHi).reduceLanes(VectorOperators.ADD);
  }

  public Neighbor[] search(short[] target, int k) {
    Neighbor[] heap = new Neighbor[k];
    for (int i = 0; i < k; i++) {
      heap[i] = new Neighbor(Integer.MAX_VALUE, false);
    }
    if (root != null) {
      search(root, target, heap);
    }
    sortAscending(heap);
    return heap;
  }

  private static void sortAscending(Neighbor[] heap) {
    int n = heap.length;
    for (int i = 1; i < n; i++) {
      if (heap[i].distance == Integer.MAX_VALUE) {
        n = i;
        break;
      }
    }
    for (int i = 1; i < n; i++) {
      Neighbor key = heap[i];
      int j = i - 1;
      while (j >= 0 && heap[j].distance > key.distance) {
        heap[j + 1] = heap[j];
        j--;
      }
      heap[j + 1] = key;
    }
  }

  private void search(VPTreeNode node, short[] target, Neighbor[] heap) {
    int k = heap.length;

    if (node.leafIndices != null) {
      for (int idx : node.leafIndices) {
        int dist = distanceSIMD(target, idx);
        if (dist < heap[k - 1].distance) {
          int i = k - 2;
          while (i >= 0 && heap[i].distance > dist) {
            heap[i + 1] = heap[i];
            i--;
          }
          heap[i + 1] = new Neighbor(dist, labels[idx]);
        }
      }
      return;
    }

    int dist = distanceSIMD(target, node.index);

    if (dist < heap[k - 1].distance) {
      int i = k - 2;
      while (i >= 0 && heap[i].distance > dist) {
        heap[i + 1] = heap[i];
        i--;
      }
      heap[i + 1] = new Neighbor(dist, node.label);
    }

    if (node.left == null && node.right == null) return;

    double threshold = node.threshold;
    if (dist < threshold) {
      search(node.left, target, heap);
      if (heap[k - 1].distance > Math.abs(dist - threshold)) {
        search(node.right, target, heap);
      }
    } else {
      search(node.right, target, heap);
      if (heap[k - 1].distance > Math.abs(dist - threshold)) {
        search(node.left, target, heap);
      }
    }
  }

  private static class VPTreeNode {
    int index;
    boolean label;
    double threshold;
    int[] leafIndices;
    VPTreeNode left;
    VPTreeNode right;
  }

  public static class Neighbor {
    public int distance;
    public boolean label;

    public Neighbor(int distance, boolean label) {
      this.distance = distance;
      this.label = label;
    }

    public int distance() { return distance; }
    public boolean label() { return label; }
  }
}
