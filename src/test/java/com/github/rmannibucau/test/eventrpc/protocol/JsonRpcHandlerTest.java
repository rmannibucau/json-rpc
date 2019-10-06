package com.github.rmannibucau.test.eventrpc.protocol;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.StringReader;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.bind.Jsonb;

import com.github.rmannibucau.eventrpc.annotations.JsonRpcException;
import com.github.rmannibucau.eventrpc.annotations.JsonRpcMethod;
import com.github.rmannibucau.eventrpc.annotations.JsonRpcParam;
import com.github.rmannibucau.eventrpc.protocol.JsonRpcHandler;
import com.github.rmannibucau.eventrpc.qualifier.JsonRpc;

import org.apache.openwebbeans.junit5.Cdi;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Cdi(packages = JsonRpcHandlerTest.class, disableDiscovery = true)
class JsonRpcHandlerTest {
    @Inject
    private JsonRpcHandler handler;

    @ParameterizedTest
    @CsvSource({
            "'{\"jsonrpc\":\"2.0\",\"method\":\"test1\"}'," +
                    "'200\n{\"jsonrpc\":\"2.0\",\"result\":\"done_1\"}'",
            "'{\"jsonrpc\":\"2.0\",\"method\":\"test2\",\"params\":[\"first\",222]}'," +
                    "'200\n{\"jsonrpc\":\"2.0\",\"result\":\">first,222<\"}'",
            "'{\"jsonrpc\":\"2.0\",\"method\":\"test2\",\"params\":{\"e1\":\"first\",\"e2\":222}}'," +
                    "'200\n{\"jsonrpc\":\"2.0\",\"result\":\">first,222<\"}'",
            "'{\"jsonrpc\":\"2.0\",\"method\":\"test3\",\"params\":[{\"data1\":\"first\",\"data2\":222}]}'," +
                    "'200\n{\"jsonrpc\":\"2.0\",\"result\":\">>first,222<<\"}'",
            "'{\"jsonrpc\":\"2.0\",\"method\":\"test4\"}'," +
                    "'200\n{\"jsonrpc\":\"2.0\",\"result\":{\"data1\":\"set1\",\"data2\":1234}}'",
            "'{\"jsonrpc\":\"2.0\",\"method\":\"test5\"}'," +
                    "'200\n{\"jsonrpc\":\"2.0\",\"result\":{\"data1\":\"set1\",\"data2\":1234}}'",
            "'{\"jsonrpc\":\"2.0\",\"method\":\"test6\"}'," +
                    "'200\n{\"jsonrpc\":\"2.0\",\"error\":{\"code\":2,\"message\":\"Bad argument...even if there is no param here\"}}'",
            "'{\"jsonrpc\":\"2.0\",\"method\":\"test7\"}'," +
                    "'200\n{\"jsonrpc\":\"2.0\",\"error\":{\"code\":2,\"message\":\"Bad argument...even if there is no param here\"}}'"
    })
    void handle(final String input, final String output) {
        final ResponseHandler responseHandler = new ResponseHandler();
        try (final StringReader reader = new StringReader(input)) {
            handler.handle(reader, responseHandler, Optional::empty);
        }
        assertEquals(output, responseHandler.getResult());
    }

    @ApplicationScoped
    public static class MyRpcEndpoints {
        @Inject
        @JsonRpc
        private Jsonb jsonb;

        @JsonRpcMethod("test1")
        public String test1() {
            assertNotNull(jsonb, "rpc endpoints are CDI beans so injections should be set");
            return "done_1";
        }

        @JsonRpcMethod("test2")
        public String simpleParams(@JsonRpcParam("e1") final String p1,
                                   @JsonRpcParam("e2") final int v2) {
            return ">" + p1 + "," + v2 + "<";
        }

        @JsonRpcMethod("test3")
        public String objectParam(final SomeModel input) {
            return ">>" + input.data1 + "," + input.data2 + "<<";
        }

        @JsonRpcMethod("test4")
        public SomeModel returnObject() {
            final SomeModel model = new SomeModel();
            model.data1 = "set1";
            model.data2 = 1234;
            return model;
        }

        @JsonRpcMethod("test5")
        public CompletionStage<SomeModel> returnObjectStage() {
            final SomeModel model = new SomeModel();
            model.data1 = "set1";
            model.data2 = 1234;
            return completedFuture(model);
        }

        @JsonRpcMethod("test6")
        @JsonRpcException(handled = IllegalArgumentException.class, code = 2)
        public SomeModel exception() {
            throw new IllegalArgumentException("Bad argument...even if there is no param here");
        }

        @JsonRpcMethod("test7")
        @JsonRpcException(handled = IllegalArgumentException.class, code = 2)
        public CompletionStage<String> exceptionInAnotherThread() {
            final CompletableFuture<String> promise = new CompletableFuture<>();
            new Thread(() -> promise.completeExceptionally(new IllegalArgumentException("Bad argument...even if there is no param here"))).start();
            return promise;
        }
    }

    public static class SomeModel {
        public String data1;
        public int data2;
    }
}
