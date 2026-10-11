/*
 * Copyright 2024-2025 the original author or authors.
 */

package io.modelcontextprotocol.server;

import io.modelcontextprotocol.common.McpTransportContext;
import reactor.core.publisher.Mono;

/**
 * Handler for MCP requests in a stateless server.
 *
 * @param <R> type of the MCP response
 * @author Dariusz Jędrzejczyk
 */
public interface McpStatelessRequestHandler<R> {

	/**
	 * Handle the request and complete with a result.
	 * @param transportContext {@link McpTransportContext} associated with the transport
	 * @param params the payload of the MCP request
	 * @return Mono emitting exactly one non-null result payload, not a JSON-RPC response;
	 * an empty publisher must not be used for a successful response
	 */
	Mono<R> handle(McpTransportContext transportContext, Object params);

}
