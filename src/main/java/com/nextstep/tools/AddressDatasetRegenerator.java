package com.nextstep.tools;

import com.nextstep.domain.site.AddressDetailParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One-off offline tool: reads src/main/resources/data.sql, parses each
 * licensed_business_record row's road_address with AddressDetailParser, and
 * bakes the parsed columns into a regenerated data.sql (and prints a
 * validation report). Not part of the running application.
 */
public final class AddressDatasetRegenerator {

    private static final Path DATA_SQL = Path.of("src/main/resources/data.sql");
    private static final Path BACKUP = Path.of("src/main/resources/data.sql.bak");

    private static final String OLD_COLUMNS =
        "address_corrected, local_gov_code";
    private static final String NEW_COLUMNS =
        "address_corrected, parsed_building_name, parsed_floor, parsed_unit_no, parse_confidence, parse_method, local_gov_code";

    private static final int ROAD_ADDRESS_INDEX = 12;
    private static final int INSERT_AFTER_INDEX = 15; // address_corrected is field #16 (0-indexed 15)

    public static void main(String[] args) throws IOException {
        List<String> lines = Files.readAllLines(DATA_SQL, StandardCharsets.UTF_8);

        List<String> output = new ArrayList<>(lines.size());
        int totalRows = 0;
        Map<String, Integer> confidenceCounts = new LinkedHashMap<>();
        Map<String, Integer> methodCounts = new LinkedHashMap<>();
        List<String> sampleHigh = new ArrayList<>();
        List<String> sampleLow = new ArrayList<>();

        for (String line : lines) {
            if (line.startsWith("INSERT INTO licensed_business_record (")) {
                output.add(line.replace(OLD_COLUMNS, NEW_COLUMNS));
                continue;
            }
            if (!line.startsWith("(")) {
                output.add(line);
                continue;
            }

            String trailer;
            String inner;
            if (line.endsWith("),")) {
                trailer = "),";
                inner = line.substring(1, line.length() - 2);
            } else if (line.endsWith(");")) {
                trailer = ");";
                inner = line.substring(1, line.length() - 2);
            } else {
                throw new IllegalStateException("Unrecognized tuple line ending: " + line);
            }

            List<String> fields = splitTupleRaw(inner);
            String roadAddress = unquoteSqlString(fields.get(ROAD_ADDRESS_INDEX));
            AddressDetailParser.Result result = AddressDetailParser.parse(roadAddress);

            totalRows++;
            confidenceCounts.merge(result.confidence(), 1, Integer::sum);
            methodCounts.merge(result.method(), 1, Integer::sum);

            List<String> rebuilt = new ArrayList<>(fields.size() + 5);
            rebuilt.addAll(fields.subList(0, INSERT_AFTER_INDEX + 1));
            rebuilt.add(toSqlLiteral(result.buildingName()));
            rebuilt.add(toSqlLiteral(result.floor()));
            rebuilt.add(toSqlLiteral(result.unitNo()));
            rebuilt.add(toSqlLiteral(result.confidence()));
            rebuilt.add(toSqlLiteral(result.method()));
            rebuilt.addAll(fields.subList(INSERT_AFTER_INDEX + 1, fields.size()));

            output.add("(" + String.join(", ", rebuilt) + trailer);

            if (AddressDetailParser.CONFIDENCE_HIGH.equals(result.confidence()) && sampleHigh.size() < 15) {
                sampleHigh.add(describe(roadAddress, result));
            }
            if (AddressDetailParser.CONFIDENCE_LOW.equals(result.confidence()) && sampleLow.size() < 15) {
                sampleLow.add(describe(roadAddress, result));
            }
        }

        Files.copy(DATA_SQL, BACKUP, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.write(DATA_SQL, output, StandardCharsets.UTF_8);

        int finalTotalRows = totalRows;
        System.out.println("=== AddressDatasetRegenerator report ===");
        System.out.println("total rows parsed: " + finalTotalRows);
        System.out.println();
        System.out.println("confidence breakdown:");
        confidenceCounts.forEach((k, v) -> System.out.printf("  %-6s %6d  (%.1f%%)%n", k, v, 100.0 * v / finalTotalRows));
        System.out.println();
        System.out.println("method breakdown:");
        methodCounts.forEach((k, v) -> System.out.printf("  %-8s %6d  (%.1f%%)%n", k, v, 100.0 * v / finalTotalRows));
        System.out.println();
        System.out.println("-- HIGH confidence samples --");
        sampleHigh.forEach(System.out::println);
        System.out.println();
        System.out.println("-- LOW confidence samples --");
        sampleLow.forEach(System.out::println);
        System.out.println();
        System.out.println("Backup written to " + BACKUP + ", data.sql regenerated with parsed_* columns.");
    }

    private static String describe(String roadAddress, AddressDetailParser.Result r) {
        return String.format(
            "  road=%s%n    -> building=%s floor=%s unitNo=%s confidence=%s method=%s",
            roadAddress, r.buildingName(), r.floor(), r.unitNo(), r.confidence(), r.method());
    }

    static List<String> splitTupleRaw(String s) {
        List<String> fields = new ArrayList<>();
        int i = 0;
        int n = s.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
            if (i >= n) {
                break;
            }
            int start = i;
            if (s.charAt(i) == '\'') {
                i++;
                while (i < n) {
                    if (s.charAt(i) == '\'') {
                        if (i + 1 < n && s.charAt(i + 1) == '\'') {
                            i += 2;
                            continue;
                        }
                        i++;
                        break;
                    }
                    i++;
                }
            } else {
                while (i < n && s.charAt(i) != ',') {
                    i++;
                }
            }
            fields.add(s.substring(start, i).trim());
            while (i < n && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
            if (i < n && s.charAt(i) == ',') {
                i++;
            }
        }
        return fields;
    }

    static String unquoteSqlString(String rawField) {
        if (rawField == null || !rawField.startsWith("'")) {
            return null;
        }
        String inner = rawField.substring(1, rawField.length() - 1);
        return inner.replace("''", "'");
    }

    static String toSqlLiteral(String value) {
        if (value == null) {
            return "NULL";
        }
        return "'" + value.replace("'", "''") + "'";
    }

    private AddressDatasetRegenerator() {
    }
}
