package com.github.rmannibucau.jsonrpc.configuration;

public final class MicroprofileInitializer {
    private MicroprofileInitializer() {
        // no-op
    }

    public static Configuration load(final Configuration configuration) {
        try {
            final org.eclipse.microprofile.config.Config config = org.eclipse.microprofile.config.ConfigProvider.getConfig();
            config.getOptionalValue("com.github.rmannibucau.jsonrpc.version", String.class)
                    .ifPresent(configuration::setJsonRpcVersion);
            config.getOptionalValue("com.github.rmannibucau.jsonrpc.timeout", Long.class)
                    .ifPresent(configuration::setTimeout);
        } catch (final RuntimeException | Error re) {
            // no-op
        }
        return configuration;
    }
}
