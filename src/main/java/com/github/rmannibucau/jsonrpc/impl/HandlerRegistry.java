package com.github.rmannibucau.jsonrpc.impl;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.enterprise.inject.Vetoed;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonStructure;
import javax.json.JsonValue;
import javax.json.bind.Jsonb;
import javax.json.spi.JsonProvider;

import com.github.rmannibucau.jsonrpc.annotations.JsonRpcMethod;
import com.github.rmannibucau.jsonrpc.annotations.JsonRpcParam;
import com.github.rmannibucau.jsonrpc.protocol.JsonRpcException;

@Vetoed
public class HandlerRegistry {
    private static final Object[] EMPTY_ARGS = new Object[0];

    private final Map<String, Function<JsonStructure, CompletionStage<JsonValue>>> handlers = new ConcurrentHashMap<>();

    private Jsonb jsonb;
    private JsonProvider jsonProvider;

    public Map<String, Function<JsonStructure, CompletionStage<JsonValue>>> getHandlers() {
        return handlers;
    }

    public Unregisterable registerMethod(final Registration registration) {
        final Function<JsonObject, Object[]> objectToArgs = mapObjectParams(registration.getParameters());
        final Function<JsonArray, Object[]> arrayToArgs = mapArrayParams(registration.getParameters());
        final Map<Class<? extends Throwable>, Integer> handledEx =
                ofNullable(registration.getExceptionMappings()).map(Collection::stream).orElseGet(Stream::empty)
                .flatMap(ex -> ofNullable(ex.getTypes())
                        .map(Collection::stream).orElseGet(Stream::empty)
                        .map(e -> new AbstractMap.SimpleEntry<>(e, ex.getCode())))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
        final Function<Throwable, JsonRpcException> exceptionMapper = exception -> handleException(handledEx, exception);
        final boolean completionStage = isCompletionStage(registration.getReturnedType());
        final Function<JsonStructure, Object> invoke = parameters ->
                doInvoke(registration.getInvoker(), objectToArgs, arrayToArgs, exceptionMapper, parameters);
        final Function<Object, JsonValue> resultMapper = createResultMapper(completionStage ?
                ParameterizedType.class.cast(registration.getReturnedType()).getActualTypeArguments()[0] :
                registration.getReturnedType());
        final Function<JsonStructure, CompletionStage<JsonValue>> handler = !completionStage ?
                invoke
                        .andThen(resultMapper)
                        .andThen(CompletableFuture::completedFuture) :
                invoke.andThen(stage -> ((CompletionStage<?>) stage)
                        .handle((result, error) -> {
                            if (error == null) {
                                return resultMapper.apply(result);
                            }
                            throw exceptionMapper.apply(
                                    CompletionException.class.isInstance(error) && error.getCause() != null ? error.getCause() : error);
                        }));
        final String jsonRpcMethod = registration.getJsonRpcMethod();
        if (handlers.putIfAbsent(jsonRpcMethod, handler) != null) {
            throw new IllegalArgumentException("Ambiguous method: '" + jsonRpcMethod + "'");
        }
        return () -> handlers.remove(jsonRpcMethod);
    }

