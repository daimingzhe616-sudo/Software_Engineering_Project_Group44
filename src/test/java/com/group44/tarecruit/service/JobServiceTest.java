package com.group44.tarecruit.service;

import com.group44.tarecruit.data.JobRepository;
import com.group44.tarecruit.model.JobPosting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JobServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void createsJobAndPersistsItToCsv() {
        JobService service = new JobService(new JobRepository(tempDir.resolve("jobs.csv")));

        JobPosting savedJob = service.createJob(new JobPosting(
                "",
                "Programming TA",
                "cs101",
                "Introduction to Programming",
                "Semester A",
                "8",
                "Java basics, lab support",
                "",
                "Support first-year programming labs.",
                2
        ));

        assertEquals("CS101", savedJob.moduleCode());
        assertEquals(1, service.getAllJobs().size());
        assertEquals("Java basics; lab support", service.getAllJobs().getFirst().requiredSkills());
    }

    @Test
    void rejectsMissingRequiredField() {
        JobService service = new JobService(new JobRepository(tempDir.resolve("jobs.csv")));

        assertThrows(IllegalArgumentException.class, () -> service.createJob(new JobPosting(
                "",
                "",
                "CS101",
                "Introduction to Programming",
                "Semester A",
                "8",
                "Java basics",
                "",
                "Support first-year programming labs.",
                2
        )));
    }

    @Test
    void rejectsInvalidHoursOpeningsAndEmptySkills() {
        JobService service = new JobService(new JobRepository(tempDir.resolve("jobs-invalid-numbers.csv")));

        assertThrows(IllegalArgumentException.class, () -> service.createJob(new JobPosting(
                "",
                "Programming TA",
                "CS101",
                "Introduction to Programming",
                "Semester A",
                "abc",
                "Java basics",
                "",
                "Support first-year programming labs.",
                2
        )));
        assertThrows(IllegalArgumentException.class, () -> service.createJob(new JobPosting(
                "",
                "Programming TA",
                "CS101",
                "Introduction to Programming",
                "Semester A",
                "0",
                "Java basics",
                "",
                "Support first-year programming labs.",
                2
        )));
        assertThrows(IllegalArgumentException.class, () -> service.createJob(new JobPosting(
                "",
                "Programming TA",
                "CS101",
                "Introduction to Programming",
                "Semester A",
                "8",
                "Java basics",
                "",
                "Support first-year programming labs.",
                0
        )));
        assertThrows(IllegalArgumentException.class, () -> service.createJob(new JobPosting(
                "",
                "Programming TA",
                "CS101",
                "Introduction to Programming",
                "Semester A",
                "8",
                " , ; | ",
                "",
                "Support first-year programming labs.",
                2
        )));

        assertTrue(service.getAllJobs().isEmpty());
    }

    @Test
    void generatesTagsFromRequiredSkillsWhenTagsAreBlank() {
        JobService service = new JobService(new JobRepository(tempDir.resolve("jobs-generated-tags.csv")));

        JobPosting savedJob = service.createJob(new JobPosting(
                "",
                "Programming TA",
                "CS101",
                "Introduction to Programming",
                "Semester A",
                "8",
                "Java basics; lab support; office hours",
                "   ",
                "Support first-year programming labs.",
                2
        ));

        assertEquals("Java basics|lab support", savedJob.tags());
    }

    @Test
    void filtersJobsByQueryTagAndSemester() {
        JobRepository repository = new JobRepository(tempDir.resolve("jobs-filter.csv"));
        repository.saveAll(List.of(
                new JobPosting("job-1", "Programming TA", "CS101", "Programming", "Semester A", "8", "Java basics", "Java|Labs", "Support labs", 2),
                new JobPosting("job-2", "Maths Support TA", "MA102", "Maths", "Semester A", "6", "Excel basics", "Excel|Core", "Support workshops", 1),
                new JobPosting("job-3", "Writing TA", "IS201", "Writing", "Semester B", "4", "Feedback literacy", "Writing|Support", "Support essays", 1)
        ));
        JobService service = new JobService(repository);

        assertEquals(1, service.filterJobs("programming", "All tags", "All semesters").size());
        assertEquals("Maths Support TA", service.filterJobs("", "Excel", "Semester A").getFirst().title());
        assertTrue(service.filterJobs("python", "All tags", "All semesters").isEmpty());
        assertEquals(List.of("Core", "Excel", "Java", "Labs", "Support", "Writing"), service.availableTags());
        assertEquals(List.of("Semester A", "Semester B"), service.availableSemesters());
    }
}
