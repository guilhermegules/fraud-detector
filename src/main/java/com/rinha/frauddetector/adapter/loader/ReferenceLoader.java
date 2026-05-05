package com.rinha.frauddetector.adapter.loader;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinha.frauddetector.domain.FraudReference;
import com.rinha.frauddetector.domain.NormalizationConstants;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

public class ReferenceLoader {

  private final ObjectMapper objectMapper;
  private final JsonFactory jsonFactory;

  private NormalizationConstants normalizationConstants;
  private Map<String, Float> mccRiskMap;
  private FraudReference fraudReference;
  private static final float LEGIT_SAMPLE_RATE = Float.parseFloat(System.getenv().getOrDefault("SAMPLE_RATE", "0.05"));;

  public ReferenceLoader() {
    this.objectMapper = new ObjectMapper();
    this.jsonFactory = objectMapper.getFactory();
  }

  public void loadNormalization() throws IOException {
    try (InputStream is = new ClassPathResource("normalization.json").getInputStream()) {
      normalizationConstants = objectMapper.readValue(is, NormalizationConstants.class);
    }
  }

  public void loadMccRisk() throws IOException {
    try (InputStream is = new ClassPathResource("mcc_risk.json").getInputStream()) {
      mccRiskMap = objectMapper.readValue(is, new TypeReference<>() {});
    }
  }

  public Map<String, Float> getMccRiskMap() {
    return mccRiskMap;
  }

  public void loadReferences(Consumer<ReferenceItem> consumer) throws IOException {
    try (InputStream is = new ClassPathResource("references.json.gz").getInputStream();
         GZIPInputStream gzis = new GZIPInputStream(is);
         JsonParser parser = jsonFactory.createParser(gzis)) {

      Random random = new Random();

      if (parser.nextToken() != JsonToken.START_ARRAY) {
        throw new IllegalStateException("Expected JSON array");
      }

      while (parser.nextToken() == JsonToken.START_OBJECT) {

        float[] vector = null;
        boolean isFraud = false;

        while (parser.nextToken() != JsonToken.END_OBJECT) {
          String field = parser.currentName();
          parser.nextToken();

          if ("vector".equals(field)) {
            vector = readFloatArray(parser);
          } else if ("label".equals(field)) {
            isFraud = "fraud".equals(parser.getValueAsString());
          } else {
            parser.skipChildren();
          }
        }

        if (vector == null) continue;

        if (!isFraud && random.nextFloat() > LEGIT_SAMPLE_RATE) {
          continue;
        }

        consumer.accept(new ReferenceItem(vector, isFraud));
      }
    }
  }
  private float[] readFloatArray(JsonParser parser) throws IOException {
    List<Float> temp = new ArrayList<>(14);

    if (parser.currentToken() != JsonToken.START_ARRAY) {
      throw new IllegalStateException("Expected array");
    }

    while (parser.nextToken() != JsonToken.END_ARRAY) {
      temp.add(parser.getFloatValue());
    }

    float[] arr = new float[temp.size()];
    for (int i = 0; i < temp.size(); i++) {
      arr[i] = temp.get(i);
    }

    return arr;
  }

  public NormalizationConstants getNormalizationConstants() {
    return normalizationConstants;
  }

  public FraudReference getFraudReference() {
    return fraudReference;
  }
}
