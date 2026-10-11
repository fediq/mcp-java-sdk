/*
 * Copyright 2026-2026 the original author or authors.
 */

package io.modelcontextprotocol.server;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.util.DefaultMcpUriTemplateManagerFactory;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StatelessRequestHandlerRegistrationTests {

	private McpServer.StatelessAsyncSpecification specification(McpStatelessServerTransport transport) {
		return McpServer.async(transport)
			.jsonMapper(mock(McpJsonMapper.class))
			.jsonSchemaValidator(mock(JsonSchemaValidator.class));
	}

	@Test
	void publicBuilderPreservesPayloadContextAndIsolatesRegistrations() {
		var transport = mock(McpStatelessServerTransport.class);
		var context = McpTransportContext.create(Map.of("user", "example"));
		var params = Map.of("cursor", "page-1");
		var result = Map.of("items", List.of(Map.of("uri", "example://item", "metadata", Map.of("name", "sample"))),
				"nextCursor", "page-2");
		Map<String, McpStatelessRequestHandler<?>> handlers = new HashMap<>();
		handlers.put("example/list", (actualContext, actualParams) -> {
			assertThat(actualContext).isSameAs(context);
			assertThat(actualParams).isSameAs(params);
			return Mono.just(result);
		});
		var builder = specification(transport);
		assertThat(builder.requestHandlers(handlers)).isSameAs(builder);
		builder.build();
		handlers.put("example/list", (ctx, input) -> Mono.just("changed"));
		assertThat(builder.requestHandler("example/later", (ctx, input) -> Mono.just("later"))).isSameAs(builder);
		var captor = ArgumentCaptor.forClass(McpStatelessServerHandler.class);
		verify(transport).setMcpHandler(captor.capture());
		var dispatcher = captor.getValue();
		StepVerifier
			.create(dispatcher.handleRequest(context,
					new McpSchema.JSONRPCRequest("example/list", "custom-id", params)))
			.assertNext(response -> {
				assertThat(response.jsonrpc()).isEqualTo("2.0");
				assertThat(response.id()).isEqualTo("custom-id");
				assertThat(response.error()).isNull();
				assertThat(response.result()).isSameAs(result);
			})
			.verifyComplete();
		StepVerifier.create(dispatcher.handleRequest(context, new McpSchema.JSONRPCRequest("example/later", 2, null)))
			.assertNext(response -> assertThat(response.error().code()).isEqualTo(-32601))
			.verifyComplete();
		StepVerifier.create(dispatcher.handleRequest(context, new McpSchema.JSONRPCRequest("ping", 3, null)))
			.assertNext(response -> {
				assertThat(response.error()).isNull();
				assertThat(response.result()).isEqualTo(Map.of());
			})
			.verifyComplete();
	}

	@Test
	void rejectsConflictsAndInvalidBatchesWithoutPartialRegistration() {
		var builder = specification(mock(McpStatelessServerTransport.class));
		McpStatelessRequestHandler<?> handler = (ctx, params) -> Mono.just(Map.of());
		builder.requestHandler("example/list", handler);
		assertThatThrownBy(() -> builder.requestHandler("example/list", handler))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("example/list");
		for (String method : List.of("initialize", "ping", "tools/list", "tools/call", "resources/list",
				"resources/read", "resources/templates/list", "resources/subscribe", "resources/unsubscribe",
				"prompts/list", "prompts/get", "completion/complete", "logging/setLevel", "roots/list",
				"sampling/createMessage", "elicitation/create")) {
			assertThatThrownBy(() -> builder.requestHandler(method, handler))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining(method);
		}
		for (String method : List.of("", " ", " example/get", "example/get\t")) {
			assertThatThrownBy(() -> builder.requestHandler(method, handler))
				.isInstanceOf(IllegalArgumentException.class);
		}
		assertThatThrownBy(() -> builder.requestHandler(null, handler)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> builder.requestHandler("example/get", null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> builder.requestHandlers(null)).isInstanceOf(IllegalArgumentException.class);
		Map<String, McpStatelessRequestHandler<?>> batch = new LinkedHashMap<>();
		batch.put("example/get", handler);
		batch.put("example/list", handler);
		assertThatThrownBy(() -> builder.requestHandlers(batch)).isInstanceOf(IllegalArgumentException.class);
		assertThat(builder.requestHandler("example/get", handler)).isSameAs(builder);
		batch.clear();
		batch.put("example/valid", handler);
		batch.put("example/null", null);
		assertThatThrownBy(() -> builder.requestHandlers(batch)).isInstanceOf(IllegalArgumentException.class);
		builder.requestHandler("example/valid", handler);
		assertThat(builder.requestHandlers(Map.of())).isSameAs(builder);
	}

	@Test
	void constructionRejectsReservedMethodsEvenWhenFeaturesBypassBuilder() {
		var transport = mock(McpStatelessServerTransport.class);
		var features = new McpStatelessServerFeatures.Async(McpServer.DEFAULT_SERVER_INFO, null, null, null, null, null,
				null, null, null, Map.of("tools/list", (ctx, params) -> Mono.just(Map.of())));
		assertThatThrownBy(() -> new McpStatelessAsyncServer(transport, mock(McpJsonMapper.class), features,
				java.time.Duration.ofSeconds(10), new DefaultMcpUriTemplateManagerFactory(),
				mock(JsonSchemaValidator.class), true))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("tools/list");
	}

	@Test
	void serverWithoutCustomHandlersStillDispatchesPing() {
		var transport = mock(McpStatelessServerTransport.class);
		specification(transport).build();
		var captor = ArgumentCaptor.forClass(McpStatelessServerHandler.class);
		verify(transport).setMcpHandler(captor.capture());
		StepVerifier
			.create(captor.getValue()
				.handleRequest(McpTransportContext.EMPTY, new McpSchema.JSONRPCRequest("ping", 7, null)))
			.assertNext(response -> {
				assertThat(response.id()).isEqualTo(7);
				assertThat(response.error()).isNull();
				assertThat(response.result()).isEqualTo(Map.of());
			})
			.verifyComplete();
	}

	/**
	 * Standard resources/read previously failed to route nested reserved paths. The
	 * default manager must dispatch the request without changing its encoded URI.
	 */
	@Test
	void standardResourceReadPreservesReservedNestedUri() {
		var transport = mock(McpStatelessServerTransport.class);
		var mapper = mock(McpJsonMapper.class);
		String uri = "skill://woofproof-internal/test-skill/references/notes%20with%20space%2f%2F.md";
		var params = Map.of("uri", uri);
		var missingParams = Map.of("uri", "skill://unknown/test-skill/references/missing.md");
		when(mapper.convertValue(eq(params), any(io.modelcontextprotocol.json.TypeRef.class)))
			.thenReturn(new McpSchema.ReadResourceRequest(uri));
		when(mapper.convertValue(eq(missingParams), any(io.modelcontextprotocol.json.TypeRef.class)))
			.thenReturn(new McpSchema.ReadResourceRequest(missingParams.get("uri")));
		var content = new McpSchema.TextResourceContents(uri, "text/markdown", "Nested resource", null);
		var result = new McpSchema.ReadResourceResult(List.of(content), null);
		McpServer.async(transport)
			.jsonMapper(mapper)
			.jsonSchemaValidator(mock(JsonSchemaValidator.class))
			.capabilities(McpSchema.ServerCapabilities.builder().resources(false, false).build())
			.resourceTemplates(
					new McpStatelessServerFeatures.AsyncResourceTemplateSpecification(McpSchema.ResourceTemplate
						.builder("skill://woofproof-internal/{skillName}/{+relativePath}", "skill")
						.build(), (ctx, request) -> {
							assertThat(request.uri()).isEqualTo(uri);
							return Mono.just(result);
						}))
			.build();
		var captor = ArgumentCaptor.forClass(McpStatelessServerHandler.class);
		verify(transport).setMcpHandler(captor.capture());
		var dispatcher = captor.getValue();
		StepVerifier.create(dispatcher.handleRequest(McpTransportContext.EMPTY,
				new McpSchema.JSONRPCRequest("resources/read", "nested-id", params)))
			.assertNext(response -> {
				assertThat(response.jsonrpc()).isEqualTo("2.0");
				assertThat(response.id()).isEqualTo("nested-id");
				assertThat(response.error()).isNull();
				assertThat(response.result()).isInstanceOf(McpSchema.ReadResourceResult.class).isSameAs(result);
				assertThat(((McpSchema.ReadResourceResult) response.result()).contents().get(0).uri()).isEqualTo(uri);
			})
			.verifyComplete();
		StepVerifier
			.create(dispatcher.handleRequest(McpTransportContext.EMPTY,
					new McpSchema.JSONRPCRequest("resources/read", "missing-id", missingParams)))
			.assertNext(response -> {
				assertThat(response.jsonrpc()).isEqualTo("2.0");
				assertThat(response.id()).isEqualTo("missing-id");
				assertThat(response.result()).isNull();
				assertThat(response.error().code()).isEqualTo(-32002);
			})
			.verifyComplete();
	}

}
