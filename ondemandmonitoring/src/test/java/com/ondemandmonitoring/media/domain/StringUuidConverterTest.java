package com.ondemandmonitoring.media.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class StringUuidConverterTest {

    private final StringUuidConverter converter = new StringUuidConverter();

    @Test
    void convertsApplicationStringIdToUuidAndBack() {
        String id = "3b4dc0b5-30fd-47af-acc7-df058ab28326";

        assertEquals(UUID.fromString(id), converter.convertToDatabaseColumn(id));
        assertEquals(id, converter.convertToEntityAttribute(UUID.fromString(id)));
    }

    @Test
    void preservesNullValues() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }

    @Test
    void rejectsNonUuidMediaIds() {
        assertThrows(IllegalArgumentException.class, () -> converter.convertToDatabaseColumn("not-a-uuid"));
    }
}
