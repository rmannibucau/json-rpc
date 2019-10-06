package com.github.rmannibucau.test.eventrpc.protocol;

import java.io.Writer;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ResponseHandler implements BiConsumer<Integer, Consumer<Writer>> {
    private final StringBuilder builder = new StringBuilder();

    @Override
    public void accept(final Integer status, final Consumer<Writer> writerConsumer) {
        builder.append(status).append('\n');
        writerConsumer.accept(new Writer() {
            @Override
            public void write(char[] cbuf, int off, int len) {
                builder.append(cbuf, off, len);
            }

            @Override
            public void flush() {
                // no-op
            }

            @Override
            public void close() {
                flush();
            }
        });
    }

    public String getResult() {
        return builder.toString();
    }
}
