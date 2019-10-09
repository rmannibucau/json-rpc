package com.github.rmannibucau.jsonrpc.documentation;

import static java.util.Comparator.comparing;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Stream;

import com.github.rmannibucau.jsonrpc.impl.Registration;

public class AsciidoctorJsonRpcDocumentationGenerator extends BaseJsonRpcDocumentationGenerator {
    private final String title;
    private Collection<Class<?>> schemas;

    public AsciidoctorJsonRpcDocumentationGenerator(final String title,
                                                    final Collection<Class<?>> endpoints,
                                                    final PrintStream output) {
        super(endpoints, output);
        this.title = title;
    }

    @Override
    protected void doRun(final Stream<Registration> forRegistrations, final PrintStream output) {
        if (title != null && !title.isEmpty()) {
            output.println("= " + title + '\n');
        }
        schemas = new HashSet<>();
        try {
            output.println("== JSON-RPC Methods\n");
            super.doRun(forRegistrations, output);
            if (!schemas.isEmpty()) {
                output.println("== Model Schemas\n");
                output.println(schemas.stream()
                        .sorted(comparing(Type::getTypeName))
                        .map(this::toSchema)
                        .collect(joining("\n")) + '\n');
            }
        } finally {
            schemas.clear();
            schemas = null;
        }
    }

    @Override
    protected String asString(final Type type) {
        if (Class.class.isInstance(type)) {
            final Class clazz = Class.class.cast(type);
            if (!clazz.isPrimitive() && !clazz.getName().startsWith("java.")) {
                schemas.add(clazz);
            }
        } else if (ParameterizedType.class.isInstance(type)) { // ensure schema can be registered
            Stream.of(ParameterizedType.class.cast(type).getActualTypeArguments()).forEach(this::asString);
        }
        return super.asString(type);
    }

    @Override
    protected String toString(final Registration registration) {
        return "=== " + registration.getJsonRpcMethod() + "\n\n" +
                ofNullable(registration.getDocumentation())
                        .filter(it -> !it.isEmpty())
                        .map(doc -> doc + "\n\n")
                        .orElse("") +
                (registration.getParameters().isEmpty() ?
                    "" :
                    ("==== Parameters\n\n[cols=\"headers\"]\n|===\n|Name|Position|Type|Required|Documentation\n" +
                        registration.getParameters().stream()
                            .map(p -> '|' + p.getName() + '|' + p.getPosition() + '|' + asString(p.getType()) + '|' + p.isRequired() +
                                '|' + ofNullable(p.getDocumentation()).filter(it -> !it.isEmpty()).orElse("-"))
                            .collect(joining("\n")) + "\n|===\n\n")) +
                "==== Result type\n\n`" + asString(registration.getReturnedType()) + "`\n\n";
    }

    // quick impl just for doc purposes
    private String toSchema(final Class<?> clazz) {
        final Map<String, Type> schema = prepareSchema(clazz);
        return "=== " + clazz.getName() + "\n\n[cols=\"headers\"]\n|===\n|Attribute|Type\n" +
                schema.entrySet().stream()
                        .sorted(comparing(Map.Entry::getKey))
                        .map(it -> '|' + it.getKey() + '|' + asString(it.getValue()))
                        .collect(joining("\n")) + "\n|===\n\n";
    }

    public static void main(final String[] args) {
        if (args.length != 3) {
            throw new IllegalArgumentException("Usage: java -cp ... " +
                    AsciidoctorJsonRpcDocumentationGenerator.class.getName() + " <title> <jsonrpcclasses> <output>");
        }
        try (final PrintStream output = toOutputStream(args[2])) {
            new AsciidoctorJsonRpcDocumentationGenerator(args[0], mapClasses(args[1]), output).run();
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
