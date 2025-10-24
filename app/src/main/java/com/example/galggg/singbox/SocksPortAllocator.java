package com.example.galggg.singbox;

import java.io.IOException;
import java.net.ServerSocket;

public final class SocksPortAllocator {
    private SocksPortAllocator() {}

    public static int pick() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        }
    }
}
