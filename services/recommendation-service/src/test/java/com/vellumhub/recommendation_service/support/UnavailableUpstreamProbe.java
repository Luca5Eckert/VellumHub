package com.vellumhub.recommendation_service.support;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test-only, process-scoped canary: an unavailable upstream that records actual TCP
 * connection attempts and returns HTTP 503. The loopback port is allocated by the OS.
 * All known upstream URLs in the distributed harness point here; attempts cannot be
 * hidden behind exception handling or HTTP fallback responses.
 */
public final class UnavailableUpstreamProbe {
    private static final AtomicLong ATTEMPTS = new AtomicLong();
    private static final ServerSocket SERVER = start();

    private UnavailableUpstreamProbe() {}

    public static String endpoint() {
        return "http://127.0.0.1:" + SERVER.getLocalPort();
    }

    public static long attempts() {
        return ATTEMPTS.get();
    }

    private static ServerSocket start() {
        try {
            ServerSocket server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
            Thread.startVirtualThread(() -> {
                while (!server.isClosed()) {
                    try {
                        Socket connection = server.accept();
                        ATTEMPTS.incrementAndGet();
                        Thread.startVirtualThread(() -> reject(connection));
                    } catch (IOException exception) {
                        if (!server.isClosed()) {
                            throw new IllegalStateException("AFF-REC-009: upstream probe stopped", exception);
                        }
                    }
                }
            });
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("AFF-REC-009: cannot bind upstream probe", exception);
        }
    }

    private static void reject(Socket connection) {
        try (connection) {
            connection.setSoTimeout(1_000);
            connection.getOutputStream().write(
                    "HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                            .getBytes(StandardCharsets.US_ASCII));
            connection.getOutputStream().flush();
        } catch (IOException ignored) {
            // A client may disconnect immediately; the attempt was still counted.
        }
    }
}
