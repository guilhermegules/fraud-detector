package com.rinha.frauddetector.adapter.loader;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinha.frauddetector.domain.FraudReference;
import com.rinha.frauddetector.domain.NormalizationConstants;
import com.rinha.frauddetector.domain.TransactionVector;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class ReferenceLoader {

  private final ObjectMapper objectMapper;
  private NormalizationConstants normalizationConstants;
  private Map<String, Double> mccRiskMap;
  private FraudReference fraudReference;

  public ReferenceLoader() {
    this.objectMapper = new ObjectMapper();
  }

  public void loadAll() throws IOException {
    loadNormalization();
    loadMccRisk();
    loadReferences();
  }

  private void loadNormalization() throws IOException {
    try (InputStream is = new ClassPathResource("normalization.json").getInputStream()) {
      normalizationConstants = objectMapper.readValue(is, NormalizationConstants.class);
    }
  }

  private void loadMccRisk() throws IOException {
    try (InputStream is = new ClassPathResource("mcc_risk.json").getInputStream()) {
      mccRiskMap = objectMapper.readValue(is, new TypeReference<Map<String, Double>>() {});
    }
  }

  private void loadReferences() throws IOException {
    try (InputStream is = new ClassPathResource("references.json.gz").getInputStream();
        GZIPInputStream gzis = new GZIPInputStream(is)) {

      List<ReferenceItem> records =
          objectMapper.readValue(gzis, new TypeReference<>() {});

      int size = records.size();
      TransactionVector[] vectors = new TransactionVector[size];
      boolean[] labels = new boolean[size];

      for (int i = 0; i < size; i++) {
        ReferenceItem record = records.get(i);
        float[] floatVector = toFloatArray(record.vector());
        vectors[i] = new TransactionVector(floatVector);
        labels[i] = "fraud".equals(record.label());
      }

      fraudReference = new FraudReference(vectors, labels);
    }
  }

  private float[] toFloatArray(double[] doubleArray) {
    float[] floatArray = new float[doubleArray.length];
    for (int i = 0; i < doubleArray.length; i++) {
      floatArray[i] = (float) doubleArray[i];
    }
    return floatArray;
  }

  public NormalizationConstants getNormalizationConstants() {
    return normalizationConstants;
  }

  public Map<String, Double> getMccRiskMap() {
    return mccRiskMap;
  }

  public FraudReference getFraudReference() {
    return fraudReference;
  }
}
