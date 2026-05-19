package com.rinha.frauddetector;

import com.rinha.frauddetector.adapter.loader.ReferenceLoader;
import com.rinha.frauddetector.app.Server;
import com.rinha.frauddetector.application.KnnFraudDetectionService;
import com.rinha.frauddetector.http.JsonCodec;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public final class Application {

    static void main(String[] args) throws Exception {
        var loader = new ReferenceLoader();
        loader.loadNormalization(JsonCodec.parseNormalization(readResource("normalization.json")));
        loader.loadMccRisk(JsonCodec.parseMccRisk(readResource("mcc_risk.json")));
        var service = new KnnFraudDetectionService(loader);
        var server = new Server(service);
        server.start();
        System.out.println("ready");
        new Thread(() -> {
            try {
                service.initialize();
                System.out.println("initialized");
            } catch (Exception e) {
                System.err.println("init error: " + e.getMessage());
            }
        }).start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    }

    private static byte[] readResource(String name) throws IOException {
        try (InputStream is = Application.class.getClassLoader().getResourceAsStream(name)) {
            if (is == null) throw new FileNotFoundException("Resource not found: " + name);
            return is.readAllBytes();
        }
    }
}
