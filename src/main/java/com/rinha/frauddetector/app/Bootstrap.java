package com.rinha.frauddetector.app;

import com.rinha.frauddetector.adapter.loader.ReferenceLoader;
import com.rinha.frauddetector.application.KnnFraudDetectionService;
import com.rinha.frauddetector.http.JsonCodec;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public final class Bootstrap {

    private final KnnFraudDetectionService fraudDetectionService;

    public Bootstrap() throws Exception {
        var loader = new ReferenceLoader();
        loader.loadNormalization(JsonCodec.parseNormalization(readResource("normalization.json")));
        loader.loadMccRisk(JsonCodec.parseMccRisk(readResource("mcc_risk.json")));
        loader.loadFraudReference();

        var service = new KnnFraudDetectionService(loader);
        service.initialize();
        this.fraudDetectionService = service;
    }

    private static byte[] readResource(String name) throws IOException {
        try (InputStream is = Bootstrap.class.getClassLoader().getResourceAsStream(name)) {
            if (is == null) throw new FileNotFoundException("Resource not found: " + name);
            return is.readAllBytes();
        }
    }

    public KnnFraudDetectionService fraudDetectionService() {
        return fraudDetectionService;
    }
}
