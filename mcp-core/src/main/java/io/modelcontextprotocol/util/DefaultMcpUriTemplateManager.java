/*
 * Copyright 2025-2025 the original author or authors.
 */

package io.modelcontextprotocol.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default implementation of the UriTemplateUtils interface.
 * <p>
 * This class provides methods for extracting variables from URI templates and matching
 * them against actual URIs.
 *
 * @author Christian Tzolov
 */
public class DefaultMcpUriTemplateManager implements McpUriTemplateManager {

	/**
	 * Pattern to match simple {variableName} and reserved {+variableName} expressions.
	 */
	private static final Pattern URI_VARIABLE_PATTERN = Pattern.compile("\\{(\\+?)([^/]+?)\\}");

	private final String uriTemplate;

	/**
	 * Constructor for DefaultMcpUriTemplateManager.
	 * @param uriTemplate The URI template to be used for variable extraction
	 */
	public DefaultMcpUriTemplateManager(String uriTemplate) {
		Assert.hasText(uriTemplate, "URI template must not be null or empty");
		this.uriTemplate = uriTemplate;
	}

	/**
	 * Extract URI variable names from a URI template.
	 * @param uriTemplate The URI template containing variables in the format
	 * {variableName}
	 * @return A list of variable names extracted from the template
	 * @throws IllegalArgumentException if duplicate variable names are found
	 */
	@Override
	public List<String> getVariableNames() {
		List<String> variables = new ArrayList<>();
		Matcher matcher = URI_VARIABLE_PATTERN.matcher(this.uriTemplate);

		while (matcher.find()) {
			String variableName = matcher.group(2);
			if (variables.contains(variableName)) {
				throw new IllegalArgumentException("Duplicate URI variable name in template: " + variableName);
			}
			variables.add(variableName);
		}

		return variables;
	}

	/**
	 * Extract URI variable values from the actual request URI.
	 * <p>
	 * This method converts the URI template into a regex pattern, then uses that pattern
	 * to extract variable values from the request URI.
	 * @param requestUri The actual URI from the request
	 * @return A map of variable names to their values
	 * @throws IllegalArgumentException if the URI template is invalid or the request URI
	 * doesn't match the template pattern
	 */
	@Override
	public Map<String, String> extractVariableValues(String requestUri) {
		Map<String, String> variableValues = new HashMap<>();
		List<String> uriVariables = this.getVariableNames();

		if (!Utils.hasText(requestUri) || uriVariables.isEmpty()) {
			return variableValues;
		}

		try {
			Matcher matcher = createUriPattern().matcher(requestUri);

			if (matcher.matches() && matcher.groupCount() == uriVariables.size()) {
				for (int i = 0; i < uriVariables.size(); i++) {
					String value = matcher.group(i + 1);
					if (value == null || value.isEmpty()) {
						throw new IllegalArgumentException(
								"Empty value for URI variable '" + uriVariables.get(i) + "' in URI: " + requestUri);
					}
					variableValues.put(uriVariables.get(i), value);
				}
			}
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Error parsing URI template: " + uriTemplate + " for URI: " + requestUri,
					e);
		}

		return variableValues;
	}

	/**
	 * Check if a URI matches the uriTemplate with variables.
	 * @param uri The URI to check
	 * @return true if the URI matches the pattern, false otherwise
	 */
	@Override
	public boolean matches(String uri) {
		// If the uriTemplate doesn't contain variables, do a direct comparison
		if (!this.isUriTemplate(this.uriTemplate)) {
			return uri.equals(this.uriTemplate);
		}

		return createUriPattern().matcher(uri).matches();
	}

	private Pattern createUriPattern() {
		StringBuilder patternBuilder = new StringBuilder("^");
		Matcher variableMatcher = URI_VARIABLE_PATTERN.matcher(this.uriTemplate);
		int lastEnd = 0;

		while (variableMatcher.find()) {
			patternBuilder.append(Pattern.quote(this.uriTemplate.substring(lastEnd, variableMatcher.start())));
			// Reserved expressions can span path segments; simple variables cannot.
			patternBuilder.append(variableMatcher.group(1).equals("+") ? "(.+)" : "([^/]+)");
			lastEnd = variableMatcher.end();
		}

		patternBuilder.append(Pattern.quote(this.uriTemplate.substring(lastEnd)));
		patternBuilder.append("$");
		return Pattern.compile(patternBuilder.toString());
	}

	@Override
	public boolean isUriTemplate(String uri) {
		return URI_VARIABLE_PATTERN.matcher(uri).find();
	}

}
