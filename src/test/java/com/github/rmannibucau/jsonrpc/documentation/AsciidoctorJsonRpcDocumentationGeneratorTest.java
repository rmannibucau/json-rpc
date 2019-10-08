package com.github.rmannibucau.jsonrpc.documentation;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import com.github.rmannibucau.test.jsonrpc.protocol.MyRpcEndpoints;
import org.junit.jupiter.api.Test;

class AsciidoctorJsonRpcDocumentationGeneratorTest {
    @Test
    void asciidoctor() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (final PrintStream stream = new PrintStream(out)) {
            new AsciidoctorJsonRpcDocumentationGenerator("The Title", singletonList(MyRpcEndpoints.class), stream).run();
            stream.flush();
            assertEquals(
                    "= The Title\n" +
                    "\n" +
                    "== JSON-RPC Methods\n" +
                    "\n" +
                    "=== test1\n" +
                    "\n" +
                    "==== Result type\n" +
                    "\n" +
                    "`String`\n" +
                    "\n" +
                    "\n" +
                    "=== test2\n" +
                    "\n" +
                    "==== Parameters\n" +
                    "\n" +
                    "[cols=\"headers\"]\n" +
                    "|===\n" +
                    "|Name|Position|Type|Required\n" +
                    "|e1|0|String|false\n" +
                    "|e2|1|int|false\n" +
                    "|===\n" +
                    "\n" +
                    "==== Result type\n" +
                    "\n" +
                    "`String`\n" +
                    "\n" +
                    "\n" +
                    "=== test3\n" +
                    "\n" +
                    "==== Parameters\n" +
                    "\n" +
                    "[cols=\"headers\"]\n" +
                    "|===\n" +
                    "|Name|Position|Type|Required\n" +
                    "|arg0|0|com.github.rmannibucau.test.jsonrpc.protocol.MyRpcEndpoints$SomeModel|false\n" +
                    "|===\n" +
                    "\n" +
                    "==== Result type\n" +
                    "\n" +
                    "`String`\n" +
                    "\n" +
                    "\n" +
                    "=== test4\n" +
                    "\n" +
                    "==== Result type\n" +
                    "\n" +
                    "`com.github.rmannibucau.test.jsonrpc.protocol.MyRpcEndpoints$SomeModel`\n" +
                    "\n" +
                    "\n" +
                    "=== test5\n" +
                    "\n" +
                    "==== Result type\n" +
                    "\n" +
                    "`com.github.rmannibucau.test.jsonrpc.protocol.MyRpcEndpoints$SomeModel`\n" +
                    "\n" +
                    "\n" +
                    "=== test6\n" +
                    "\n" +
                    "==== Result type\n" +
                    "\n" +
                    "`com.github.rmannibucau.test.jsonrpc.protocol.MyRpcEndpoints$SomeModel`\n" +
                    "\n" +
                    "\n" +
                    "=== test7\n" +
                    "\n" +
                    "==== Result type\n" +
                    "\n" +
                    "`String`\n" +
                    "\n" +
                    "\n" +
                    "== Model Schemas\n" +
                    "\n" +
                    "=== com.github.rmannibucau.test.jsonrpc.protocol.MyRpcEndpoints$SomeModel\n" +
                    "\n" +
                    "[cols=\"headers\"]\n" +
                    "|===\n" +
                    "|Attribute|Type\n" +
                    "|data1|String\n" +
                    "|data2|int\n" +
                    "|===\n" +
                    "\n" +
                    "\n" +
                    "\n", new String(out.toByteArray(), StandardCharsets.UTF_8));
        }
    }
}
