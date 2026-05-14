package com.rinha.frauddetector.app;

import com.rinha.frauddetector.application.KnnFraudDetectionService;
import com.rinha.frauddetector.domain.FraudScore;
import com.rinha.frauddetector.dto.FraudRequest;
import com.rinha.frauddetector.http.JsonCodec;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public final class Server {

  private static final Logger LOG = Logger.getLogger(Server.class.getName());

  private final HttpServer server;
  private final KnnFraudDetectionService fraudDetectionService;

  public Server(KnnFraudDetectionService fraudDetectionService) throws IOException {
    this.fraudDetectionService = fraudDetectionService;
    int port = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8080"));
    this.server = HttpServer.create(new InetSocketAddress(port), 512);
    this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    this.server.createContext("/ready", this::handleReady);
    this.server.createContext("/fraud-score", this::handleFraudScore);
  }

  public void start() {
    server.start();
  }

  public void stop() {
    server.stop(0);
  }

  private void handleReady(HttpExchange exchange) throws IOException {
    try (exchange) {
      byte[] response = JsonCodec.readyResponse();
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.length);
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(response);
      }
    }
  }

  private void handleFraudScore(HttpExchange exchange) {
    try (exchange) {
      if (!"POST".equals(exchange.getRequestMethod())) {
        exchange.sendResponseHeaders(405, -1);
        return;
      }
      byte[] body = exchange.getRequestBody().readAllBytes();
      FraudRequest request;
      try {
        request = JsonCodec.parseFraudRequest(body);
      } catch (JsonCodec.JsonParseException e) {
        exchange.sendResponseHeaders(400, -1);
        return;
      }
      if (request.transaction() == null
          || request.transaction().requested_at() == null
          || request.customer() == null
          || request.merchant() == null
          || request.terminal() == null) {
        exchange.sendResponseHeaders(400, -1);
        return;
      }
      FraudScore score = fraudDetectionService.evaluate(request);
      byte[] response = score.responseBytes();
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.length);
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(response);
      }
    } catch (Exception e) {
      LOG.warning("fraud-score error: " + e.getMessage());
      try {
        if (exchange.getResponseCode() == -1) {
          exchange.sendResponseHeaders(500, -1);
        }
      } catch (Exception ignored) {
      }
    }
  }
}
