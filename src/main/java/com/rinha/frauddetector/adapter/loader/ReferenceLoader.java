package com.rinha.frauddetector.adapter.loader;

import com.rinha.frauddetector.domain.FraudReference;
import com.rinha.frauddetector.domain.NormalizationConstants;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public class ReferenceLoader {

  private static final int DIM = 16;
  private static final int FILE_SIGNATURE = 0x52524546;
  private static final int VERSION = 2;

  private NormalizationConstants normalizationConstants;
  private Map<String, Float> mccRiskMap;
  private FraudReference fraudReference;

  public void loadNormalization(NormalizationConstants constants) {
    this.normalizationConstants = constants;
  }

  public void loadMccRisk(Map<String, Float> mccRiskMap) {
    this.mccRiskMap = mccRiskMap;
  }

  public FraudReference loadFraudReference() throws IOException {
    try (var in = new DataInputStream(new BufferedInputStream(open()))) {
      int signature = Integer.reverseBytes(in.readInt());
      if (signature != FILE_SIGNATURE) throw new IOException("Invalid signature: " + signature);
      int version = Integer.reverseBytes(in.readInt());
      if (version != VERSION) throw new IOException("Invalid version: " + version);
      int dim = Integer.reverseBytes(in.readInt());
      if (dim != DIM) throw new IOException("Invalid dimension: " + dim);
      int size = Integer.reverseBytes(in.readInt());

      short[] vectors = new short[size * DIM];
      boolean[] labels = new boolean[size];

      for (int i = 0; i < size; i++) {
        int base = i * DIM;
        for (int j = 0; j < DIM; j++) {
          vectors[base + j] = Short.reverseBytes(in.readShort());
        }
      }

      for (int i = 0; i < size; i++) {
        labels[i] = in.readByte() != 0;
      }

      fraudReference = new FraudReference(vectors, labels);
      return fraudReference;
    }
  }

  private InputStream open() throws IOException {
    InputStream is = getClass().getClassLoader().getResourceAsStream("references.bin");
    if (is == null) throw new IOException("references.bin not found on classpath");
    return is;
  }

  public NormalizationConstants getNormalizationConstants() {
    return normalizationConstants;
  }

  public Map<String, Float> getMccRiskMap() {
    return mccRiskMap;
  }

  public FraudReference getFraudReference() {
    return fraudReference;
  }
}
