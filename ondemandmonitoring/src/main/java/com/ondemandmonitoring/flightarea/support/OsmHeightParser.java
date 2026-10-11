package com.ondemandmonitoring.flightarea.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the OpenStreetMap {@code height} tag. Only plain metre values are accepted
 * ("25", "25.5", "25 m"); feet, ranges and free text yield null. Nothing is estimated here.
 */
public final class OsmHeightParser {

    private static final Pattern METRES =
            Pattern.compile("^\\s*(\\d{1,4}(?:[.,]\\d{1,2})?)\\s*(?:m|meter|meters|metre|metres)?\\s*$",
                    Pattern.CASE_INSENSITIVE);

    private OsmHeightParser() {
    }

    public static Double parseMeters(String raw) {
        if (raw == null) return null;
        Matcher matcher = METRES.matcher(raw);
        if (!matcher.matches()) return null;
        double value = Double.parseDouble(matcher.group(1).replace(',', '.'));
        return value > 0 ? value : null;
    }
}
