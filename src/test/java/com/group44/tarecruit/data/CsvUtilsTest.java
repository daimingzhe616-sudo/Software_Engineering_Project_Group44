package com.group44.tarecruit.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvUtilsTest {
    @TempDir
    Path tempDir;

    @Test
    void preservesCommaQuoteAndNewlineFields() {
        Path csvPath = tempDir.resolve("edge-cases.csv");
        List<String> header = List.of("id", "content");
        List<List<String>> rows = List.of(
                List.of("comma", "TA role, lab support"),
                List.of("quote", "Applicant said \"ready\""),
                List.of("newline", "First line\nSecond line")
        );

        CsvUtils.write(csvPath, header, rows);

        assertEquals(List.of(header, rows.get(0), rows.get(1), rows.get(2)), CsvUtils.read(csvPath));
    }

    @Test
    void readsMissingAndEmptyFilesAsEmptyRows() throws Exception {
        Path missingPath = tempDir.resolve("missing.csv");
        Path emptyPath = tempDir.resolve("empty.csv");
        Files.writeString(emptyPath, "");

        assertTrue(CsvUtils.read(missingPath).isEmpty());
        assertTrue(CsvUtils.read(emptyPath).isEmpty());
    }

    @Test
    void ignoresBlankLinesWhilePreservingHeaderOnlyFiles() throws Exception {
        Path csvPath = tempDir.resolve("blank-lines.csv");
        Files.writeString(csvPath, "id,name\n\n\n");

        assertEquals(List.of(List.of("id", "name")), CsvUtils.read(csvPath));
    }
}
