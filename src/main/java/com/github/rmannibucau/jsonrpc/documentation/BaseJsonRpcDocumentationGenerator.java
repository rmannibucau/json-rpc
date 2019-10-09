package com.github.rmannibucau.jsonrpc.documentation;

import static java.beans.Introspector.decapitalize;
import static java.util.Arrays.asList;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import javax.json.bind.annotation.JsonbProperty;

import com.github.rmannibucau.jsonrpc.annotations.JsonRpcException;
import com.github.rmannibucau.jsonrpc.annotations.JsonRpcMethod;
import com.github.rmannibucau.jsonrpc.annotations.JsonRpcParam;
import com.github.rmannibucau.jsonrpc.impl.Registration;

public abstract class BaseJsonRpcDocumentationGenerator implements Runnable {
    private final Collection<Class<?>> endpoints;
    private final PrintStream output;

    public BaseJsonRpcDocumentationGenerator(final Collection<Class<?>> endpoints,
                                             final PrintStream output) {
        this.endpoints = endpoints;
        this.output = output;
    }

    @Override
    public void run() {
        doRun(forRegistrations(), output);
    }

    protected String toString(final Registration registration) {
        return registration.toString();
    }

    protected String asString(final Type type) {
        return type.getTypeName().replace("java.lang.", "");
    }

    protected void doRun(final Stream<Registration> forRegistrations, final PrintStream output) {
        output.println(forRegistrations.map(this::toString).sorted().collect(joining("\n")));
    }

    protected Map<String, Type> prepareSchema(final Class<?> clazz) {
        final Map<String, Type> fields = new HashMap<>();
        Stream.of(clazz.getMethods())
            .filter(it -> (it.getName().startsWith("get") || it.getName().startsWith("is")) && it.getName().length() > 2)
            .filter(it -> it.getDeclaringClass() != Object.class)
            .forEach(m -> {
                final String key = of(decapitalize(m.getName().startsWith("get") ?
                            m.getName().substring(3) : m.getName().substring(2)))
                    .map(name -> ofNullable(m.getAnnotation(JsonbProperty.class))
                        .map(JsonbProperty::value)
                        .orElseGet(() -> {
                            try {
                                return ofNullable(clazz.getDeclaredField(name))
                                        .map(f -> f.getAnnotation(JsonbProperty.class))
                                        .map(JsonbProperty::value)
                                        .orElse(name);
                            } catch (final NoSuchFieldException e) {
                                return name;
                            }
                        }))
                    .orElseThrow(IllegalStateException::new);
                fields.putIfAbsent(key, m.getGenericReturnType());
            });
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            Stream.of(current.getDeclaredFields())
                .filter(it -> Modifier.isPublic(it.getModifiers()))
                .forEach(field -> fields.putIfAbsent(
                    ofNullable(field.getAnnotation(JsonbProperty.class)).map(JsonbProperty::value).orElseGet(field::getName),
                    field.getGenericType()));
            current = current.getSuperclass();
        }
        return fields;
    }

    private Stream<Registration> forRegistrations() {
        return endpoints.stream().flatMap(this::toRegistration);
    }

    private Stream<Registration> toRegistration(final Class<?> aClass) {
        return Stream.of(aClass.getMethods())
            .filter(it -> it.isAnnotationPresent(JsonRpcMethod.class))
            .map(method -> {
                final AtomicInteger idx = new AtomicInteger(-1);
                final Optional<JsonRpcMethod> config = of(method.getAnnotation(JsonRpcMethod.class));
                return new Registration(
                    config.map(JsonRpcMethod::value).filter(m -> !m.isEmpty())
                        .orElse(method.getDeclaringClass().getName() + "." + method.getName()),
                        extractRealType(method.getGenericReturnType()),
                    a -> null,
                    Stream.of(method.getParameters())
                        .map(p -> {
                            final Optional<JsonRpcParam> conf = ofNullable(p.getAnnotation(JsonRpcParam.class));
                            idx.incrementAndGet();
                            return new Registration.Parameter(
                                extractRealType(p.getParameterizedType()),
                                conf.map(JsonRpcParam::value).filter(it -> !it.isEmpty()).orElseGet(p::getName),
                                conf.map(JsonRpcParam::position).filter(it -> it >= 0).orElseGet(idx::get),
                                conf.map(JsonRpcParam::required).orElse(false),
                                conf.map(JsonRpcParam::documentation).orElse(""));
                        })
                        .collect(toList()),
                    ofNullable(method.getAnnotationsByType(JsonRpcException.class))
                        .map(ex -> Stream.of(ex)
                                .map(e -> new Registration.ExceptionMapping(asList(e.handled()), e.code(), e.documentation()))
                                .collect(toList()))
                        .orElseGet(Collections::emptyList),
                    config.map(JsonRpcMethod::documentation).orElse(""));
            });
    }

    private Type extractRealType(final Type type) {
        if (ParameterizedType.class.isInstance(type)) {
            final ParameterizedType pt = ParameterizedType.class.cast(type);
            return Stream.of(Optional.class, CompletionStage.class, CompletableFuture.class)
                    .anyMatch(gt -> pt.getRawType() == gt) ? pt.getActualTypeArguments()[0] : pt;
        }
        return type;
    }

    public static PrintStream toOutputStream(final String arg) throws IOException {
        switch (ofNullable(arg).orElse("stdout")) {
            case "stdout":
                return new PrintStream(System.out) {
                    @Override
                    public void close() {
                        flush();
                    }
                };
            case "stderr":
                return new PrintStream(System.err) {
                    @Override
                    public void close() {
                        flush();
                    }
                };
            default:
                final Path path = Paths.get(arg);
                if (!Files.exists(path.getParent())) {
                    Files.createDirectories(path.getParent());
                }
                return new PrintStream(Files.newOutputStream(path));
        }
    }

    public static List<Class<?>> mapClasses(final String arg) {
        return Stream.of(arg.split(","))
                .map(String::trim)
                .filter(it -> !it.isEmpty())
                .map(clazz -> {
                    try {
                        return Thread.currentThread().getContextClassLoader().loadClass(clazz);
                    } catch (final ClassNotFoundException e) {
                        throw new IllegalArgumentException(e);
                    }
                })
                .collect(toList());
    }
}
