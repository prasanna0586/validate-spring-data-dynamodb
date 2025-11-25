package org.example.dynamodb.converter;

import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;

public class InstantConverter implements AttributeConverter<Instant> {

    @Override
    public AttributeValue transformFrom(Instant instant) {
        if (instant == null) {
            return AttributeValue.builder().nul(true).build();
        }
        return AttributeValue.builder().s(instant.toString()).build();
    }

    @Override
    public Instant transformTo(AttributeValue attributeValue) {
        if (attributeValue == null || attributeValue.s() == null || attributeValue.nul() != null && attributeValue.nul()) {
            return null;
        }
        return Instant.parse(attributeValue.s());
    }

    @Override
    public EnhancedType<Instant> type() {
        return EnhancedType.of(Instant.class);
    }

    @Override
    public AttributeValueType attributeValueType() {
        return AttributeValueType.S;
    }
}
