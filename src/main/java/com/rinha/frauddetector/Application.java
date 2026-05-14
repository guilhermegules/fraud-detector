package com.rinha.frauddetector;

import com.rinha.frauddetector.app.Bootstrap;
import com.rinha.frauddetector.app.Server;

public final class Application {

    static void main(String[] args) throws Exception {
        var bootstrap = new Bootstrap();
        var server = new Server(bootstrap.fraudDetectionService());
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    }
}
