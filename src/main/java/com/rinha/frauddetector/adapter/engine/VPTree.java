package com.rinha.frauddetector.adapter.engine;

import jdk.incubator.vector.*;

import java.util.Arrays;
import java.util.Random;

public class VPTree {

  private static final VectorSpecies<Short> SPECIES = ShortVector.SPECIES_256;
  private static final VectorSpecies<Integer> INT_SPECIES = IntVector.SPECIES_256;

  private final short[] vectors;
  private final boolean[] labels;
  private final int count;
  private static final int STRIDE = 16;

  private final int[] leftChild;
  private final int[] rightChild;
  private final long[] thresholds;
  private final int[] vpIdx;
  private final int[] leafStart;
  private final int[] leafEnd;
  private final int[] sortedIndices;

  private int rootIdx;
  private int nodeCount;

  private static final int LEAF_SIZE = 2048;
  private static final Random RANDOM = new Random(42);

  public VPTree(short[] vectors, boolean[] labels, int dim) {
    this.vectors = vectors;
    this.labels = labels;
    this.count = labels.length;

    sortedIndices = new int[count];
    for (int i = 0; i < count; i++) sortedIndices[i] = i;

    int maxNodes = count > 0 ? 2 * count + 1 : 1;
    leftChild = new int[maxNodes];
    rightChild = new int[maxNodes];
    thresholds = new long[maxNodes];
    vpIdx = new int[maxNodes];
    leafStart = new int[maxNodes];
    leafEnd = new int[maxNodes];

    nodeCount = 0;
    if (count > 0) rootIdx = build(0, count);
  }

  private int build(int start, int end) {
    int nodeIdx = nodeCount++;
    int size = end - start;

    if (size <= LEAF_SIZE) {
      leftChild[nodeIdx] = -1;
      rightChild[nodeIdx] = -1;
      leafStart[nodeIdx] = start;
      leafEnd[nodeIdx] = end;
      return nodeIdx;
    }

    int vpPos = start + RANDOM.nextInt(size);
    int vp = sortedIndices[vpPos];
    vpIdx[nodeIdx] = vp;

    sortedIndices[vpPos] = sortedIndices[start];
    sortedIndices[start] = vp;

    int sampleSize = Math.min(256, size - 1);
    long[] distSamples = new long[sampleSize];
    var vpVec = ShortVector.fromArray(SPECIES, vectors, vp * STRIDE);

    for (int i = 0; i < sampleSize; i++) {
      int idx = start + 1 + RANDOM.nextInt(size - 1);
      distSamples[i] = distance(vpVec, sortedIndices[idx]);
    }

    Arrays.sort(distSamples);
    long median = distSamples[sampleSize / 2];
    thresholds[nodeIdx] = median;

    int mid = partition(start + 1, end, vp, median);

    leftChild[nodeIdx] = build(start + 1, mid);
    rightChild[nodeIdx] = build(mid, end);
    return nodeIdx;
  }

  private int partition(int start, int end, int vp, long median) {
    int left = start;
    int right = end - 1;
    var vpVec = ShortVector.fromArray(SPECIES, vectors, vp * STRIDE);

    while (true) {
      while (left <= right && distance(vpVec, sortedIndices[left]) < median) left++;
      while (left <= right && distance(vpVec, sortedIndices[right]) >= median) right--;
      if (left >= right) break;
      int tmp = sortedIndices[left];
      sortedIndices[left] = sortedIndices[right];
      sortedIndices[right] = tmp;
      left++;
      right--;
    }
    return left;
  }

  private long distance(ShortVector va, int dataIdx) {
    var vb = ShortVector.fromArray(SPECIES, vectors, dataIdx * STRIDE);
    var diff = va.sub(vb);
    var lo = (IntVector) diff.convertShape(VectorOperators.S2I, INT_SPECIES, 0);
    var hi = (IntVector) diff.convertShape(VectorOperators.S2I, INT_SPECIES, 1);
    return (long) lo.mul(lo).reduceLanes(VectorOperators.ADD)
         + (long) hi.mul(hi).reduceLanes(VectorOperators.ADD);
  }

  public void search(short[] target, int k, Neighbor[] heap) {
    for (int i = 0; i < k; i++) heap[i].set(Long.MAX_VALUE, false);

    if (count > 0) {
      var queryVec = ShortVector.fromArray(SPECIES, target, 0);
      searchNode(rootIdx, queryVec, heap);
    }
  }

  private void searchNode(int nodeIdx, ShortVector queryVec, Neighbor[] heap) {
    int k = heap.length;

    if (leftChild[nodeIdx] == -1) {
      for (int i = leafStart[nodeIdx]; i < leafEnd[nodeIdx]; i++) {
        int idx = sortedIndices[i];
        long dist = distance(queryVec, idx);
        if (dist >= heap[k - 1].distance) continue;
        int j = k - 2;
        while (j >= 0 && heap[j].distance > dist) {
          heap[j + 1].distance = heap[j].distance;
          heap[j + 1].label = heap[j].label;
          j--;
        }
        heap[j + 1].set(dist, labels[idx]);
      }
      return;
    }

    int vp = vpIdx[nodeIdx];
    long dist = distance(queryVec, vp);

    if (dist < heap[k - 1].distance) {
      int j = k - 2;
      while (j >= 0 && heap[j].distance > dist) {
        heap[j + 1].distance = heap[j].distance;
        heap[j + 1].label = heap[j].label;
        j--;
      }
      heap[j + 1].set(dist, labels[vp]);
    }

    long threshold = thresholds[nodeIdx];

    if (dist < threshold) {
      searchNode(leftChild[nodeIdx], queryVec, heap);
      if (heap[k - 1].distance > threshold - dist) {
        searchNode(rightChild[nodeIdx], queryVec, heap);
      }
    } else {
      searchNode(rightChild[nodeIdx], queryVec, heap);
      if (heap[k - 1].distance > dist - threshold) {
        searchNode(leftChild[nodeIdx], queryVec, heap);
      }
    }
  }

  public static class Neighbor {
    public long distance;
    public boolean label;

    public Neighbor() {}

    public void set(long distance, boolean label) {
      this.distance = distance;
      this.label = label;
    }

    public long distance() { return distance; }
    public boolean label() { return label; }
  }
}
