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

public class ReferenceLoader {

  private static final int DIM = 14;
  private static final int HOURS = 24;
  private static final int DAYS = 7;
  private static final int TX_BUCKETS = 4;
  public static final int BUCKET_COUNT = 8 * HOURS * DAYS * TX_BUCKETS;

  private static final int FILE_SIGNATURE = 0x52524546;
  private static final int VERSION = 1;

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
    int size = 0;
    try (var in = new DataInputStream(new BufferedInputStream(open()))) {
      size = readHeader(in);
    }

    short[] allVectors = new short[size * DIM];
    boolean[] allLabels = new boolean[size];
    int[] bucketCounts = new int[BUCKET_COUNT];
    int[] bucketIndices = new int[size];

    try (var in = new DataInputStream(new BufferedInputStream(open()))) {
      readHeader(in);
      short[] vector = new short[DIM];

      for (int i = 0; i < size; i++) {
        for (int j = 0; j < DIM; j++) {
          vector[j] = readShortLE(in);
        }
        allLabels[i] = in.readByte() != 0;

        System.arraycopy(vector, 0, allVectors, i * DIM, DIM);

        int b = bucket(vector[9], vector[10], vector[11], vector[3], vector[4], vector[8]);
        bucketIndices[i] = b;
        bucketCounts[b]++;
      }
    }

    int[] bucketStarts = new int[BUCKET_COUNT + 1];
    for (int i = 0; i < BUCKET_COUNT; i++) {
      bucketStarts[i + 1] = bucketStarts[i] + bucketCounts[i];
    }

    short[] vectors = new short[size * DIM];
    boolean[] labels = new boolean[size];
    int[] positions = bucketStarts.clone();

    for (int i = 0; i < size; i++) {
      int b = bucketIndices[i];
      int pos = positions[b]++;
      int baseDst = pos * DIM;
      int baseSrc = i * DIM;

      System.arraycopy(allVectors, baseSrc, vectors, baseDst, DIM);
      labels[pos] = allLabels[i];
    }

    fraudReference = new FraudReference(vectors, labels, bucketStarts, DIM);
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

  private int bucket(short online, short cardPresent, short knownMerchant,
                     short hourValue, short dayValue, short txCountValue) {

    int binary = 0;
    if (online > 5000) binary |= 1;
    if (cardPresent > 5000) binary |= 2;
    if (knownMerchant > 5000) binary |= 4;

    int hour = Math.round((hourValue * 23f) / 10000f);
    hour = Math.clamp(hour, 0, HOURS - 1);

    int day = Math.round((dayValue * 6f) / 10000f);
    day = Math.clamp(day, 0, DAYS - 1);

    int tx = txCountValue / 2500;
    tx = Math.clamp(tx, 0, TX_BUCKETS - 1);

    return (((binary * HOURS) + hour) * DAYS + day) * TX_BUCKETS + tx;
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
