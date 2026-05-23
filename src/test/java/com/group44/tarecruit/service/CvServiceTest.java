package com.group44.tarecruit.service;

import com.group44.tarecruit.model.ApplicantProfile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CvServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void generatesResumePdfAndStoresAvatar() throws Exception {
        CvService service = new CvService(tempDir.resolve("uploads"));
        Path avatar = tempDir.resolve("avatar.png");
        Files.writeString(avatar, "fake-image");

        CvService.StoredCv storedAvatar = service.storeAvatar(avatar);
        ApplicantProfile profile = new ApplicantProfile(
                "ta-1",
                "Amy Parker",
                "20240001",
                "CS",
                "Year 2",
                "Java, tutoring",
                "Mon/Wed",
                "3.8",
                "",
                "",
                storedAvatar.originalFileName(),
                storedAvatar.storedPath(),
                "2026-05-02T10:00:00"
        );

        CvService.StoredCv generated = service.generateResumePdf(profile);
        assertTrue(Files.exists(Path.of(generated.storedPath())));
        assertTrue(Files.size(Path.of(generated.storedPath())) > 0);
    }

    @Test
    void rejectsUnsupportedAvatarFormat() {
        CvService service = new CvService(tempDir.resolve("uploads"));
        Path avatar = tempDir.resolve("avatar.gif");
        assertThrows(IllegalArgumentException.class, () -> service.storeAvatar(avatar));
    }

    @Test
    void rejectsNonPdfCvFiles() {
        CvService service = new CvService(tempDir.resolve("uploads"));
        Path cv = tempDir.resolve("resume.txt");

        assertThrows(IllegalArgumentException.class, () -> service.storeCv(cv));
    }

    @Test
    void storesPdfCvWithOriginalNameAndUniqueTargetPath() throws Exception {
        CvService service = new CvService(tempDir.resolve("uploads"));
        Path cv = tempDir.resolve("resume.pdf");
        Files.writeString(cv, "%PDF-1.4 fake content");

        CvService.StoredCv first = service.storeCv(cv);
        CvService.StoredCv second = service.storeCv(cv);

        assertEquals("resume.pdf", first.originalFileName());
        assertTrue(Files.exists(Path.of(first.storedPath())));
        assertTrue(Files.exists(Path.of(second.storedPath())));
        assertTrue(!first.storedPath().equals(second.storedPath()));
    }

    @Test
    void wrapsMissingSourceFileAsStorageFailure() {
        CvService service = new CvService(tempDir.resolve("uploads"));
        Path missingCv = tempDir.resolve("missing.pdf");

        assertThrows(IllegalStateException.class, () -> service.storeCv(missingCv));
    }

    @Test
    void generatedResumePaginatesLongProfileContent() throws Exception {
        CvService service = new CvService(tempDir.resolve("uploads"));
        String longSkills = "Java tutoring, lab support, feedback literacy, communication\n".repeat(80);
        ApplicantProfile profile = new ApplicantProfile(
                "ta-1",
                "Amy Parker",
                "20240001",
                "CS",
                "Year 2",
                longSkills,
                "Mon/Wed",
                "3.8",
                "",
                "",
                "",
                "",
                "2026-05-02T10:00:00"
        );

        CvService.StoredCv generated = service.generateResumePdf(profile);

        try (PDDocument document = PDDocument.load(Path.of(generated.storedPath()).toFile())) {
            assertTrue(document.getNumberOfPages() > 1);
        }
    }
}