    public Unregisterable registerMethodReflect(final Object bean, final Method method,
                                                final JsonRpcMethod config, final JsonRpcParam[] params,
                                                final com.github.rmannibucau.jsonrpc.annotations.JsonRpcException[] exceptions) {
        if (!method.isAccessible()) {
            method.setAccessible(true);
        }
        final Parameter[] types = method.getParameters();
        final AtomicInteger paramIdx = new AtomicInteger(0);
        final AtomicInteger exceptionIdx = new AtomicInteger(0);
        return registerMethod(new Registration(
            of(config.value())
                    .filter(it -> !it.isEmpty())
                    .orElse(method.getDeclaringClass().getName() + "." + method.getName()),
            method.getGenericReturnType(),
            args -> {
                try {
                    return method.invoke(bean, args);
                } catch (final IllegalAccessException e) {
                    throw new JsonRpcException(-32601, "Method can't be called", e);
                } catch (final InvocationTargetException ite) {
                    final Throwable targetException = ite.getTargetException();
                    if (JsonRpcException.class.isInstance(targetException)) {
                        throw JsonRpcException.class.cast(targetException);
                    }
                    if (RuntimeException.class.isInstance(targetException)) {
                        throw RuntimeException.class.cast(targetException);
                    }
                    throw new IllegalStateException(targetException);
                }
            },
            Stream.of(params)
                .map(p -> {
                    final int idx = paramIdx.getAndIncrement();
                    final Optional<JsonRpcParam> param = ofNullable(params[idx]);
                    return new Registration.Parameter(
                            types[idx].getParameterizedType(),
                            param.map(JsonRpcParam::value).filter(it -> !it.isEmpty()).orElseGet(types[idx]::getName),
                            param.map(JsonRpcParam::position).filter(it -> it >= 0).orElse(idx),
                            param.map(JsonRpcParam::required).orElse(false),
                            param.map(JsonRpcParam::documentation).orElse(""));
                })
                .collect(toList()),
            Stream.of(exceptions)
                .map(e -> {
                    final int idx = exceptionIdx.getAndIncrement();
                    final Optional<com.github.rmannibucau.jsonrpc.annotations.JsonRpcException> param = ofNullable(exceptions[idx]);
                    return new Registration.ExceptionMapping(
                            param.map(com.github.rmannibucau.jsonrpc.annotations.JsonRpcException::handled)
                                .map(Stream::of)
                                .orElseGet(Stream::empty)
                                .collect(toList()),
                            e.code());
                })
                .collect(toList()), config.documentation()));
    }

    private JsonRpcException handleException(final Map<Class<? extends Throwable>, Integer> handledEx, final Throwable exception) {
        return JsonRpcException.class.isInstance(exception) ?
                JsonRpcException.class.cast(exception) :
                handledEx.entrySet().stream()
                    .filter(handled -> handled.getKey().isAssignableFrom(exception.getClass()))
                    .findFirst()
                    .map(handled -> new JsonRpcException(handled.getValue(), exception.getMessage(), exception))
                    .orElseGet(() -> new JsonRpcException(-32603, exception.getMessage(), exception));
    }

    private Function<Object, JsonValue> createResultMapper(final Type genericReturnType) {
        if (ParameterizedType.class.isInstance(genericReturnType) &&
                ParameterizedType.class.cast(genericReturnType).getRawType() == Optional.class) {
            final Function<Object, JsonValue> nestedMapper = createResultMapper(
                    ParameterizedType.class.cast(genericReturnType).getActualTypeArguments()[0]);
            return v -> v == null || !Optional.class.cast(v).isPresent() ? null : nestedMapper.apply(Optional.class.cast(v).get());
        }
        if (Class.class.isInstance(genericReturnType)) {
            final Class<?> clazz = Class.class.cast(genericReturnType);
            if (CharSequence.class.isAssignableFrom(clazz)) {
                return v -> v == null ? null : jsonProvider.createValue(String.valueOf(v));
            }
            if (Integer.class.isAssignableFrom(clazz)) {
                return v -> v == null ? null : jsonProvider.createValue(Integer.class.cast(v));
            }
            if (Double.class.isAssignableFrom(clazz)) {
                return v -> v == null ? null : jsonProvider.createValue(Double.class.cast(v));
            }
        }

        return v -> v == null ? null : jsonb.fromJson(jsonb.toJson(v), JsonValue.class);
    }

