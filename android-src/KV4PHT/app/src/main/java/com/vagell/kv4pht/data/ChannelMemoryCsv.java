/*
kv4p HT (see http://kv4p.com)
Copyright (C) 2026 Vance Vagell

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.vagell.kv4pht.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts channel memories to and from CSV, so channels can be backed up and moved between
 * installs.
 * <p>
 * The format follows RFC 4180: fields are comma separated, a field is quoted when it holds a
 * comma, a quote or a line break, and an embedded quote is doubled. Exports carry a header row
 * and imports match columns by that header, so column order does not matter and unknown columns
 * are ignored. A file without a header is read in the order the header would have listed.
 */
public final class ChannelMemoryCsv {
    public static final String NONE = "None";

    private static final String COL_NAME = "name";
    private static final String COL_FREQUENCY = "frequency";
    private static final String COL_OFFSET = "offset";
    private static final String COL_OFFSET_KHZ = "offset_khz";
    private static final String COL_TX_TONE = "tx_tone";
    private static final String COL_RX_TONE = "rx_tone";
    private static final String COL_GROUP = "group";
    private static final String COL_SKIP = "skip_during_scan";

    private static final List<String> COLUMNS = List.of(
        COL_NAME, COL_FREQUENCY, COL_OFFSET, COL_OFFSET_KHZ,
        COL_TX_TONE, COL_RX_TONE, COL_GROUP, COL_SKIP);

    private static final int DEFAULT_OFFSET_KHZ = 600;
    private static final char FIELD_SEPARATOR = ',';
    private static final char QUOTE = '"';

    /** Memories read from a file, plus the number of rows that could not be understood. */
    public static final class ImportResult {
        private final List<ChannelMemory> memories;
        private final int skippedRows;

        ImportResult(List<ChannelMemory> memories, int skippedRows) {
            this.memories = memories;
            this.skippedRows = skippedRows;
        }

        public List<ChannelMemory> getMemories() {
            return memories;
        }

        public int getSkippedRows() {
            return skippedRows;
        }
    }

    private ChannelMemoryCsv() {}

    public static String toCsv(List<ChannelMemory> memories) {
        StringBuilder out = new StringBuilder();
        out.append(String.join(",", COLUMNS)).append("\r\n");
        if (memories != null) {
            for (ChannelMemory memory : memories) {
                appendRow(out, memory);
            }
        }
        return out.toString();
    }

    private static void appendRow(StringBuilder out, ChannelMemory memory) {
        if (memory == null) {
            return;
        }
        out.append(escape(memory.name)).append(FIELD_SEPARATOR)
            .append(escape(memory.frequency)).append(FIELD_SEPARATOR)
            .append(offsetName(memory.offset)).append(FIELD_SEPARATOR)
            .append(memory.offsetKhz).append(FIELD_SEPARATOR)
            .append(escape(toneOrNone(memory.txTone))).append(FIELD_SEPARATOR)
            .append(escape(toneOrNone(memory.rxTone))).append(FIELD_SEPARATOR)
            .append(escape(memory.group)).append(FIELD_SEPARATOR)
            .append(memory.skipDuringScan).append("\r\n");
    }

    public static ImportResult fromCsv(String csv) {
        List<List<String>> rows = parseRows(csv);
        List<ChannelMemory> memories = new ArrayList<>();
        int skipped = 0;
        Map<String, Integer> columns = headerColumns(rows.isEmpty() ? null : rows.get(0));
        int firstDataRow = 0;
        if (columns == null) {
            columns = defaultColumns();
        } else {
            firstDataRow = 1;
        }
        for (int i = firstDataRow; i < rows.size(); i++) {
            ChannelMemory memory = toMemory(rows.get(i), columns);
            if (memory == null) {
                skipped++;
            } else {
                memories.add(memory);
            }
        }
        return new ImportResult(memories, skipped);
    }

    private static ChannelMemory toMemory(List<String> row, Map<String, Integer> columns) {
        String frequency = normalizeFrequency(cell(row, columns, COL_FREQUENCY));
        if (frequency == null) {
            return null;
        }
        ChannelMemory memory = new ChannelMemory();
        memory.frequency = frequency;
        String name = cell(row, columns, COL_NAME);
        memory.name = name.isEmpty() ? frequency : name;
        memory.group = cell(row, columns, COL_GROUP);
        memory.offset = parseOffset(cell(row, columns, COL_OFFSET));
        memory.offsetKhz = parseOffsetKhz(cell(row, columns, COL_OFFSET_KHZ));
        memory.txTone = toneOrNone(cell(row, columns, COL_TX_TONE));
        memory.rxTone = toneOrNone(cell(row, columns, COL_RX_TONE));
        memory.skipDuringScan = parseBoolean(cell(row, columns, COL_SKIP));
        return memory;
    }

