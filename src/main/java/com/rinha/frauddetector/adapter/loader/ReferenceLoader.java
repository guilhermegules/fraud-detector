package com.rinha.frauddetector.adapter.loader;

import com.rinha.frauddetector.domain.FraudReference;
import com.rinha.frauddetector.domain.NormalizationConstants;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static com.rinha.frauddetector.config.Constants.WEIGHTS;

public class ReferenceLoader {

  private static final int DIM = 16;

  private static final int FILE_SIGNATURE = 0x52524546;
  private static final int VERSION = 2;

  private final ObjectMapper mapper = new ObjectMapper();

  private NormalizationConstants normalizationConstants;
  private Map<String, Float> mccRiskMap;
  private FraudReference fraudReference;

  public void loadNormalization() throws IOException {
    try (InputStream is = new ClassPathResource("normalization.json").getInputStream()) {
      normalizationConstants = mapper.readValue(is, NormalizationConstants.class);
    }
  }

  public void loadMccRisk() throws IOException {
    try (InputStream is = new ClassPathResource("mcc_risk.json").getInputStream()) {
      mccRiskMap = mapper.readValue(is, new TypeReference<>() {});
    }
  }

  public FraudReference loadFraudReference() throws IOException {
    int size;
    try (var in = new DataInputStream(new BufferedInputStream(open()))) {
      size = readHeader(in);
    }

    short[] vectors = new short[size * DIM];
    boolean[] labels = new boolean[size];

    try (var in = new DataInputStream(new BufferedInputStream(open()))) {
      readHeader(in);

      for (int i = 0; i < size; i++) {
        int base = i * DIM;
        for (int j = 0; j < DIM; j++) {
          short val = readShortLE(in);
          vectors[base + j] = (short) Math.round(val * WEIGHTS[j]);
        }
      }

      byte[] rawLabels = new byte[size];
      in.readFully(rawLabels);
      for (int i = 0; i < size; i++) {
        labels[i] = rawLabels[i] != 0;
      }
    }

    fraudReference = new FraudReference(vectors, labels);
    return fraudReference;
  }

  private int readHeader(DataInputStream in) throws IOException {
    int signature = readIntLE(in);
    if (signature != FILE_SIGNATURE) {
      throw new IOException("Invalid signature: " + signature);
    }
    int version = readIntLE(in);
    if (version != VERSION) {
      throw new IOException("Invalid version: " + version);
    }
    int dim = readIntLE(in);
    if (dim != DIM) {
      throw new IOException("Invalid dimension: " + dim);
    }
    return readIntLE(in);
  }

  private InputStream open() throws IOException {
    return new ClassPathResource("references.bin").getInputStream();
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

  private int readIntLE(DataInputStream in) throws IOException {
    return Integer.reverseBytes(in.readInt());
  }

  private short readShortLE(DataInputStream in) throws IOException {
    return Short.reverseBytes(in.readShort());
  }
}
