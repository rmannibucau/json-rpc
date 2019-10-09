package com.github.rmannibucau.jsonrpc.impl;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.function.Function;

public class Registration {
    private final String jsonRpcMethod;
    private final String documentation;
    private final Type returnedType;
    private final Function<Object[], Object> invoker;
    private final Collection<Parameter> parameters;
    private final Collection<ExceptionMapping> exceptionMappings;

    public Registration(final String jsonRpcMethod,
                        final Type returnedType, final Function<Object[], Object> invoker,
                        final Collection<Parameter> parameters,
                        final Collection<ExceptionMapping> exceptionMappings,
                        final String documentation) {
        this.jsonRpcMethod = requireNonNull(jsonRpcMethod, "JSON-RPC method can't be null");
        this.returnedType = ofNullable(returnedType).orElse(Object.class);
        this.invoker = requireNonNull(invoker, "invoker can't be null");
        this.parameters = parameters;
        this.exceptionMappings = exceptionMappings;
        this.documentation = documentation;
    }

    public String getDocumentation() {
        return documentation;
    }

    public String getJsonRpcMethod() {
        return jsonRpcMethod;
    }

    public Type getReturnedType() {
        return returnedType;
    }

    public Function<Object[], Object> getInvoker() {
        return invoker;
    }

    public Collection<Parameter> getParameters() {
        return parameters;
    }

    public Collection<ExceptionMapping> getExceptionMappings() {
        return exceptionMappings;
    }

    public static class ExceptionMapping {
        private final Collection<Class<? extends Throwable>> types;
        private final int code;

        public ExceptionMapping(final Collection<Class<? extends Throwable>> types, final int code) {
            this.types = types;
            this.code = code;
        }

        public Collection<Class<? extends Throwable>> getTypes() {
            return types;
        }

        public int getCode() {
            return code;
        }
    }

    public static class Parameter {
        private final Type type;
        private final String name;
        private final int position;
        private final boolean required;
        private final String documentation;

        public Parameter(final Type type, final String name, final int position, final boolean required,
                         final String documentation) {
            this.type = type;
            this.name = name;
            this.position = position;
            this.required = required;
            this.documentation = documentation;
        }

        public String getDocumentation() {
            return documentation;
        }

        public Type getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        public int getPosition() {
            return position;
        }

        public boolean isRequired() {
            return required;
        }
    }
}
