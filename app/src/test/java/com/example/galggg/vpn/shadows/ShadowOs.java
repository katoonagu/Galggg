package com.example.galggg.vpn.shadows;

import android.system.Os;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@Implements(value = Os.class, callThroughByDefault = true)
public final class ShadowOs {

    private ShadowOs() {
    }

    @Implementation
    protected static void chmod(String path, int mode) {
        // no-op for host unit tests; real behavior covered on device
    }
}
