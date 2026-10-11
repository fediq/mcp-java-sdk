/*
 * Copyright 2026-2026 the original author or authors.
 */

package io.modelcontextprotocol.server;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.HttpHeaders;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.ProtocolVersions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static io.modelcontextprotocol.util.McpJsonMapperUtils.JSON_MAPPER;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

class StatelessCustomRequestTransportTests {

	@Test
	void publicRegistrationDispatchesThroughHttpTransportAndSerializesNestedTree() throws Exception {
		var context = McpTransportContext.create(Map.of("principal", "example"));
		var transport = HttpServletStatelessServerTransport.builder().contextExtractor(request -> context).build();
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("name", "sample");
		metadata.put("optional", null);
		metadata.put("values", Arrays.asList(true, 42, 1.5, null));
		var result = Map.of("items", List.of(Map.of("uri", "example://item", "metadata", metadata)), "nextCursor",
				"page-2");
		var capabilities = McpSchema.ServerCapabilities.builder()
			.extensions(Map.of("com.example/catalog", Map.of()))
			.build();
		var server = McpServer.async(transport)
			.capabilities(capabilities)
			.requestHandler("example/list", (ctx, params) -> {
				assertThat(ctx).isSameAs(context);
				assertThat(params).isEqualTo(Map.of("cursor", "page-1"));
				return Mono.just(result);
			})
			.build();
		try {
			assertThat(server.getServerCapabilities()).isSameAs(capabilities);
			var request = new MockHttpServletRequest("POST", "/mcp");
			request.setContent(JSON_MAPPER.writeValueAsBytes(
					new McpSchema.JSONRPCRequest("example/list", "http-id", Map.of("cursor", "page-1"))));
			request.addHeader("Content-Type", "application/json");
			request.addHeader("Accept", "application/json, text/event-stream");
			request.addHeader(HttpHeaders.PROTOCOL_VERSION, ProtocolVersions.MCP_2025_11_25);
			var response = new MockHttpServletResponse();
			transport.service(request, response);
			assertThat(response.getStatus()).isEqualTo(200);
			assertThatJson(response.getContentAsString()).isEqualTo("""
					{"jsonrpc":"2.0","id":"http-id","result":{
					  "items":[{"uri":"example://item","metadata":{
					    "name":"sample","optional":null,"values":[true,42,1.5,null]}}],"nextCursor":"page-2"}}
					""");
		}
		finally {
			server.closeGracefully().block();
		}
	}

}
