package io.github.bl4z3es.riseofempires.core;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The one text format used for saving records and events. A line is fields joined by '|'. Each
 * field is URL-encoded, so no field can contain a separator or a newline. A list is its
 * elements joined by ',' (each URL-encoded) and then stored as one field.
 */
public final class Wire {
    private Wire() {}

    public static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    public static String dec(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    public static String line(Object... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append('|');
            sb.append(enc(String.valueOf(fields[i])));
        }
        return sb.toString();
    }

    public static String[] fields(String line) {
        String[] raw = line.split("\\|", -1);
        for (int i = 0; i < raw.length; i++) raw[i] = dec(raw[i]);
        return raw;
    }

    public static String list(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(enc(items.get(i)));
        }
        return sb.toString();
    }

    public static List<String> unlist(String s) {
        List<String> out = new ArrayList<>();
        if (s.isEmpty()) return out;
        for (String part : s.split(",", -1)) out.add(dec(part));
        return out;
    }
}
