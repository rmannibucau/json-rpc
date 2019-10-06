package com.github.rmannibucau.jsonrpc.protocol;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.io.Reader;
import java.io.Writer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonStructure;
import javax.json.JsonValue;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbException;

import com.github.rmannibucau.jsonrpc.configuration.Configuration;
import com.github.rmannibucau.jsonrpc.impl.HandlerRegistry;
import com.github.rmannibucau.jsonrpc.qualifier.JsonRpc;

@ApplicationScoped
public class JsonRpcHandler {
    @Inject
    private Configuration configuration;

    @Inject
    @JsonRpc
    private Jsonb jsonb;

    @Inject
    private HandlerRegistry registry;

    public void handle(final Reader reader,
                       final BiConsumer<Integer, Consumer<Writer>> responseHandler,
                       final Supplier<Optional<Runnable>> asyncHandler) {
        final JsonStructure request;
        try {
            request = readRequest(reader);
        } catch (final JsonbException jsonbEx) {
            sendResponse(responseHandler, createResponse(-32700, jsonbEx.getMessage()));
            return;
        }

        final Optional<Runnable> asyncCallback = asyncHandler.get();
        final CompletionStage<?> promise = prepareResultChain(request).handle((value, error) -> {
            try {
                if (value != null) {
                    sendResponse(responseHandler, value);
                } else {
                    sendResponse(responseHandler, createResponse(-32603, error.getMessage()));
                }
            } finally {
                asyncCallback.ifPresent(Runnable::run);
            }
            return value;
        });
        if (!asyncCallback.isPresent()) { // async is not possible, force sync call
            try {
                promise.toCompletableFuture().get(configuration.getTimeout(), MILLISECONDS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (final ExecutionException e) {
                throw new IllegalStateException(e.getCause());
            } catch (final TimeoutException e) {
                sendResponse(responseHandler, createResponse(-32603, "Execution timed-out"));
                asyncCallback.ifPresent(Runnable::run);
            }
        }
    }

    public CompletionStage<Response> handleRequest(final JsonObject request) {
        return doValidate(request)
            .map(CompletableFuture::completedFuture)
            .orElseGet(() -> {
                final Function<JsonStructure, CompletionStage<JsonValue>> fn = registry.getHandlers().get(request.getString("method"));
                final String id = ofNullable(request.getJsonString("id")).map(JsonString::getString).orElse(null);
                final JsonStructure params = ofNullable(request.get("params")).map(JsonStructure.class::cast).orElse(null);

                final Response response = newResponse(id);
                try {
                    return fn.apply(params).handle((result, error) -> {
                        if (error != null) {
                            onError(response,
                                CompletionException.class.isInstance(error) && error.getCause() != null ?
                                    error.getCause() : error);
                        } else {
                            response.setResult(result);
                        }
                        return response;
                    })
                    .toCompletableFuture();
                } catch (final RuntimeException re) {
                    onError(response, re);
                    return completedFuture(response);
                }
            });
    }

    public void setConfiguration(final Configuration configuration) {
        this.configuration = configuration;
    }

    public void setJsonb(final Jsonb jsonb) { // to use without cdi
        this.jsonb = jsonb;
    }

    public void setRegistry(final HandlerRegistry registry) { // to use without cdi
        this.registry = registry;
    }

    private Response newResponse(String id) {
        final Response response = new Response();
        response.setJsonrpc(configuration.getJsonRpcVersion());
        response.setId(id);
        return response;
    }

    private void onError(final Response response, final Throwable re) {
        final Response.ErrorResponse errorResponse = new Response.ErrorResponse();

        if (JsonRpcException.class.isInstance(re)) {
            JsonRpcException jsonRpcException = JsonRpcException.class.cast(re);
            errorResponse.setCode(jsonRpcException.getCode());
            errorResponse.setData(jsonRpcException.getData());
        } else {
            errorResponse.setCode(-32099);
        }
        errorResponse.setMessage(re.getMessage());

        response.setError(errorResponse);
    }

    private Optional<Response> doValidate(final JsonObject request) {
        final Pair<String, Response> pair = ensurePresent(request, "jsonrpc", -32600);
        if (pair.second != null) {
            return of(pair.second);
        }
        if (!configuration.getJsonRpcVersion().equals(pair.first)) {
            return of(createResponse(-32600, "invalid jsonrpc version"));
        }
        final Pair<String, Response> method = ensurePresent(request, "method", -32601);
        if (method.second != null) {
            return of(method.second);
        }
        if (!registry.getHandlers().containsKey(method.first)) {
            return of(createResponse(-32601, "Unknown method"));
        }
        return empty();
    }

    private Pair<String, Response> ensurePresent(final JsonObject request, final String key, final int code) {
        final JsonString methodJson = request.getJsonString(key);
        if (methodJson == null) {
            return new Pair<>(null, createResponse(code, "Missing " + key));
        }

        final String method = methodJson.getString();
        if (method.isEmpty()) {
            return new Pair<>(null, createResponse(code, "Empty " + key));
        }

        return new Pair<>(method, null);
    }

    public Response createResponse(final int code, final String message) {
        final Response.ErrorResponse errorResponse = new Response.ErrorResponse();
        errorResponse.setCode(code);
        errorResponse.setMessage(message);

        final Response response = new Response();
        response.setJsonrpc(configuration.getJsonRpcVersion());
        response.setError(errorResponse);
        return response;
    }

    public CompletionStage<?> prepareResultChain(final JsonStructure request) {
        switch (request.getValueType()) {
            case OBJECT: // single request
                return handleRequest(request.asJsonObject());
            case ARRAY: // batch
                final CompletableFuture<?>[] futures = request.asJsonArray().stream()
                        .map(it -> it.getValueType() == JsonValue.ValueType.OBJECT ?
                                handleRequest(it.asJsonObject()) :
                                completedFuture(createResponse(-32600, "Batch requests must be JSON objects")))
                        .map(CompletionStage::toCompletableFuture)
                        .toArray(CompletableFuture[]::new);
                return CompletableFuture.allOf(futures)
                        .thenApply(ignored -> Stream.of(futures)
                            .map(f -> f.getNow(null))
                            .toArray(Response[]::new));
            default:
                return completedFuture(createResponse(-32600, "Unknown request type: " + request.getValueType()));
        }
    }

    private void sendResponse(final BiConsumer<Integer, Consumer<Writer>> handler, final Object response) {
        handler.accept(200, writer -> jsonb.toJson(response, writer));
    }

    private JsonStructure readRequest(final Reader reader) {
        return jsonb.fromJson(reader, JsonStructure.class);
    }

    private static class Pair<A, B> {
        private final A first;
        private final B second;

        private Pair(final A first, final B second) {
            this.first = first;
            this.second = second;
        }
    }
}
