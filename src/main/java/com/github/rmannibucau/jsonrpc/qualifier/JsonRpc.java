package com.github.rmannibucau.jsonrpc.qualifier;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Qualifier;

@Qualifier
@Retention(RUNTIME)
@Target({METHOD, TYPE, PARAMETER, FIELD})
public @interface JsonRpc {
    Literal INSTANCE = new Literal();

    class Literal extends AnnotationLiteral<JsonRpc> implements JsonRpc {}
}
