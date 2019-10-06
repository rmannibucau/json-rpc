package com.github.rmannibucau.eventrpc.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target(METHOD)
@Retention(RUNTIME)
@Repeatable(JsonRpcException.List.class)
public @interface JsonRpcException {
    Class<? extends Throwable>[] handled() default {};

    /**
     * @return jsonrpc code to return.
     */
    int code();

    @Target(METHOD)
    @Retention(RUNTIME)
    @interface List {
        /**
         * IMPORTANT: for now, overlapping is not handled, can be done in a later version (see jbatch for ex.).
         *
         * @return list of exception mapping.
         */
        JsonRpcException[] value();
    }
}