    /**
     * Frequencies are stored as "xxx.xxxx" strings, so whatever shape the file uses is
     * normalized to that. Returns null when the value is not a number at all, which is how a
     * row gets reported as skipped.
     */
    private static String normalizeFrequency(String value) {
        if (value.isEmpty()) {
            return null;
        }
        try {
            return String.format(Locale.US, "%.4f", Float.parseFloat(value.replace(',', '.')));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int parseOffset(String value) {
        String offset = value.trim().toLowerCase(Locale.US);
        if (offset.equals("up") || offset.equals("2") || offset.equals("+")) {
            return ChannelMemory.OFFSET_UP;
        }
        if (offset.equals("down") || offset.equals("1") || offset.equals("-")) {
            return ChannelMemory.OFFSET_DOWN;
        }
        return ChannelMemory.OFFSET_NONE;
    }

    private static int parseOffsetKhz(String value) {
        try {
            int khz = Integer.parseInt(value.trim());
            return khz > 0 ? khz : DEFAULT_OFFSET_KHZ;
        } catch (NumberFormatException e) {
            return DEFAULT_OFFSET_KHZ;
        }
    }

    private static boolean parseBoolean(String value) {
        String flag = value.trim().toLowerCase(Locale.US);
        return flag.equals("true") || flag.equals("1") || flag.equals("yes") || flag.equals("y");
    }

    private static String offsetName(int offset) {
        if (offset == ChannelMemory.OFFSET_UP) {
            return "Up";
        }
        if (offset == ChannelMemory.OFFSET_DOWN) {
            return "Down";
        }
        return NONE;
    }

    /** The app stores "None" rather than an empty value when a memory has no tone. */
    private static String toneOrNone(String tone) {
        return (tone == null || tone.trim().isEmpty()) ? NONE : tone.trim();
    }

    private static String cell(List<String> row, Map<String, Integer> columns, String column) {
        Integer index = columns.get(column);
        if (index == null || index < 0 || index >= row.size()) {
            return "";
        }
        String value = row.get(index);
        return value == null ? "" : value.trim();
    }

    /** Returns the column positions named by a header row, or null when this is not a header. */
    private static Map<String, Integer> headerColumns(List<String> row) {
        if (row == null) {
            return null;
        }
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < row.size(); i++) {
            String key = row.get(i) == null ? "" : row.get(i).trim().toLowerCase(Locale.US);
            if (COLUMNS.contains(key)) {
                columns.put(key, i);
            }
        }
        // A genuine header names the two columns that every memory needs.
        boolean isHeader = columns.containsKey(COL_NAME) && columns.containsKey(COL_FREQUENCY);
        return isHeader ? columns : null;
    }

    private static Map<String, Integer> defaultColumns() {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < COLUMNS.size(); i++) {
            columns.put(COLUMNS.get(i), i);
        }
        return columns;
    }

    private static String escape(String value) {
        String text = value == null ? "" : value;
        boolean needsQuotes = text.indexOf(FIELD_SEPARATOR) >= 0
            || text.indexOf(QUOTE) >= 0
            || text.indexOf('\n') >= 0
            || text.indexOf('\r') >= 0;
        if (!needsQuotes) {
            return text;
        }
        return QUOTE + text.replace("\"", "\"\"") + QUOTE;
    }

    /** Splits CSV text into rows of fields, honoring quoted fields that span lines. */
    private static List<List<String>> parseRows(String csv) {
        List<List<String>> rows = new ArrayList<>();
        if (csv == null || csv.isEmpty()) {
            return rows;
        }
        String text = csv.startsWith("﻿") ? csv.substring(1) : csv;
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == QUOTE && i + 1 < text.length() && text.charAt(i + 1) == QUOTE) {
                    field.append(QUOTE);
                    i++;
                } else if (c == QUOTE) {
                    inQuotes = false;
                } else {
                    field.append(c);
                }
            } else if (c == QUOTE) {
                inQuotes = true;
            } else if (c == FIELD_SEPARATOR) {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString());
                field.setLength(0);
                addRow(rows, row);
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
            i++;
        }
        row.add(field.toString());
        addRow(rows, row);
        return rows;
    }

    /** Adds a row unless every field is blank, so blank lines are dropped rather than skipped. */
    private static void addRow(List<List<String>> rows, List<String> row) {
        for (String field : row) {
            if (field != null && !field.trim().isEmpty()) {
                rows.add(row);
                return;
            }
        }
    }
}
