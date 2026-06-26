package com.netzero.ingest;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CsvParser {
    public static List<Map<String, String>> parse(InputStream in) {
        var rows = new ArrayList<Map<String, String>>();
        try (var br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String header = br.readLine();
            if (header == null) return rows;
            if (header.startsWith("﻿")) header = header.substring(1); // BOM 제거
            String[] cols = header.split(",", -1);
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] v = line.split(",", -1);
                var m = new LinkedHashMap<String, String>();
                for (int i = 0; i < cols.length; i++) m.put(cols[i].trim(), i < v.length ? v[i].trim() : "");
                rows.add(m);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return rows;
    }
}
