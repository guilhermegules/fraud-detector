package com.rinha.frauddetector.app;

import com.rinha.frauddetector.application.KnnFraudDetectionService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public final class Server implements Runnable {

    private static final Logger LOG = Logger.getLogger(Server.class.getName());

    private static final byte[] CRLFCRLF = "\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

    static final byte[] STATUS_200 = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ".getBytes(StandardCharsets.US_ASCII);
    static final byte[] STATUS_400 = "HTTP/1.1 400 Bad Request\r\nContent-Length: ".getBytes(StandardCharsets.US_ASCII);
    static final byte[] STATUS_503 = "HTTP/1.1 503 Service Unavailable\r\nContent-Length: ".getBytes(StandardCharsets.US_ASCII);

    private static final byte[] STATUS_405 = "HTTP/1.1 405 Method Not Allowed\r\nContent-Length: ".getBytes(StandardCharsets.US_ASCII);

    private static final byte[] TRAILER_CLOSE = "\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] TRAILER_KEEPALIVE = "\r\nConnection: keep-alive\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

    private final Selector selector;
    private final ServerSocketChannel serverChannel;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, Handler> handlers = new HashMap<>();
    private volatile boolean running = true;

    public Server(KnnFraudDetectionService fraudDetectionService) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8080"));
        this.selector = Selector.open();
        this.serverChannel = ServerSocketChannel.open();
        this.serverChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        this.serverChannel.bind(new InetSocketAddress(port), 512);
        this.serverChannel.configureBlocking(false);
        this.serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        handlers.put("GET:/ready", new ReadyHandler());
        handlers.put("POST:/fraud-score", new FraudScoreHandler(fraudDetectionService));
    }

    public void start() {
        Thread t = new Thread(this, "nio-server");
        t.setDaemon(false);
        t.start();
    }

    public void stop() {
        running = false;
        selector.wakeup();
    }

    @Override
    public void run() {
        while (running && selector.isOpen()) {
            try {
                selector.select();
                var keys = selector.selectedKeys();
                var iter = keys.iterator();
                while (iter.hasNext()) {
                    var key = iter.next();
                    iter.remove();
                    if (!key.isValid()) continue;
                    try {
                        if (key.isAcceptable()) doAccept(key);
                        else if (key.isReadable()) doRead(key);
                    } catch (IOException e) {
                        close(key);
                    }
                }
            } catch (IOException e) {
                if (running) LOG.severe("Selector error: " + e.getMessage());
            }
        }
        try { serverChannel.close(); } catch (IOException ignored) {}
        try { selector.close(); } catch (IOException ignored) {}
    }

    private void doAccept(SelectionKey key) throws IOException {
        var server = (ServerSocketChannel) key.channel();
        var client = server.accept();
        client.configureBlocking(false);
        client.setOption(StandardSocketOptions.TCP_NODELAY, true);
        client.register(selector, SelectionKey.OP_READ, new Connection());
    }

    private void doRead(SelectionKey key) throws IOException {
        var ch = (SocketChannel) key.channel();
        var conn = (Connection) key.attachment();

        if (conn.bufLen == conn.buf.length) {
            byte[] nb = new byte[conn.buf.length << 1];
            System.arraycopy(conn.buf, 0, nb, 0, conn.bufLen);
            conn.buf = nb;
        }
        int n = ch.read(ByteBuffer.wrap(conn.buf, conn.bufLen, conn.buf.length - conn.bufLen));
        if (n == -1) { close(key); return; }
        conn.bufLen += n;

        if (!conn.headersParsed) {
            int headerEnd = indexOf(conn.buf, conn.bufLen, CRLFCRLF);
            if (headerEnd == -1) return;

            conn.method = parseToken(conn.buf, 0, scan(conn.buf, 0, headerEnd, (byte) ' '));
            int pathStart = scan(conn.buf, 0, headerEnd, (byte) ' ') + 1;
            while (pathStart < headerEnd && conn.buf[pathStart] == ' ') pathStart++;
            int pathEnd = scan(conn.buf, pathStart, headerEnd, (byte) ' ');
            conn.path = parseToken(conn.buf, pathStart, pathEnd);
            conn.contentLength = parseContentLength(conn.buf, headerEnd);
            conn.headersParsed = true;
            conn.headerEnd = headerEnd + 4;

            if (conn.contentLength > 0) {
                conn.body = new byte[conn.contentLength];
            }
        }

        if (conn.contentLength > 0) {
            int available = conn.bufLen - conn.headerEnd;
            int toCopy = Math.min(conn.contentLength - conn.bodyRead, available);
            if (toCopy > 0) {
                System.arraycopy(conn.buf, conn.headerEnd + conn.bodyRead, conn.body, conn.bodyRead, toCopy);
                conn.bodyRead += toCopy;
            }
        }

        if (conn.contentLength == 0 || conn.bodyRead >= conn.contentLength) {
            key.interestOps(0);
            executor.execute(() -> handle(key, conn, ch));
        }
    }

    private void handle(SelectionKey key, Connection conn, SocketChannel ch) {
        try {
            var handler = handlers.get(conn.method + ":" + conn.path);
            if (handler == null) {
                sendError(key, ch, STATUS_405);
                return;
            }
            var result = handler.handle(conn.body);
            byte[] response = buildResponse(result.statusLine(), result.body(), result.keepAlive());
            writeAll(ch, ByteBuffer.wrap(response));
            if (result.keepAlive()) {
                selector.wakeup();
                synchronized (key) {
                    if (key.isValid()) {
                        conn.reset();
                        key.interestOps(SelectionKey.OP_READ);
                    }
                }
            } else {
                close(key);
            }
        } catch (Exception e) {
            LOG.warning("Handler error: " + e.getMessage());
            close(key);
        }
    }

    private void sendError(SelectionKey key, SocketChannel ch, byte[] statusLine) {
        try {
            writeAll(ch, ByteBuffer.wrap(buildResponse(statusLine, new byte[0], false)));
        } catch (IOException ignored) {}
        close(key);
    }

    private static byte[] buildResponse(byte[] statusLine, byte[] body, boolean keepAlive) {
        byte[] cl = Integer.toString(body.length).getBytes(StandardCharsets.US_ASCII);
        byte[] trailer = keepAlive ? TRAILER_KEEPALIVE : TRAILER_CLOSE;
        byte[] resp = new byte[statusLine.length + cl.length + trailer.length + body.length];
        int pos = 0;
        System.arraycopy(statusLine, 0, resp, pos, statusLine.length); pos += statusLine.length;
        System.arraycopy(cl, 0, resp, pos, cl.length); pos += cl.length;
        System.arraycopy(trailer, 0, resp, pos, trailer.length); pos += trailer.length;
        System.arraycopy(body, 0, resp, pos, body.length);
        return resp;
    }

    private static void writeAll(SocketChannel ch, ByteBuffer buf) throws IOException {
        while (buf.hasRemaining()) {
            ch.write(buf);
        }
    }

    private static void close(SelectionKey key) {
        if (key == null) return;
        key.cancel();
        try {
            var ch = key.channel();
            if (ch != null) ch.close();
        } catch (IOException ignored) {}
    }

    private static int indexOf(byte[] haystack, int length, byte[] needle) {
        if (length < needle.length) return -1;
        outer:
        for (int i = 0; i <= length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static int scan(byte[] data, int from, int to, byte b) {
        for (int i = from; i < to; i++) {
            if (data[i] == b) return i;
        }
        return to;
    }

    private static String parseToken(byte[] data, int from, int to) {
        return new String(data, from, to - from, StandardCharsets.US_ASCII);
    }

    private static int parseContentLength(byte[] data, int headerEnd) {
        int i = 0;
        while (i < headerEnd && data[i] != '\n') i++;
        i++;
        while (i < headerEnd) {
            while (i < headerEnd && (data[i] == '\r' || data[i] == '\n')) i++;
            if (i >= headerEnd) break;
            int nameStart = i;
            while (i < headerEnd && data[i] != ':') i++;
            if (i >= headerEnd) break;
            boolean isContentLength = regionMatches(data, nameStart, i, "content-length");
            do i++;
            while (i < headerEnd && data[i] == ' ');
            int valStart = i;
            while (i < headerEnd && data[i] != '\r') i++;
            if (isContentLength) {
                return Integer.parseInt(parseToken(data, valStart, i));
            }
        }
        return 0;
    }

    private static boolean regionMatches(byte[] data, int from, int to, String s) {
        int len = to - from;
        if (len != s.length()) return false;
        for (int i = 0; i < len; i++) {
            byte a = data[from + i];
            byte b = (byte) s.charAt(i);
            if (a >= 'A' && a <= 'Z') a += 32;
            if (a != b) return false;
        }
        return true;
    }

    private static final class Connection {
        byte[] buf = new byte[4096];
        int bufLen = 0;
        boolean headersParsed = false;
        String method;
        String path;
        int contentLength = 0;
        int headerEnd = 0;
        byte[] body;
        int bodyRead = 0;

        void reset() {
            int consumed = headerEnd + contentLength;
            int leftover = bufLen - consumed;
            if (leftover > 0) {
                System.arraycopy(buf, consumed, buf, 0, leftover);
            }
            bufLen = leftover;
            headersParsed = false;
            method = null;
            path = null;
            contentLength = 0;
            headerEnd = 0;
            body = null;
            bodyRead = 0;
        }
    }
}
