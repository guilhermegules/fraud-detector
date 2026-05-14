package com.rinha.frauddetector.adapter.engine;

import jdk.incubator.vector.*;

import java.util.Arrays;

public class VPTree {

  private static final VectorSpecies<Short> SPECIES = ShortVector.SPECIES_256;
  private static final VectorSpecies<Integer> INT_SPECIES = IntVector.SPECIES_256;

  private final short[] vectors;
  private final boolean[] labels;
  private final int count;
  private static final int STRIDE = 16;

  private int[] leftChild;
  private int[] rightChild;
  private long[] thresholds;
  private int[] vpIdx;
  private int[] leafStart;
  private int[] leafEnd;
  private final int[] sortedIndices;

  private int rootIdx;
  private int nodeCount;

  private static final int LEAF_SIZE = 256;

  public VPTree(short[] vectors, boolean[] labels, int dim) {
    this.vectors = vectors;
    this.labels = labels;
    this.count = labels.length;

    sortedIndices = new int[count];
    for (int i = 0; i < count; i++) sortedIndices[i] = i;

    int initialCapacity = Math.max(64, (count + LEAF_SIZE - 1) / LEAF_SIZE * 2);
    leftChild = new int[initialCapacity];
    rightChild = new int[initialCapacity];
    thresholds = new long[initialCapacity];
    vpIdx = new int[initialCapacity];
    leafStart = new int[initialCapacity];
    leafEnd = new int[initialCapacity];

    nodeCount = 0;
    if (count > 0) {
      buildIterative(new java.util.Random(42));
      reorder();
    }
  }

  private void ensureCapacity(int required) {
    if (required < leftChild.length) return;
    int newLen = Math.max(leftChild.length * 2, required + 1);
    leftChild = Arrays.copyOf(leftChild, newLen);
    rightChild = Arrays.copyOf(rightChild, newLen);
    thresholds = Arrays.copyOf(thresholds, newLen);
    vpIdx = Arrays.copyOf(vpIdx, newLen);
    leafStart = Arrays.copyOf(leafStart, newLen);
    leafEnd = Arrays.copyOf(leafEnd, newLen);
  }

  private int allocateNode() {
    int idx = nodeCount++;
    ensureCapacity(idx);
    return idx;
  }

  private void buildIterative(java.util.Random rng) {
    int stackSize = count / (LEAF_SIZE / 4) + 1;
    int[] startStack = new int[stackSize];
    int[] endStack = new int[stackSize];
    int[] parentStack = new int[stackSize];
    boolean[] isRightStack = new boolean[stackSize];
    int sp = 0;

    startStack[sp] = 0;
    endStack[sp] = count;
    parentStack[sp] = -1;
    isRightStack[sp] = false;
    sp++;

    while (sp > 0) {
      sp--;
      int start = startStack[sp];
      int end = endStack[sp];
      int parent = parentStack[sp];
      boolean isRight = isRightStack[sp];
      int size = end - start;

      int nodeIdx = allocateNode();

      if (parent >= 0) {
        if (isRight) {
          rightChild[parent] = nodeIdx;
        } else {
          leftChild[parent] = nodeIdx;
        }
      } else {
        rootIdx = nodeIdx;
      }

      if (size <= LEAF_SIZE) {
        leftChild[nodeIdx] = -1;
        rightChild[nodeIdx] = -1;
        leafStart[nodeIdx] = start;
        leafEnd[nodeIdx] = end;
        continue;
      }

      int vpPos = start + rng.nextInt(size);
      int vp = sortedIndices[vpPos];
      vpIdx[nodeIdx] = start;

      sortedIndices[vpPos] = sortedIndices[start];
      sortedIndices[start] = vp;

      int sampleSize = Math.min(256, size - 1);
      long[] distSamples = new long[sampleSize];
      var vpVec = ShortVector.fromArray(SPECIES, vectors, vp * STRIDE);

      for (int i = 0; i < sampleSize; i++) {
        int idx = start + 1 + rng.nextInt(size - 1);
        distSamples[i] = distance(vpVec, sortedIndices[idx]);
      }

      Arrays.sort(distSamples);
      long median = distSamples[sampleSize / 2];
      thresholds[nodeIdx] = median;

      int mid = partition(start + 1, end, vp, median);
      int remaining = end - start - 1;
      int leftSize = mid - (start + 1);
      int rightSize = remaining - leftSize;
      if (leftSize < remaining / 4 || rightSize < remaining / 4) {
        mid = (start + 1 + end) >>> 1;
      }

      if (sp + 2 > stackSize) {
        int newSize = Math.max(stackSize * 2, sp + 2);
        startStack = Arrays.copyOf(startStack, newSize);
        endStack = Arrays.copyOf(endStack, newSize);
        parentStack = Arrays.copyOf(parentStack, newSize);
        isRightStack = Arrays.copyOf(isRightStack, newSize);
        stackSize = newSize;
      }

      startStack[sp] = mid;
      endStack[sp] = end;
      parentStack[sp] = nodeIdx;
      isRightStack[sp] = true;
      sp++;

      startStack[sp] = start + 1;
      endStack[sp] = mid;
      parentStack[sp] = nodeIdx;
      isRightStack[sp] = false;
      sp++;
    }
  }

  private void reorder() {
    short[] tmp = new short[STRIDE];
    for (int i = 0; i < count; i++) {
      if (sortedIndices[i] == i) continue;
      System.arraycopy(vectors, i * STRIDE, tmp, 0, STRIDE);
      boolean labelTmp = labels[i];
      int current = i;
      while (true) {
        int source = sortedIndices[current];
        if (source == i) {
          System.arraycopy(tmp, 0, vectors, current * STRIDE, STRIDE);
          labels[current] = labelTmp;
          sortedIndices[current] = current;
          break;
        }
        System.arraycopy(vectors, source * STRIDE, vectors, current * STRIDE, STRIDE);
        labels[current] = labels[source];
        sortedIndices[current] = current;
        current = source;
      }
    }
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

    if (count == 0) return;

    var queryVec = ShortVector.fromArray(SPECIES, target, 0);

    int[] stack = new int[nodeCount];
    int sp = 0;
    stack[sp++] = rootIdx;

    while (sp > 0) {
      int nodeIdx = stack[--sp];

      if (leftChild[nodeIdx] == -1) {
        for (int i = leafStart[nodeIdx]; i < leafEnd[nodeIdx]; i++) {
          long dist = distance(queryVec, i);
          if (dist >= heap[k - 1].distance) continue;
          int j = k - 2;
          while (j >= 0 && heap[j].distance > dist) {
            heap[j + 1].distance = heap[j].distance;
            heap[j + 1].label = heap[j].label;
            j--;
          }
          heap[j + 1].set(dist, labels[i]);
        }
      } else {
        int vpPos = vpIdx[nodeIdx];
        long dist = distance(queryVec, vpPos);

        if (dist < heap[k - 1].distance) {
          int j = k - 2;
          while (j >= 0 && heap[j].distance > dist) {
            heap[j + 1].distance = heap[j].distance;
            heap[j + 1].label = heap[j].label;
            j--;
          }
          heap[j + 1].set(dist, labels[vpPos]);
        }

        long threshold = thresholds[nodeIdx];

        if (dist < threshold) {
          if (heap[k - 1].distance > threshold - dist) {
            stack[sp++] = rightChild[nodeIdx];
          }
          stack[sp++] = leftChild[nodeIdx];
        } else {
          if (heap[k - 1].distance > dist - threshold) {
            stack[sp++] = leftChild[nodeIdx];
          }
          stack[sp++] = rightChild[nodeIdx];
        }
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
