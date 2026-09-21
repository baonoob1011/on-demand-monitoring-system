package com.ondemandmonitoring.planning.service.impl;

import java.util.List;

final class CsvFormatter {

    private CsvFormatter() {
    }

    static String csv(List<List<String>> records) {
        StringBuilder csv = new StringBuilder();
        for (List<String> record : records) appendRecord(csv, record);
        return csv.toString();
    }

    static void appendRecord(StringBuilder csv, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) csv.append(',');
            csv.append(escape(values.get(i)));
        }
        csv.append('\n');
    }

    static String value(Double value) {
        return value == null ? null : Double.toString(value);
    }

    static String value(Long value) {
        return value == null ? null : Long.toString(value);
    }

    static String value(Integer value) {
        return value == null ? null : Integer.toString(value);
    }

    static String value(Boolean value) {
        return value == null ? null : Boolean.toString(value);
    }

    private static String escape(String value) {
        if (value == null) return "";
        boolean quoted = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!quoted) return value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
