package com.rinha.frauddetector.app;

import com.rinha.frauddetector.http.JsonCodec;

class ReadyHandler implements Handler {

    @Override
    public Result handle(byte[] requestBody) {
        return new Result(Server.STATUS_200, JsonCodec.readyResponse(), true);
    }
}
