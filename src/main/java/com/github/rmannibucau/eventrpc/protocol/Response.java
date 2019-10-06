package com.github.rmannibucau.eventrpc.protocol;

import javax.json.JsonValue;
import javax.json.bind.annotation.JsonbPropertyOrder;

@JsonbPropertyOrder({"jsonrpc", "id", "result", "error"})
public class Response {
    private String jsonrpc = "2.0";
    private String id;
    private JsonValue result;
    private ErrorResponse error;

    public String getJsonrpc() {
        return jsonrpc;
    }

    public void setJsonrpc(final String jsonrpc) {
        this.jsonrpc = jsonrpc;
    }

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public JsonValue getResult() {
        return result;
    }

    public void setResult(final JsonValue result) {
        this.result = result;
    }

    public ErrorResponse getError() {
        return error;
    }

    public void setError(final ErrorResponse error) {
        this.error = error;
    }

    public static class ErrorResponse {
        private int code;
        private String message;
        private JsonValue data;

        public int getCode() {
            return code;
        }

        public void setCode(final int code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(final String message) {
            this.message = message;
        }

        public JsonValue getData() {
            return data;
        }

        public void setData(final JsonValue data) {
            this.data = data;
        }
    }
}
