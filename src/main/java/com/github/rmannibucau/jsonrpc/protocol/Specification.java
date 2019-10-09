package com.github.rmannibucau.jsonrpc.protocol;

import java.util.Collection;
import java.util.Map;

public class Specification {
    private Map<String, MethodSpecification> methods;

    public void setMethods(final Map<String, MethodSpecification> methods) {
        this.methods = methods;
    }

    public Map<String, MethodSpecification> getMethods() {
        return methods;
    }

    public static class ExceptionSpecification {
        private int code;
        private String description;

        public int getCode() {
            return code;
        }

        public void setCode(final int code) {
            this.code = code;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(final String description) {
            this.description = description;
        }
    }

    public static class ParameterSpecification {
        private String name;
        private int position;
        private String description;

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }

        public int getPosition() {
            return position;
        }

        public void setPosition(final int position) {
            this.position = position;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(final String description) {
            this.description = description;
        }
    }

    public static class MethodSpecification {
        private String method;
        private String documentation;
        private Collection<ParameterSpecification> parameters;
        private Collection<ExceptionSpecification> exceptions;

        public String getMethod() {
            return method;
        }

        public void setMethod(final String method) {
            this.method = method;
        }

        public String getDocumentation() {
            return documentation;
        }

        public void setDocumentation(final String documentation) {
            this.documentation = documentation;
        }

        public Collection<ParameterSpecification> getParameters() {
            return parameters;
        }

        public void setParameters(final Collection<ParameterSpecification> parameters) {
            this.parameters = parameters;
        }

        public Collection<ExceptionSpecification> getExceptions() {
            return exceptions;
        }

        public void setExceptions(final Collection<ExceptionSpecification> exceptions) {
            this.exceptions = exceptions;
        }
    }
}
