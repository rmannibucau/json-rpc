package com.github.rmannibucau.eventrpc.servlet;

import static java.util.Optional.empty;
import static java.util.Optional.of;

import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;
import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.github.rmannibucau.eventrpc.protocol.JsonRpcHandler;

// https://www.jsonrpc.org/specification
public class JsonRpcServlet extends HttpServlet {
    @Inject
    protected JsonRpcHandler handler;

    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
        handler.handle(
            req.getReader(),
            (status, writerConsumer) -> {
                resp.setStatus(status);
                try (final Writer writer = resp.getWriter()) {
                    writerConsumer.accept(writer);
                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
            }, () -> {
                if (req.isAsyncSupported()) {
                    final AsyncContext asyncContext = req.startAsync();
                    if (asyncContext != null) {
                        final AtomicBoolean done = new AtomicBoolean(false);
                        return of(() -> {
                            if (done.compareAndSet(false, true)) {
                                asyncContext.complete();
                            }
                        });
                    }
                }
                return empty();
            });
    }

    public void setHandler(final JsonRpcHandler handler) {
        this.handler = handler;
    }
}
