package com.gathin.tvlights;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** The values lumieres.py gives for the same inputs (golden.json), and small helpers to read them. */
final class Golden {
    static final JSONObject DATA = load();

    private Golden() {
    }

    private static JSONObject load() {
        try (InputStream in = Golden.class.getResourceAsStream("/golden.json")) {
            byte[] buf = new byte[1 << 16];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = in.read(buf)) > 0) sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            return new JSONObject(sb.toString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static double[] triple(JSONArray a) throws Exception {
        return new double[]{a.getDouble(0), a.getDouble(1), a.getDouble(2)};
    }

    /** A list of [h, s, v] triples; null for a missing or JSON-null list. */
    static double[][] colors(JSONArray a) throws Exception {
        if (a == null) return null;
        double[][] out = new double[a.length()][];
        for (int i = 0; i < a.length(); i++) out[i] = triple(a.getJSONArray(i));
        return out;
    }
}
