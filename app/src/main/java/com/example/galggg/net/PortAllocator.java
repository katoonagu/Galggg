package com.example.galggg.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;

public final class PortAllocator {
    private PortAllocator(){}

    /** Return preferred port if free; otherwise pick an ephemeral loopback port. */
    public static int chooseLoopbackPort(int preferred) {
        if (isFree(preferred)) {
            return preferred;
        }
        try (ServerSocket s = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            return s.getLocalPort();
        } catch (IOException e) {
            return preferred; // fallback: let the caller fail loudly
        }
    }

    private static boolean isFree(int port) {
        try (ServerSocket s = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
