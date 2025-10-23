package com.example.galggg.provision;

public final class LocalProvision {
    private LocalProvision() {}

    public static ProvisionData get() {
        return new ProvisionData(
                ProvisionConstants.ADDRESS,
                ProvisionConstants.PORT,
                ProvisionConstants.UUID,
                ProvisionConstants.SNI,
                ProvisionConstants.PUBLIC_KEY,
                ProvisionConstants.SHORT_ID,
                ProvisionConstants.FLOW
        );
    }
}

