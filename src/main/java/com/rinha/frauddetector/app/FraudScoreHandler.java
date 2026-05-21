package com.rinha.frauddetector.app;

import com.rinha.frauddetector.application.KnnFraudDetectionService;
import com.rinha.frauddetector.dto.FraudRequest;
import com.rinha.frauddetector.http.JsonCodec;

class FraudScoreHandler implements Handler {

    private final KnnFraudDetectionService fraudDetectionService;

    FraudScoreHandler(KnnFraudDetectionService fraudDetectionService) {
        this.fraudDetectionService = fraudDetectionService;
    }

    @Override
    public Result handle(byte[] requestBody) {
        if (!fraudDetectionService.isReady()) {
            return new Result(Server.STATUS_503, new byte[0], false);
        }
        FraudRequest request;
        try {
            request = JsonCodec.parseFraudRequest(requestBody);
        } catch (JsonCodec.JsonParseException e) {
            return new Result(Server.STATUS_400, new byte[0], false);
        }
        if (request.transaction() == null || request.transaction().requested_at() == null
                || request.customer() == null || request.merchant() == null
                || request.terminal() == null) {
            return new Result(Server.STATUS_400, new byte[0], false);
        }
        var score = fraudDetectionService.evaluate(request);
        return new Result(Server.STATUS_200, score.responseBytes(), true);
    }
}
