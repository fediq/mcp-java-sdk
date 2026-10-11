/*
 * Copyright 2025-2025 the original author or authors.
 */

package io.modelcontextprotocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.util.DefaultMcpUriTemplateManagerFactory;
import io.modelcontextprotocol.util.McpUriTemplateManager;
import io.modelcontextprotocol.util.McpUriTemplateManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link McpUriTemplateManager} and its implementations.
 *
 * @author Christian Tzolov
 */
public class McpUriTemplateManagerTests {

	private McpUriTemplateManagerFactory uriTemplateFactory;

	@BeforeEach
	void setUp() {
		this.uriTemplateFactory = new DefaultMcpUriTemplateManagerFactory();
	}

	@Test
	void shouldExtractVariableNamesFromTemplate() {
		List<String> variables = this.uriTemplateFactory.create("/api/users/{userId}/posts/{postId}")
			.getVariableNames();
		assertEquals(2, variables.size());
		assertEquals("userId", variables.get(0));
		assertEquals("postId", variables.get(1));
	}

	@Test
	void shouldReturnEmptyListWhenTemplateHasNoVariables() {
		List<String> variables = this.uriTemplateFactory.create("/api/users/all").getVariableNames();
		assertEquals(0, variables.size());
	}

	@Test
	void shouldThrowExceptionWhenExtractingVariablesFromNullTemplate() {
		assertThrows(IllegalArgumentException.class, () -> this.uriTemplateFactory.create(null).getVariableNames());
	}

	@Test
	void shouldThrowExceptionWhenExtractingVariablesFromEmptyTemplate() {
		assertThrows(IllegalArgumentException.class, () -> this.uriTemplateFactory.create("").getVariableNames());
	}

	@Test
	void shouldThrowExceptionWhenTemplateContainsDuplicateVariables() {
		assertThrows(IllegalArgumentException.class,
				() -> this.uriTemplateFactory.create("/api/users/{userId}/posts/{userId}").getVariableNames());
	}

	@Test
	void shouldExtractVariableValuesFromRequestUri() {
		Map<String, String> values = this.uriTemplateFactory.create("/api/users/{userId}/posts/{postId}")
			.extractVariableValues("/api/users/123/posts/456");
		assertEquals(2, values.size());
		assertEquals("123", values.get("userId"));
		assertEquals("456", values.get("postId"));
	}

	@Test
	void shouldReturnEmptyMapWhenTemplateHasNoVariables() {
		Map<String, String> values = this.uriTemplateFactory.create("/api/users/all")
			.extractVariableValues("/api/users/all");
		assertEquals(0, values.size());
	}

	@Test
	void shouldReturnEmptyMapWhenRequestUriIsNull() {
		Map<String, String> values = this.uriTemplateFactory.create("/api/users/{userId}/posts/{postId}")
			.extractVariableValues(null);
		assertEquals(0, values.size());
	}

	@Test
	void shouldMatchUriAgainstTemplatePattern() {
		var uriTemplateManager = this.uriTemplateFactory.create("/api/users/{userId}/posts/{postId}");

		assertTrue(uriTemplateManager.matches("/api/users/123/posts/456"));
		assertFalse(uriTemplateManager.matches("/api/users/123/comments/456"));
	}

	@Test
	void shouldMatchUriWithQueryParameters() {
		String templateWithQuery = "file://name/search?={search}";
		var uriTemplateManager = this.uriTemplateFactory.create(templateWithQuery);

		assertTrue(uriTemplateManager.matches("file://name/search?=abcd"),
				"Should correctly match a URI containing query parameters.");
	}

	/**
	 * Reserved expressions previously treated '+' as part of the name and rejected nested
	 * paths. Matching and extraction must preserve the original encoded value.
	 */
	@Test
	void shouldMatchAndExtractReservedNestedPath() {
		String template = "skill://woofproof-internal/{skillName}/{+relativePath}";
		var manager = this.uriTemplateFactory.create(template);
		assertEquals(List.of("skillName", "relativePath"), manager.getVariableNames());
		for (String path : List.of("SKILL.md", "references/notes%20with%20space.md", "references/deep/asset%2f%2F.bin",
				"./../references/asset.bin")) {
			String uri = "skill://woofproof-internal/test-skill/" + path;
			assertTrue(manager.matches(uri));
			assertEquals(Map.of("skillName", "test-skill", "relativePath", path), manager.extractVariableValues(uri));
		}
		String nestedUri = "skill://woofproof-internal/test-skill/references/asset.bin";
		var simpleManager = this.uriTemplateFactory.create(template.replace("{+relativePath}", "{relativePath}"));
		assertFalse(simpleManager.matches(nestedUri));
		assertEquals(Map.of(), simpleManager.extractVariableValues(nestedUri));
		assertFalse(manager.matches("skill://woofproof-internal/test-skill/"));
		assertEquals(Map.of(), manager.extractVariableValues("skill://woofproof-internal/test-skill/"));
	}

	/**
	 * The reserved operator must not hide duplicate variable declarations.
	 */
	@Test
	void shouldRejectMixedDuplicateVariables() {
		assertThrows(IllegalArgumentException.class,
				() -> this.uriTemplateFactory.create("/{path}/{+path}").getVariableNames());
	}

	/**
	 * Reserved paths must stop at the literal suffix, with regex delimiters quoted.
	 */
	@Test
	void shouldMatchReservedPathWithLiteralSuffix() {
		var manager = this.uriTemplateFactory.create("file:///{+path}?download=true");
		String uri = "file:///references/deep/notes%20with%20space.md?download=true";
		assertTrue(manager.matches(uri));
		assertEquals(Map.of("path", "references/deep/notes%20with%20space.md"), manager.extractVariableValues(uri));
		for (String invalidUri : List.of(uri + "extra", uri.replace("?download", "Xdownload"), "prefix" + uri,
				"file:///?download=true")) {
			assertFalse(manager.matches(invalidUri));
			assertEquals(Map.of(), manager.extractVariableValues(invalidUri));
		}
	}

}
