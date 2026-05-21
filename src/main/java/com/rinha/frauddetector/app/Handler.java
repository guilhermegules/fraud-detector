package com.rinha.frauddetector.app;

interface Handler {

    record Result(byte[] statusLine, byte[] body, boolean keepAlive) {}

    Result handle(byte[] requestBody);
}
