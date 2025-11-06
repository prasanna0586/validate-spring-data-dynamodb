package org.example.dynamodb.converter;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;

import java.time.Instant;

public class InstantConverter implements DynamoDBTypeConverter<String, Instant> {

    @Override
    public String convert(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    @Override
    public Instant unconvert(String s) {
        return s != null ? Instant.parse(s) : null;
    }
}
