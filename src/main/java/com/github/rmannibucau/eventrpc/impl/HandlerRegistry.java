package com.github.rmannibucau.eventrpc.impl;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.enterprise.inject.Vetoed;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonStructure;
import javax.json.JsonValue;
import javax.json.bind.Jsonb;
import javax.json.spi.JsonProvider;

import com.github.rmannibucau.eventrpc.annotations.JsonRpcMethod;
import com.github.rmannibucau.eventrpc.annotations.JsonRpcParam;
import com.github.rmannibucau.eventrpc.protocol.JsonRpcException;

@Vetoed
public class HandlerRegistry {
    private static final Object[] EMPTY_ARGS = new Object[0];

    private final Map<String, Function<JsonStructure, CompletionStage<JsonValue>>> handlers = new HashMap<>();

    private Jsonb jsonb;
    private JsonProvider jsonProvider;

    public Map<String, Function<JsonStructure, CompletionStage<JsonValue>>> getHandlers() {
        return handlers;
    }

    public void registerMethod(final Object bean, final Method method,
                               final JsonRpcMethod config, final JsonRpcParam[] params,
                               final com.github.rmannibucau.eventrpc.annotations.JsonRpcException[] exceptions) {
        if (!method.isAccessible()) {
            method.setAccessible(true);
        }

        final Function<JsonObject, Object[]> objectToArgs = mapObjectParams(method, params);
        final Function<JsonArray, Object[]> arrayToArgs = mapArrayParams(method, params);
        final Map<Class<? extends Throwable>, Integer> handledEx = Stream.of(exceptions)
                .flatMap(ex -> Stream.of(ex.handled()).map(e -> new AbstractMap.SimpleEntry<>(e, ex.code())))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
        final Function<Throwable, JsonRpcException> exceptionMapper = exception -> handleException(handledEx, exception);
        final boolean completionStage = CompletionStage.class.isAssignableFrom(method.getReturnType());
        final Function<JsonStructure, Object> invoke = parameters ->
                doInvoke(bean, method, objectToArgs, arrayToArgs, exceptionMapper, parameters);
        final Function<Object, JsonValue> resultMapper = createResultMapper(
                completionStage ?
                    ParameterizedType.class.cast(method.getGenericReturnType()).getActualTypeArguments()[0] :
                    method.getGenericReturnType());
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
        final String methodId = of(config.value())
                .filter(it -> !it.isEmpty())
                .orElse(method.getDeclaringClass().getName() + "." + method.getName());
        handlers.put(methodId, handler);
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

    private Object doInvoke(final Object bean, final Method method,
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
            return method.invoke(bean, args);
        } catch (final IllegalAccessException e) {
            throw new JsonRpcException(-32601, "Method can't be called", e);
        } catch (final InvocationTargetException e) {
            throw exceptionMapper.apply(e.getTargetException());
        }
    }

    private Function<JsonArray, Object[]> mapArrayParams(final Method method, final JsonRpcParam[] params) {
        final Collection<Function<JsonArray, Object>> mappers = new ArrayList<>(params.length);
        final Parameter[] parameters = method.getParameters();
        for (int i = 0; i < params.length; i++) {
            final boolean required = params[i] != null && params[i].required();
            final int index = params[i] == null || params[i].position() < 0 ? i : params[i].position();

            final Type expectedType = parameters[i].getParameterizedType();
            final boolean optional = isOptional(expectedType);

            final Function<JsonArray, JsonValue> jsExtractor = request -> request.get(index);
            final Function<JsonArray, JsonValue> validatedExtractor = !optional && required ?
                request -> {
                    final JsonValue applied = jsExtractor.apply(request);
                    if (applied == null) {
                        throw new JsonRpcException(-32601, "Missing #" + index + " parameter.");
                    }
                    return applied;
                } :
                jsExtractor;
            final Function<JsonArray, Object> finalMapper = request ->
                    mapToType(expectedType, optional, validatedExtractor.apply(request));

            mappers.add(finalMapper);
        }
        return optimize(mappers);
    }

    private Function<JsonObject, Object[]> mapObjectParams(final Method method, final JsonRpcParam[] params) {
        final Collection<Function<JsonObject, Object>> mappers = new ArrayList<>(params.length);
        final Parameter[] parameters = method.getParameters();
        for (int i = 0; i < params.length; i++) {
            final boolean required = params[i] != null && params[i].required();
            final String name = params[i] == null || params[i].value().isEmpty() ?
                    parameters[i].getName() : params[i].value();

            final Type expectedType = parameters[i].getParameterizedType();
            final boolean optional = isOptional(expectedType);

            final Function<JsonObject, JsonValue> jsExtractor = request -> request.get(name);
            final Function<JsonObject, JsonValue> validatedExtractor = !optional && required ?
                request -> {
                    final JsonValue applied = jsExtractor.apply(request);
                    if (applied == null) {
                        throw new JsonRpcException(-32601, "Missing '" + name + "' parameter.");
                    }
                    return applied;
                } :
                jsExtractor;
            final Function<JsonObject, Object> finalMapper = request ->
                    mapToType(expectedType, optional, validatedExtractor.apply(request));

            mappers.add(finalMapper);
        }
        return optimize(mappers);
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

    private boolean isOptional(final Type expectedType) {
        final boolean optional;
        if (ParameterizedType.class.isInstance(expectedType)) {
            optional = ParameterizedType.class.cast(expectedType).getRawType() == Optional.class;
        } else {
            optional = false;
        }
        return optional;
    }

    public void setJsonb(final Jsonb jsonb) {
        this.jsonb = jsonb;
    }

    public void setJsonProvider(final JsonProvider jsonProvider) {
        this.jsonProvider = jsonProvider;
    }
}
