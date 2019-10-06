package com.github.rmannibucau.jsonrpc.annotations;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target(PARAMETER)
@Retention(RUNTIME)
public @interface JsonRpcParam {
    /**
     * @return the name if the params structure is an object, default - when empty - to bytecode parameter name.
     */
    String value() default "";

    /**
     * @return the position of the parameter in the param structure if an array, default to the java parameter position.
     */
    int position() default -1;

    /**
     * If the parameter is not an {@link java.util.Optional} it enforces it to not be null before calling the method.
     *
     * @return if required the method will fail if it is missing.
     */
    boolean required() default false;
}
