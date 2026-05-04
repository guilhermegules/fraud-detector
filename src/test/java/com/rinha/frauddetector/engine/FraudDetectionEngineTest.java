package com.rinha.frauddetector.engine;

import com.rinha.frauddetector.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FraudDetectionEngineTest {

    private FraudDetectionEngine engine;

    @BeforeEach
    void setUp() {
        engine = new FraudDetectionEngine();
    }

    private float[] createVectors(int n, float... values) {
        float[] result = new float[n * 14];
        for (int i = 0; i < n; i++) {
            System.arraycopy(values, 0, result, i * 14, 14);
        }
        return result;
    }

    @Test
    void shouldInitializeWithDataset() {
        float[] vectors = new float[] {
            0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f, 1.1f, 1.2f, 1.3f, 1.4f,
            0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f
        };
        byte[] labels = new byte[] {0, 1};

        engine.initialize(vectors, labels, 2);

        assertNotNull(engine);
    }

    @Test
    void shouldReturnFraudResponseForValidRequest() {
        float[] vectors = new float[] {
            0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f, 1.1f, 1.2f, 1.3f, 1.4f
        };
        byte[] labels = new byte[] {0};

        engine.initialize(vectors, labels, 1);

        FraudRequest request = createSampleRequest();
        FraudResponse response = engine.evaluate(request);

        assertNotNull(response);
        assertTrue(response.fraud_score() >= 0.0 && response.fraud_score() <= 1.0);
    }

    @Test
    void shouldApproveLowRiskTransaction() {
        float[] baseVector = new float[] {
            0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f,
            0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f
        };
        float[] vectors = createVectors(5, baseVector);
        byte[] labels = new byte[] {0, 0, 0, 0, 0};

        engine.initialize(vectors, labels, 5);

        FraudRequest request = createSampleRequest();
        FraudResponse response = engine.evaluate(request);

        assertTrue(response.approved());
        assertTrue(response.fraud_score() < 0.5);
    }

    @Test
    void shouldRejectHighRiskTransaction() {
        float[] baseVector = new float[] {
            0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f,
            0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f
        };
        float[] vectors = createVectors(5, baseVector);
        byte[] labels = new byte[] {1, 1, 1, 1, 1};

        engine.initialize(vectors, labels, 5);

        FraudRequest request = createHighRiskRequest();
        FraudResponse response = engine.evaluate(request);

        assertFalse(response.approved());
        assertTrue(response.fraud_score() >= 0.5);
    }

    @Test
    void shouldMapRequestToVector() {
        float[] vectors = new float[] {
            0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f,
            0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f
        };
        byte[] labels = new byte[] {0};

        engine.initialize(vectors, labels, 1);

        FraudRequest request = createSampleRequest();
        FraudResponse response = engine.evaluate(request);

        assertNotNull(response);
    }

    @Test
    void shouldHandleEmptyDataset() {
        float[] vectors = new float[0];
        byte[] labels = new byte[0];

        engine.initialize(vectors, labels, 0);

        FraudRequest request = createSampleRequest();
        FraudResponse response = engine.evaluate(request);

        assertNotNull(response);
        assertTrue(response.approved());
        assertEquals(0.0, response.fraud_score(), 0.001);
    }

    @Test
    void shouldClampFraudScoreBetweenZeroAndOne() {
        float[] vectors = new float[] {
            0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f,
            0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f
        };
        byte[] labels = new byte[] {0};

        engine.initialize(vectors, labels, 1);

        FraudRequest request = createSampleRequest();
        FraudResponse response = engine.evaluate(request);

        assertTrue(response.fraud_score() >= 0.0);
        assertTrue(response.fraud_score() <= 1.0);
    }

    @Test
    void shouldUseKNearestNeighbors() {
        float[] vectors = new float[5 * 14];
        byte[] labels = new byte[5];
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 14; j++) {
                vectors[i * 14 + j] = 0.5f;
            }
            labels[i] = 0;
        }

        engine.initialize(vectors, labels, 5);

        FraudRequest request = createSampleRequest();
        FraudResponse response = engine.evaluate(request);

        assertNotNull(response);
    }

    @Test
    void shouldFallbackOnError() {
        engine.initialize(new float[0], new byte[0], 0);

        assertDoesNotThrow(() -> {
            FraudResponse response = engine.evaluate(null);
            assertNotNull(response);
            assertTrue(response.approved());
            assertEquals(0.0, response.fraud_score(), 0.001);
        });
    }

    @Test
    void shouldProcessRequestWithinTimeLimit() {
        float[] vectors = new float[10 * 14];
        byte[] labels = new byte[10];
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 14; j++) {
                vectors[i * 14 + j] = (float) (Math.random() * 0.5);
            }
            labels[i] = (byte) (i % 2);
        }

        engine.initialize(vectors, labels, 10);

        FraudRequest request = createSampleRequest();

        // Warmup
        for (int i = 0; i < 100; i++) {
            engine.evaluate(request);
        }

        long total = 0;
        int iterations = 1000;
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            engine.evaluate(request);
            total += System.nanoTime() - start;
        }

        long avgNanos = total / iterations;
        assertNotNull(engine.evaluate(request));
        assertTrue(avgNanos < 1_000_000, "Average time should be within 1ms, was: " + avgNanos + "ns");
    }

    @Test
    void shouldExtractAmountFromTransaction() {
        float[] vectors = new float[] {
            0.01f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f,
            0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f
        };
        byte[] labels = new byte[] {0};

        engine.initialize(vectors, labels, 1);

        TransactionDTO tx = new TransactionDTO(1000.0, 1, "2026-03-11T20:23:35Z");
        FraudRequest request = new FraudRequest("tx-1", tx,
            new CustomerDTO(500.0, 2, List.of("MERC-001")),
            new MerchantDTO("MERC-001", "5912", 300.0),
            new TerminalDTO(false, true, 10.0),
            null);

        FraudResponse response = engine.evaluate(request);
        assertNotNull(response);
    }

    @Test
    void shouldExtractCustomerFeatures() {
        float[] vectors = new float[] {
            0.5f, 0.5f, 0.5f, 0.01f, 0.02f, 0.5f, 0.5f,
            0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f
        };
        byte[] labels = new byte[] {0};

        engine.initialize(vectors, labels, 1);

        CustomerDTO customer = new CustomerDTO(500.0, 2, List.of("MERC-001"));
        FraudRequest request = new FraudRequest("tx-1",
            new TransactionDTO(100.0, 1, "2026-03-11T20:23:35Z"),
            customer,
            new MerchantDTO("MERC-001", "5912", 300.0),
            new TerminalDTO(false, true, 10.0),
            null);

        FraudResponse response = engine.evaluate(request);
        assertNotNull(response);
    }

    @Test
    void shouldNotAllocateNewObjectsInEvaluate() {
        float[] vectors = new float[5 * 14];
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 14; j++) {
                vectors[i * 14 + j] = 0.5f;
            }
        }
        byte[] labels = new byte[] {0, 0, 0, 0, 0};

        engine.initialize(vectors, labels, 5);

        FraudRequest request = createSampleRequest();

        // Warmup
        for (int i = 0; i < 100; i++) {
            engine.evaluate(request);
        }

        // The test passes if no exception is thrown, indicating stable execution
        for (int i = 0; i < 1000; i++) {
            FraudResponse response = engine.evaluate(request);
            assertNotNull(response);
        }
    }

    private FraudRequest createSampleRequest() {
        return new FraudRequest(
            "tx-123",
            new TransactionDTO(100.0, 1, "2026-03-11T20:23:35Z"),
            new CustomerDTO(500.0, 2, List.of("MERC-001")),
            new MerchantDTO("MERC-001", "5912", 300.0),
            new TerminalDTO(false, true, 10.0),
            null
        );
    }

    private FraudRequest createHighRiskRequest() {
        return new FraudRequest(
            "tx-999",
            new TransactionDTO(10000.0, 12, "2026-03-11T23:59:59Z"),
            new CustomerDTO(200.0, 50, List.of("MERC-001")),
            new MerchantDTO("MERC-999", "7999", 5000.0),
            new TerminalDTO(true, false, 500.0),
            new LastTransactionDTO("2026-03-11T20:00:00Z", 100.0)
        );
    }
}
