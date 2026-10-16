package com.ondemandmonitoring.media.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.UUID;

/** Maps UUID identifiers represented as strings in the application to PostgreSQL UUID values. */
@Converter
public class StringUuidConverter implements AttributeConverter<String, UUID> {

    @Override
    public UUID convertToDatabaseColumn(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    @Override
    public String convertToEntityAttribute(UUID value) {
        return value == null ? null : value.toString();
    }
}
