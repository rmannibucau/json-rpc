package com.github.rmannibucau.jsonrpc.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target(METHOD)
@Retention(RUNTIME)
public @interface JsonRpcMethod {
    /**
     * @return JSON-RPC method (identifier).
     */
    String value() default "";

    /**
     * Mainly to auto-document the code and the documentation generator.
     *
     * @return some explanation about this method.
     */
    String documentation() default "";
}
