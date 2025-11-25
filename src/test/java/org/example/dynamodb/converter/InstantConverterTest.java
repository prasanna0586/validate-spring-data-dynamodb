package org.example.dynamodb.converter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InstantConverter Unit Tests")
class InstantConverterTest {

    private InstantConverter instantConverter;

    @BeforeEach
    void setUp() {
        instantConverter = new InstantConverter();
    }

    // ==================== transformFrom Tests (Instant -> AttributeValue) ====================

    @Test
    @DisplayName("Should convert Instant to ISO-8601 String AttributeValue")
    void testTransformFrom_Success() {
        // Given
        Instant instant = Instant.parse("2024-01-15T10:30:00Z");

        // When
        AttributeValue result = instantConverter.transformFrom(instant);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.s()).isEqualTo("2024-01-15T10:30:00Z");
    }

    @Test
    @DisplayName("Should convert null Instant to null AttributeValue")
    void testTransformFrom_Null() {
        // When
        AttributeValue result = instantConverter.transformFrom(null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.nul()).isTrue();
    }

    @Test
    @DisplayName("Should convert Instant with milliseconds correctly")
    void testTransformFrom_WithMilliseconds() {
        // Given
        Instant instant = Instant.parse("2024-01-15T10:30:00.123Z");

        // When
        AttributeValue result = instantConverter.transformFrom(instant);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.s()).isEqualTo("2024-01-15T10:30:00.123Z");
    }

    @Test
    @DisplayName("Should convert Instant with nanoseconds correctly")
    void testTransformFrom_WithNanoseconds() {
        // Given
        Instant instant = Instant.parse("2024-01-15T10:30:00.123456789Z");

        // When
        AttributeValue result = instantConverter.transformFrom(instant);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.s()).isEqualTo("2024-01-15T10:30:00.123456789Z");
    }

    @Test
    @DisplayName("Should convert epoch time correctly")
    void testTransformFrom_Epoch() {
        // Given
        Instant epoch = Instant.EPOCH; // 1970-01-01T00:00:00Z

        // When
        AttributeValue result = instantConverter.transformFrom(epoch);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.s()).isEqualTo("1970-01-01T00:00:00Z");
    }

    // ==================== transformTo Tests (AttributeValue -> Instant) ====================

    @Test
    @DisplayName("Should unconvert ISO-8601 String AttributeValue to Instant")
    void testTransformTo_Success() {
        // Given
        AttributeValue attributeValue = AttributeValue.builder().s("2024-01-15T10:30:00Z").build();

        // When
        Instant result = instantConverter.transformTo(attributeValue);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(Instant.parse("2024-01-15T10:30:00Z"));
    }

    @Test
    @DisplayName("Should unconvert null AttributeValue to null Instant")
    void testTransformTo_Null() {
        // When
        Instant result = instantConverter.transformTo(null);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should unconvert null-type AttributeValue to null Instant")
    void testTransformTo_NullType() {
        // Given
        AttributeValue attributeValue = AttributeValue.builder().nul(true).build();

        // When
        Instant result = instantConverter.transformTo(attributeValue);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should unconvert AttributeValue with milliseconds correctly")
    void testTransformTo_WithMilliseconds() {
        // Given
        AttributeValue attributeValue = AttributeValue.builder().s("2024-01-15T10:30:00.123Z").build();

        // When
        Instant result = instantConverter.transformTo(attributeValue);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(Instant.parse("2024-01-15T10:30:00.123Z"));
    }

    @Test
    @DisplayName("Should unconvert AttributeValue with nanoseconds correctly")
    void testTransformTo_WithNanoseconds() {
        // Given
        AttributeValue attributeValue = AttributeValue.builder().s("2024-01-15T10:30:00.123456789Z").build();

        // When
        Instant result = instantConverter.transformTo(attributeValue);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(Instant.parse("2024-01-15T10:30:00.123456789Z"));
    }

    @Test
    @DisplayName("Should unconvert epoch time correctly")
    void testTransformTo_Epoch() {
        // Given
        AttributeValue attributeValue = AttributeValue.builder().s("1970-01-01T00:00:00Z").build();

        // When
        Instant result = instantConverter.transformTo(attributeValue);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(Instant.EPOCH);
    }

    // ==================== Round-trip Tests ====================

    @Test
    @DisplayName("Should maintain precision through transformFrom and transformTo round-trip")
    void testRoundTrip_MaintainsPrecision() {
        // Given
        Instant original = Instant.parse("2024-01-15T10:30:00.123456789Z");

        // When - Convert to AttributeValue and back to Instant
        AttributeValue converted = instantConverter.transformFrom(original);
        Instant unconverted = instantConverter.transformTo(converted);

        // Then
        assertThat(unconverted).isEqualTo(original);
    }

    @Test
    @DisplayName("Should handle null values in round-trip")
    void testRoundTrip_Null() {
        // Given
        Instant original = null;

        // When - Convert to AttributeValue and back to Instant
        AttributeValue converted = instantConverter.transformFrom(original);
        Instant unconverted = instantConverter.transformTo(converted);

        // Then
        assertThat(converted.nul()).isTrue();
        assertThat(unconverted).isNull();
    }

    @Test
    @DisplayName("Should handle current time in round-trip")
    void testRoundTrip_CurrentTime() {
        // Given
        Instant now = Instant.now();

        // When - Convert to AttributeValue and back to Instant
        AttributeValue converted = instantConverter.transformFrom(now);
        Instant unconverted = instantConverter.transformTo(converted);

        // Then
        assertThat(unconverted).isEqualTo(now);
    }

    // ==================== Type and AttributeValueType Tests ====================

    @Test
    @DisplayName("Should return correct EnhancedType")
    void testType() {
        // When
        EnhancedType<Instant> type = instantConverter.type();

        // Then
        assertThat(type).isEqualTo(EnhancedType.of(Instant.class));
    }

    @Test
    @DisplayName("Should return String AttributeValueType")
    void testAttributeValueType() {
        // When
        AttributeValueType attributeValueType = instantConverter.attributeValueType();

        // Then
        assertThat(attributeValueType).isEqualTo(AttributeValueType.S);
    }
}
