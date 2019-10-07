package com.github.rmannibucau.jsonrpc.configuration;

import javax.enterprise.inject.Vetoed;

@Vetoed
public class Configuration {
    private String jsonRpcVersion = "2.0";
    private long timeout = 30000L;
    private boolean active = true;

    public boolean isActive() {
        return active;
    }

    public void setActive(final boolean active) {
        this.active = active;
    }

    public String getJsonRpcVersion() {
        return jsonRpcVersion;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setJsonRpcVersion(final String jsonRpcVersion) {
        this.jsonRpcVersion = jsonRpcVersion;
    }

    public void setTimeout(final long timeout) {
        this.timeout = timeout;
    }
}
