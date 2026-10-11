/*
 * Copyright 2026-2026 the original author or authors.
 */

package io.modelcontextprotocol.server;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.Map;
import io.modelcontextprotocol.spec.McpError;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultMcpStatelessServerHandlerTests {

	@Test
	void testHandleRequestWithUnregisteredMethod() {
		// no request/initialization handlers
		DefaultMcpStatelessServerHandler handler = new DefaultMcpStatelessServerHandler(Collections.emptyMap(),
				Collections.emptyMap());

		// unregistered method
		McpSchema.JSONRPCRequest request = new McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION, "resources/list",
				"test-id-123", null);

		StepVerifier.create(handler.handleRequest(McpTransportContext.EMPTY, request)).assertNext(response -> {
			assertThat(response).isNotNull();
			assertThat(response.jsonrpc()).isEqualTo(McpSchema.JSONRPC_VERSION);
			assertThat(response.id()).isEqualTo("test-id-123");
			assertThat(response.result()).isNull();

			assertThat(response.error()).isNotNull();
			assertThat(response.error().code()).isEqualTo(McpSchema.ErrorCodes.METHOD_NOT_FOUND);
			assertThat(response.error().message()).isEqualTo("Method not found: resources/list");
		}).verifyComplete();
	}

	/**
	 * Synchronous handler failures previously escaped the JSON-RPC error conversion. Both
	 * invocation and publisher failures must preserve protocol errors and ids.
	 */
	@Test
	void synchronousAndReactiveFailuresUseTheSameErrorConversion() {
		var protocolError = new McpSchema.JSONRPCResponse.JSONRPCError(-32602, "Invalid input", Map.of("field", "uri"));
		Map<String, McpStatelessRequestHandler<?>> handlers = Map.of("example/sync", (ctx, params) -> {
			throw new IllegalStateException("failure");
		}, "example/reactive", (ctx, params) -> Mono.error(new IllegalStateException("failure")),
				"example/protocol-sync", (ctx, params) -> {
					throw new McpError(protocolError);
				}, "example/protocol-reactive", (ctx, params) -> Mono.error(new McpError(protocolError)));
		var dispatcher = new DefaultMcpStatelessServerHandler(handlers, Map.of());
		handlers.keySet().forEach(method -> {
			StepVerifier
				.create(dispatcher.handleRequest(McpTransportContext.EMPTY,
						new McpSchema.JSONRPCRequest(method, "error-id", null)))
				.assertNext(response -> {
					assertThat(response.jsonrpc()).isEqualTo("2.0");
					assertThat(response.id()).isEqualTo("error-id");
					assertThat(response.result()).isNull();
					assertThat(response.error()).isEqualTo(method.contains("protocol") ? protocolError
							: new McpSchema.JSONRPCResponse.JSONRPCError(-32603, "failure"));
				})
				.verifyComplete();
		});
	}

	/**
	 * Exceptions without a message must still produce a valid internal error response.
	 */
	@Test
	void failureWithoutMessageProducesInternalError() {
		var dispatcher = new DefaultMcpStatelessServerHandler(Map.of("example/fail", (ctx, params) -> {
			throw new IllegalStateException();
		}), Map.of());
		StepVerifier
			.create(dispatcher.handleRequest(McpTransportContext.EMPTY,
					new McpSchema.JSONRPCRequest("example/fail", 1, null)))
			.assertNext(response -> {
				assertThat(response.error().code()).isEqualTo(-32603);
				assertThat(response.error().message()).isEqualTo("Internal error");
			})
			.verifyComplete();
	}

}
