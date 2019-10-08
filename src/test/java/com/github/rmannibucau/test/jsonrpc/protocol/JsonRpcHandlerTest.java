package com.github.rmannibucau.test.jsonrpc.protocol;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringReader;
import java.util.Optional;

import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;

import com.github.rmannibucau.jsonrpc.impl.HandlerRegistry;
import com.github.rmannibucau.jsonrpc.impl.Registration;
import com.github.rmannibucau.jsonrpc.protocol.JsonRpcHandler;
import org.apache.openwebbeans.junit5.Cdi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Cdi(classes = MyRpcEndpoints.class, disableDiscovery = true)
class JsonRpcHandlerTest {
    @Inject
    private JsonRpcHandler handler;

    @Inject
    private HandlerRegistry registry;

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
        doHandle(input, output);
    }

    @Test
    void manualRegistration() {
        final HandlerRegistry.Unregisterable custom = registry.registerMethod(new Registration(
                "custom",
                JsonObject.class,
                args -> Json.createObjectBuilder().add("message", "i am here").build(),
                emptyList(),
                emptyList()));
        handle("{\"jsonrpc\":\"2.0\",\"method\":\"custom\"}", "200\n{\"jsonrpc\":\"2.0\",\"result\":{\"message\":\"i am here\"}}");
        custom.close();
    }

    private void doHandle(final String input, final String output) {
        final ResponseHandler responseHandler = new ResponseHandler();
        try (final StringReader reader = new StringReader(input)) {
            handler.handle(reader, responseHandler, Optional::empty);
        }
        assertEquals(output, responseHandler.getResult());
    }
}
