package com.galggg.ui.bridge;

import com.galggg.ui.data.InMemoryVpnController;
import com.galggg.ui.data.VpnController;

public interface VpnControllerFactory {
    VpnController create();
}

public final class VpnControllerProvider {
    private static java.util.concurrent.atomic.AtomicReference<java.util.function.Supplier<VpnController>> holder =
            new java.util.concurrent.atomic.AtomicReference<>(() -> new InMemoryVpnController());

    private VpnControllerProvider() {}

    public static VpnController get() {
        return holder.get().get();
    }

    public static void set(VpnControllerFactory factory) {
        holder.set(() -> factory.create());
    }
}