    private Object doInvoke(final Function<Object[], Object> invoker,
                            final Function<JsonObject, Object[]> objectToArgs,
                            final Function<JsonArray, Object[]> arrayToArgs,
                            final Function<Throwable, JsonRpcException> exceptionMapper,
                            final JsonStructure parameters) {
        final Object[] args = ofNullable(parameters)
                .map(p -> {
                    switch (p.getValueType()) {
                        case OBJECT:
                            return objectToArgs.apply(p.asJsonObject());
                        case ARRAY:
                            return arrayToArgs.apply(p.asJsonArray());
                        default:
                            throw new JsonRpcException(-32601, "Unsupported params type: " + p.getValueType());
                    }
                })
                .orElseGet(() -> arrayToArgs.apply(JsonValue.EMPTY_JSON_ARRAY));
        try {
            return invoker.apply(args);
        } catch (final RuntimeException e) {
            throw exceptionMapper.apply(e);
        }
    }

    private Function<JsonArray, Object[]> mapArrayParams(final Collection<Registration.Parameter> params) {
        if (params == null) {
            return r -> EMPTY_ARGS;
        }
        return optimize(params.stream()
            .map(param -> {
                final boolean optional = isOptional(param.getType());
                final Function<JsonArray, JsonValue> jsExtractor = request -> request.size() > param.getPosition() ?
                        request.get(param.getPosition()) : null;
                final Function<JsonArray, JsonValue> validatedExtractor = !optional && param.isRequired() ?
                        request -> {
                            final JsonValue applied = jsExtractor.apply(request);
                            if (applied == null) {
                                throw new JsonRpcException(-32601, "Missing #" + param.getPosition() + " parameter.");
                            }
                            return applied;
                        } :
                        jsExtractor;
                return (Function<JsonArray, Object>) request ->
                        mapToType(param.getType(), optional, validatedExtractor.apply(request));
            })
            .collect(toList()));
    }

    private Function<JsonObject, Object[]> mapObjectParams(final Collection<Registration.Parameter> params) {
        if (params == null) {
            return r -> EMPTY_ARGS;
        }
        return optimize(params.stream()
            .map(param -> {
                final boolean optional = isOptional(param.getType());

                final Function<JsonObject, JsonValue> jsExtractor = request -> request.get(param.getName());
                final Function<JsonObject, JsonValue> validatedExtractor = !optional && param.isRequired() ?
                        request -> {
                            final JsonValue applied = jsExtractor.apply(request);
                            if (applied == null) {
                                throw new JsonRpcException(-32601, "Missing '" + param.getName() + "' parameter.");
                            }
                            return applied;
                        } :
                        jsExtractor;
                return (Function<JsonObject, Object>) request ->
                        mapToType(param.getType(), optional, validatedExtractor.apply(request));
            })
            .collect(toList()));
    }

    private <A extends JsonStructure> Function<A, Object[]> optimize(Collection<Function<A, Object>> mappers) {
        switch (mappers.size()) {
            case 0:
                return r -> EMPTY_ARGS;
            case 1:
                return mappers.iterator().next().andThen(r -> new Object[] { r });
            default:
                return p -> mappers.stream().map(fn -> fn.apply(p)).toArray(Object[]::new);
        }
    }

    private Object mapToType(final Type expectedType, final boolean optional, final JsonValue apply) {
        return apply == null ? (optional ? empty() : null) : jsonb.fromJson(apply.toString(), expectedType);
    }

    private boolean isCompletionStage(final Type expectedType) {
        if (ParameterizedType.class.isInstance(expectedType)) {
            final Type rawType = ParameterizedType.class.cast(expectedType).getRawType();
            return Class.class.isInstance(rawType) && CompletionStage.class.isAssignableFrom(Class.class.cast(rawType));
        }
        return false;
    }

    private boolean isOptional(final Type expectedType) {
        return ParameterizedType.class.isInstance(expectedType) &&
                ParameterizedType.class.cast(expectedType).getRawType() == Optional.class;
    }

    public void setJsonb(final Jsonb jsonb) {
        this.jsonb = jsonb;
    }

    public void setJsonProvider(final JsonProvider jsonProvider) {
        this.jsonProvider = jsonProvider;
    }

    @FunctionalInterface
    public interface Unregisterable extends AutoCloseable {
        void close();
    }
}
