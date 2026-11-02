package com.kant.cardhub.common.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenApiContractTest {

    private static final Set<String> EFFECTIVE_STATUSES = Set.of(
            "ACTIVE", "HIDDEN", "RESERVED", "SOLD", "DELETED", "BLOCKED");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    JsonNode openApi;

    @BeforeAll
    void loadOpenApiDocument() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        openApi = objectMapper.readTree(body);
    }

    @Test
    void legacyHandlersAreDeprecatedButMixedCompatibilityOperationsRemainCurrent() {
        assertThat(operation("/api/v1/admin/listings/{listingId}/hide", "post").path("deprecated").asBoolean())
                .isTrue();
        assertThat(operation("/api/v1/favorites/{listingId}", "post").path("deprecated").asBoolean())
                .isTrue();
        assertThat(operation("/api/v1/favorites/{listingId}", "delete").path("deprecated").asBoolean())
                .isTrue();

        assertThat(operation("/api/v1/price-alerts", "post").path("deprecated").asBoolean())
                .isFalse();
        assertThat(operation("/api/v1/orders", "post").path("deprecated").asBoolean())
                .isFalse();
        assertThat(operation("/api/v1/orders/group", "post").path("deprecated").asBoolean())
                .isFalse();
    }

    @Test
    void legacyPropertiesAreDeprecatedAndPointToTheirReplacement() {
        assertDeprecatedProperty(
                uniqueSchemaWithPropertiesExcluding(
                        Set.of("baselinePrice"), "cardVariantId", "listingId", "thresholdPercentage"),
                "listingId", "cardVariantId");
        assertDeprecatedProperty(
                uniqueSchemaWithProperties("baselinePrice", "thresholdPercentage", "triggeredPrice", "listingId"),
                "listingId", "cardVariantId");

        assertDeprecatedProperty(
                uniqueSchemaWithProperties("listingId", "offerId", "shippingAddress", "shippingAddressSnapshot"),
                "shippingAddress", "shippingAddressSnapshot");
        assertDeprecatedProperty(
                uniqueSchemaWithProperties("listingIds", "shippingAddress", "shippingAddressSnapshot"),
                "shippingAddress", "shippingAddressSnapshot");
        assertDeprecatedProperty(
                uniqueSchemaWithProperties("orderNo", "fulfillmentStatus", "shippingAddress", "shippingAddressSnapshot"),
                "shippingAddress", "shippingAddressSnapshot");

        List<JsonNode> favoriteCompatibleSchemas = schemasWithProperties(
                "id", "cardVariantId", "listingId", "createdAt");
        assertThat(favoriteCompatibleSchemas)
                .as("favorite/price-alert response schemas carrying a legacy listingId")
                .isNotEmpty();
        favoriteCompatibleSchemas.forEach(schema ->
                assertDeprecatedProperty(schema, "listingId", "cardVariantId"));
    }

    @Test
    void allThreeListingResponseShapesExposeTheSixEffectiveStatuses() {
        List<Map.Entry<String, JsonNode>> listingSchemas = componentSchemas().stream()
                .filter(entry -> entry.getValue().path("properties").has("effectiveStatus"))
                .toList();

        assertThat(listingSchemas)
                .extracting(Map.Entry::getKey)
                .as("Listing Summary, Detail and admin ListingResponse schemas")
                .hasSizeGreaterThanOrEqualTo(3);

        listingSchemas.forEach(entry -> {
            JsonNode properties = entry.getValue().path("properties");
            assertThat(properties.has("status"))
                    .as("%s keeps raw lifecycle status", entry.getKey())
                    .isTrue();
            assertThat(properties.has("moderationStatus"))
                    .as("%s keeps raw moderation status", entry.getKey())
                    .isTrue();

            JsonNode effectiveStatus = resolveSchema(properties.path("effectiveStatus"));
            assertThat(textValues(effectiveStatus.path("enum")))
                    .as("%s effectiveStatus enum", entry.getKey())
                    .containsExactlyInAnyOrderElementsOf(EFFECTIVE_STATUSES);
            assertThat(properties.path("effectiveStatus").path("description").asText())
                    .as("%s effectiveStatus precedence description", entry.getKey())
                    .containsIgnoringCase("BLOCKED")
                    .contains("lifecycle");
        });

        assertResponseContainsProperty("/api/v1/listings", "get", "200", "effectiveStatus");
        assertResponseContainsProperty("/api/v1/listings/{listingId}", "get", "200", "effectiveStatus");
        assertResponseContainsProperty("/api/v1/admin/listings", "get", "200", "effectiveStatus");

    }

    @Test
    void changedOperationsHaveTagsDescriptionsSuccessAndMajorErrorStatuses() {
        List<OperationContract> contracts = List.of(
                new OperationContract("/api/v1/catalog/variants", "get", "200", "400"),
                new OperationContract("/api/v1/admin/catalog/games", "post", "200", "400"),
                new OperationContract("/api/v1/admin/catalog/variants/{variantId}/listability", "patch", "200", "404"),
                new OperationContract("/api/v1/listings", "get", "200", "400"),
                new OperationContract("/api/v1/listings/{listingId}", "get", "200", "404"),
                new OperationContract("/api/v1/admin/listings/{listingId}/block", "post", "200", "400"),
                new OperationContract("/api/v1/admin/listings/{listingId}/unblock", "post", "200", "404"),
                new OperationContract("/api/v1/favorites/card-variants/{variantId}", "post", "201", "409"),
                new OperationContract("/api/v1/price-alerts", "post", "201", "422"),
                new OperationContract("/api/v1/orders", "post", "201", "400"),
                new OperationContract("/api/v1/orders/group", "post", "201", "400"),
                new OperationContract("/api/v1/orders/{orderId}", "get", "200", "404"),
                new OperationContract("/api/v1/orders/{orderId}/refund", "get", "200", "404"),
                new OperationContract("/api/v1/admin/payments/{paymentId}", "get", "200", "404"),
                new OperationContract("/api/v1/admin/payments/{paymentId}/reconcile", "post", "200", "404"),
                new OperationContract("/api/v1/shipping/carriers", "get", "200", "401")
        );

        contracts.forEach(this::assertDocumented);

        List<OperationKey> exampleBearingOperations = List.of(
                new OperationKey("/api/v1/catalog/variants", "get"),
                new OperationKey("/api/v1/admin/catalog/games", "post"),
                new OperationKey("/api/v1/listings", "post"),
                new OperationKey("/api/v1/admin/listings/{listingId}/block", "post"),
                new OperationKey("/api/v1/favorites/card-variants/{variantId}", "post"),
                new OperationKey("/api/v1/price-alerts", "post"),
                new OperationKey("/api/v1/orders", "post"),
                new OperationKey("/api/v1/orders/group", "post"),
                new OperationKey("/api/v1/payments/confirm", "post"),
                new OperationKey("/api/v1/shipping/carriers", "get")
        );
        exampleBearingOperations.forEach(key -> assertThat(containsExample(operation(key.path(), key.method())))
                .as("%s %s has a non-sensitive example", key.method().toUpperCase(), key.path())
                .isTrue());
    }

    @Test
    void publicReadsOverrideGlobalSecurityAndProtectedOperationsKeepBearerAuth() {
        List<OperationKey> publicReads = List.of(
                new OperationKey("/api/v1/catalog/games", "get"),
                new OperationKey("/api/v1/catalog/sets", "get"),
                new OperationKey("/api/v1/catalog/cards", "get"),
                new OperationKey("/api/v1/catalog/cards/{cardId}", "get"),
                new OperationKey("/api/v1/catalog/variants", "get"),
                new OperationKey("/api/v1/catalog/variants/{variantId}", "get"),
                new OperationKey("/api/v1/listings", "get"),
                new OperationKey("/api/v1/listings/{listingId}", "get")
        );
        publicReads.forEach(key -> {
            JsonNode security = operation(key.path(), key.method()).path("security");
            assertThat(security.isArray() && security.isEmpty())
                    .as("%s %s explicitly has no security requirement", key.method().toUpperCase(), key.path())
                    .isTrue();
        });

        assertThat(openApi.at("/components/securitySchemes/bearerAuth").path("scheme").asText())
                .isEqualTo("bearer");
        assertThat(hasBearerRequirement(openApi.path("security")))
                .as("global bearerAuth requirement")
                .isTrue();

        List<OperationKey> protectedOperations = List.of(
                new OperationKey("/api/v1/admin/catalog/games", "post"),
                new OperationKey("/api/v1/listings", "post"),
                new OperationKey("/api/v1/favorites", "get"),
                new OperationKey("/api/v1/price-alerts", "post"),
                new OperationKey("/api/v1/orders", "post"),
                new OperationKey("/api/v1/payments/confirm", "post"),
                new OperationKey("/api/v1/shipping/carriers", "get"),
                new OperationKey("/api/v1/admin/listings/{listingId}/block", "post")
        );
        protectedOperations.forEach(key -> assertThat(hasBearerRequirement(effectiveSecurity(key)))
                .as("%s %s uses bearerAuth", key.method().toUpperCase(), key.path())
                .isTrue());
    }

    @Test
    void sweetTrackerCallbackRemainsOutOfThePublishedContract() {
        List<String> shippingCallbackPaths = new ArrayList<>();
        openApi.path("paths").properties().forEach(entry -> {
            String path = entry.getKey().toLowerCase();
            if (path.contains("shipping") && path.contains("callback")) {
                shippingCallbackPaths.add(entry.getKey());
            }
        });
        assertThat(shippingCallbackPaths).isEmpty();
    }

    private void assertDeprecatedProperty(JsonNode schema, String propertyName, String replacementName) {
        JsonNode property = schema.path("properties").path(propertyName);
        assertThat(property.path("deprecated").asBoolean())
                .as("%s is marked deprecated", propertyName)
                .isTrue();
        assertThat(property.path("description").asText())
                .as("%s documents its replacement", propertyName)
                .contains(replacementName);
    }

    private void assertDocumented(OperationContract contract) {
        JsonNode operation = operation(contract.path(), contract.method());
        assertThat(textValues(operation.path("tags")))
                .as("%s %s tags", contract.method().toUpperCase(), contract.path())
                .isNotEmpty();
        assertThat(operation.path("summary").asText())
                .as("%s %s summary", contract.method().toUpperCase(), contract.path())
                .isNotBlank();
        assertThat(operation.path("description").asText())
                .as("%s %s description", contract.method().toUpperCase(), contract.path())
                .isNotBlank();

        JsonNode responses = operation.path("responses");
        assertThat(responses.has(contract.successCode()))
                .as("%s %s actual success status %s", contract.method().toUpperCase(), contract.path(),
                        contract.successCode())
                .isTrue();
        assertThat(responses.path(contract.successCode()).path("description").asText())
                .isNotBlank();
        assertThat(responses.has(contract.majorErrorCode()))
                .as("%s %s major error status %s", contract.method().toUpperCase(), contract.path(),
                        contract.majorErrorCode())
                .isTrue();
        assertThat(responses.path(contract.majorErrorCode()).path("description").asText())
                .isNotBlank();
    }

    private JsonNode operation(String path, String method) {
        JsonNode operation = openApi.path("paths").path(path).path(method);
        assertThat(operation.isMissingNode())
                .as("OpenAPI operation %s %s exists", method.toUpperCase(), path)
                .isFalse();
        return operation;
    }

    private JsonNode effectiveSecurity(OperationKey key) {
        JsonNode operationSecurity = operation(key.path(), key.method()).get("security");
        return operationSecurity == null ? openApi.path("security") : operationSecurity;
    }

    private boolean hasBearerRequirement(JsonNode security) {
        if (!security.isArray()) {
            return false;
        }
        for (JsonNode requirement : security) {
            if (requirement.has("bearerAuth")) {
                return true;
            }
        }
        return false;
    }

    private JsonNode uniqueSchemaWithProperties(String... propertyNames) {
        List<JsonNode> matches = schemasWithProperties(propertyNames);
        assertThat(matches)
                .as("one schema containing %s", List.of(propertyNames))
                .hasSize(1);
        return matches.getFirst();
    }

    private JsonNode uniqueSchemaWithPropertiesExcluding(
            Set<String> excludedPropertyNames,
            String... propertyNames
    ) {
        List<JsonNode> matches = schemasWithProperties(propertyNames).stream()
                .filter(schema -> excludedPropertyNames.stream()
                        .noneMatch(schema.path("properties")::has))
                .toList();
        assertThat(matches)
                .as("one schema containing %s and excluding %s", List.of(propertyNames), excludedPropertyNames)
                .hasSize(1);
        return matches.getFirst();
    }

    private List<JsonNode> schemasWithProperties(String... propertyNames) {
        return componentSchemas().stream()
                .map(Map.Entry::getValue)
                .filter(schema -> {
                    JsonNode properties = schema.path("properties");
                    for (String propertyName : propertyNames) {
                        if (!properties.has(propertyName)) {
                            return false;
                        }
                    }
                    return true;
                })
                .toList();
    }

    private List<Map.Entry<String, JsonNode>> componentSchemas() {
        return new ArrayList<>(openApi.path("components").path("schemas").properties());
    }

    private void assertResponseContainsProperty(
            String path,
            String method,
            String responseCode,
            String propertyName
    ) {
        JsonNode content = operation(path, method)
                .path("responses")
                .path(responseCode)
                .path("content");
        JsonNode mediaType = content.get("application/json");
        if (mediaType == null && !content.properties().isEmpty()) {
            mediaType = content.properties().iterator().next().getValue();
        }
        JsonNode responseSchema = mediaType == null
                ? objectMapper.createObjectNode()
                : mediaType.path("schema");
        assertThat(containsProperty(responseSchema, propertyName, new HashSet<>()))
                .as("%s %s response contains %s", method.toUpperCase(), path, propertyName)
                .isTrue();
    }

    private boolean containsProperty(JsonNode node, String propertyName, Set<String> visitedRefs) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }

        JsonNode reference = node.get("$ref");
        if (reference != null) {
            String ref = reference.asText();
            if (visitedRefs.add(ref)) {
                boolean found = containsProperty(resolveReference(ref), propertyName, visitedRefs);
                visitedRefs.remove(ref);
                if (found) {
                    return true;
                }
            }
        }

        JsonNode properties = node.get("properties");
        if (properties != null && properties.has(propertyName)) {
            return true;
        }
        for (JsonNode child : node) {
            if (containsProperty(child, propertyName, visitedRefs)) {
                return true;
            }
        }
        return false;
    }

    private JsonNode resolveSchema(JsonNode schema) {
        JsonNode current = schema;
        Set<String> visited = new HashSet<>();
        while (current != null && current.has("$ref")) {
            String ref = current.path("$ref").asText();
            if (!visited.add(ref)) {
                break;
            }
            current = resolveReference(ref);
        }
        return current == null ? objectMapper.createObjectNode() : current;
    }

    private JsonNode resolveReference(String reference) {
        if (!reference.startsWith("#/")) {
            return objectMapper.createObjectNode();
        }
        return openApi.at(reference.substring(1));
    }

    private boolean containsExample(JsonNode node) {
        return containsExample(node, new HashSet<>());
    }

    private boolean containsExample(JsonNode node, Set<String> visitedRefs) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }
        JsonNode example = node.get("example");
        if (example != null && !example.isNull()) {
            if (!example.isTextual() || !example.asText().isBlank()) {
                return true;
            }
        }
        JsonNode examples = node.get("examples");
        if (examples != null && examples.size() > 0) {
            return true;
        }

        JsonNode reference = node.get("$ref");
        if (reference != null) {
            String ref = reference.asText();
            if (visitedRefs.add(ref)) {
                boolean found = containsExample(resolveReference(ref), visitedRefs);
                visitedRefs.remove(ref);
                if (found) {
                    return true;
                }
            }
        }

        Iterator<JsonNode> children = node.iterator();
        while (children.hasNext()) {
            if (containsExample(children.next(), visitedRefs)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> textValues(JsonNode array) {
        Set<String> values = new LinkedHashSet<>();
        if (!array.isArray()) {
            return values;
        }
        for (JsonNode item : array) {
            values.add(item.asText());
        }
        return values;
    }

    private record OperationKey(String path, String method) {
    }

    private record OperationContract(
            String path,
            String method,
            String successCode,
            String majorErrorCode
    ) {
    }
}
