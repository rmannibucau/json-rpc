package com.github.rmannibucau.test.jsonrpc.protocol;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.bind.Jsonb;

import com.github.rmannibucau.jsonrpc.annotations.JsonRpcException;
import com.github.rmannibucau.jsonrpc.annotations.JsonRpcMethod;
import com.github.rmannibucau.jsonrpc.annotations.JsonRpcParam;
import com.github.rmannibucau.jsonrpc.qualifier.JsonRpc;

@ApplicationScoped
public class MyRpcEndpoints {
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

    public static class SomeModel {
        public String data1;
        private int data2;

        public void setData2(final int data2) {
            this.data2 = data2;
        }

        public int getData2() {
            return data2;
        }
    }
}
