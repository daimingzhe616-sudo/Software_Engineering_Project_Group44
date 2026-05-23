package com.group44.tarecruit.service;

import com.group44.tarecruit.data.ApplicationRepository;
import com.group44.tarecruit.data.JobRepository;
import com.group44.tarecruit.data.NotificationRepository;
import com.group44.tarecruit.data.ProfileRepository;
import com.group44.tarecruit.data.UserRepository;
import com.group44.tarecruit.model.ApplicantProfile;
import com.group44.tarecruit.model.ApplicationStatus;
import com.group44.tarecruit.model.JobApplication;
import com.group44.tarecruit.model.JobPosting;
import com.group44.tarecruit.model.NotificationItem;
import com.group44.tarecruit.model.Role;
import com.group44.tarecruit.model.UserAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void selectingApplicantUpdatesStatusAndCreatesNotification() {
        ApplicationRepository applicationRepository = new ApplicationRepository(tempDir.resolve("applications.csv"));
        JobRepository jobRepository = new JobRepository(tempDir.resolve("jobs.csv"));
        ProfileRepository profileRepository = new ProfileRepository(tempDir.resolve("profiles.csv"));
        UserRepository userRepository = new UserRepository(tempDir.resolve("users.csv"));
        NotificationRepository notificationRepository = new NotificationRepository(tempDir.resolve("notifications.csv"));

        jobRepository.saveAll(List.of(new JobPosting(
                "job-1",
                "Programming TA",
                "CS101",
                "Introduction to Programming",
                "Semester A",
                "8",
                "Java basics, lab support",
                "Java|High demand",
                "Help in labs",
                2
        )));
        userRepository.saveAll(List.of(new UserAccount("ta-1", Role.APPLICANT, "Amy Parker", "amy@school.edu", "password123")));
        profileRepository.saveAll(List.of(new ApplicantProfile(
                "ta-1",
                "Amy Parker",
                "20240001",
                "BSc Computer Science",
                "Year 2",
                "Java, tutoring",
                "Mon/Wed",
                "3.8",
                "amy_cv.txt",
                "cv.txt",
                "",
                "",
                "2026-04-01T10:00:00"
        )));
        applicationRepository.saveAll(List.of(new JobApplication(
                "app-1",
                "job-1",
                "ta-1",
                ApplicationStatus.UNDER_REVIEW,
                "2026-04-01T10:00:00",
                ""
        )));

        ApplicationService service = new ApplicationService(
                applicationRepository,
                jobRepository,
                profileRepository,
                userRepository,
                new NotificationService(notificationRepository)
        );

        service.selectApplicant("app-1");

        assertEquals(ApplicationStatus.SELECTED, applicationRepository.findById("app-1").orElseThrow().status());
        NotificationItem notification = notificationRepository.findAll().getFirst();
        assertEquals("ta-1", notification.userId());
    }

    @Test
    void applyingForJobCreatesApplicationAndNotification() {
        ApplicationRepository applicationRepository = new ApplicationRepository(tempDir.resolve("applications-apply.csv"));
        JobRepository jobRepository = new JobRepository(tempDir.resolve("jobs-apply.csv"));
        ProfileRepository profileRepository = new ProfileRepository(tempDir.resolve("profiles-apply.csv"));
        UserRepository userRepository = new UserRepository(tempDir.resolve("users-apply.csv"));
        NotificationRepository notificationRepository = new NotificationRepository(tempDir.resolve("notifications-apply.csv"));

        jobRepository.saveAll(List.of(new JobPosting(
                "job-1",
                "Programming TA",
                "CS101",
                "Introduction to Programming",
                "Semester A",
                "8",
                "Java basics; lab support",
                "Java|Support",
                "Help in labs",
                2
        )));
        userRepository.saveAll(List.of(new UserAccount("ta-1", Role.APPLICANT, "Amy Parker", "amy@school.edu", "password123")));
        profileRepository.saveAll(List.of(new ApplicantProfile(
                "ta-1",
                "Amy Parker",
                "20240001",
                "BSc Computer Science",
                "Year 2",
                "Java, tutoring",
                "Mon/Wed",
                "3.8",
                "amy_cv.txt",
                "cv.txt",
                "",
                "",
                "2026-04-01T10:00:00"
        )));

        ApplicationService service = new ApplicationService(
                applicationRepository,
                jobRepository,
                profileRepository,
                userRepository,
                new NotificationService(notificationRepository)
        );

        JobApplication application = service.applyForJob("job-1", "ta-1");

        assertEquals(ApplicationStatus.APPLIED, application.status());
        assertTrue(service.hasApplicantApplied("ta-1", "job-1"));
        assertEquals(1, notificationRepository.findAll().size());
    }

    @Test
    void preventsSelectingMoreApplicantsThanOpenings() {
        ApplicationRepository applicationRepository = new ApplicationRepository(tempDir.resolve("applications-2.csv"));
        JobRepository jobRepository = new JobRepository(tempDir.resolve("jobs-2.csv"));
        ProfileRepository profileRepository = new ProfileRepository(tempDir.resolve("profiles-2.csv"));
        UserRepository userRepository = new UserRepository(tempDir.resolve("users-2.csv"));
        NotificationRepository notificationRepository = new NotificationRepository(tempDir.resolve("notifications-2.csv"));

        jobRepository.saveAll(List.of(new JobPosting(
                "job-1",
                "Programming TA",
                "CS101",
                "Introduction to Programming",
                "Semester A",
                "8",
                "Java basics, lab support",
                "Java|High demand",
                "Help in labs",
                1
        )));
        userRepository.saveAll(List.of(
                new UserAccount("ta-1", Role.APPLICANT, "Amy Parker", "amy@school.edu", "password123"),
                new UserAccount("ta-2", Role.APPLICANT, "Bob Chen", "bob@school.edu", "password123")
        ));
        profileRepository.saveAll(List.of(
                new ApplicantProfile("ta-1", "Amy Parker", "20240001", "CS", "Year 2", "Java", "Mon", "3.8", "", "", "", "", ""),
                new ApplicantProfile("ta-2", "Bob Chen", "20240002", "CS", "Year 2", "Java", "Tue", "3.7", "", "", "", "", "")
        ));
        applicationRepository.saveAll(List.of(
                new JobApplication("app-1", "job-1", "ta-1", ApplicationStatus.SELECTED, "2026-04-01T10:00:00", ""),
                new JobApplication("app-2", "job-1", "ta-2", ApplicationStatus.UNDER_REVIEW, "2026-04-01T11:00:00", "")
        ));

        ApplicationService service = new ApplicationService(
                applicationRepository,
                jobRepository,
                profileRepository,
                userRepository,
                new NotificationService(notificationRepository)
        );

        assertThrows(IllegalArgumentException.class, () -> service.selectApplicant("app-2"));
    }

    @Test
    void preventsDuplicateApplication() {
        ApplicationRepository applicationRepository = new ApplicationRepository(tempDir.resolve("applications-duplicate.csv"));
        JobRepository jobRepository = new JobRepository(tempDir.resolve("jobs-duplicate.csv"));
        ProfileRepository profileRepository = new ProfileRepository(tempDir.resolve("profiles-duplicate.csv"));
        UserRepository userRepository = new UserRepository(tempDir.resolve("users-duplicate.csv"));
        NotificationRepository notificationRepository = new NotificationRepository(tempDir.resolve("notifications-duplicate.csv"));

        jobRepository.saveAll(List.of(new JobPosting(
                "job-1",
                "Programming TA",
                "CS101",
                "Introduction to Programming",
                "Semester A",
                "8",
                "Java basics; lab support",
                "Java|Support",
                "Help in labs",
                2
        )));
        userRepository.saveAll(List.of(new UserAccount("ta-1", Role.APPLICANT, "Amy Parker", "amy@school.edu", "password123")));
        profileRepository.saveAll(List.of(new ApplicantProfile(
                "ta-1",
                "Amy Parker",
                "20240001",
                "BSc Computer Science",
                "Year 2",
                "Java, tutoring",
                "Mon/Wed",
                "3.8",
                "",
                "",
                "",
                "",
                "2026-04-01T10:00:00"
        )));
        applicationRepository.saveAll(List.of(new JobApplication(
                "app-1",
                "job-1",
                "ta-1",
                ApplicationStatus.APPLIED,
                "2026-04-01T10:00:00",
                ""
        )));

        ApplicationService service = new ApplicationService(
                applicationRepository,
                jobRepository,
                profileRepository,
                userRepository,
                new NotificationService(notificationRepository)
        );

        assertThrows(IllegalArgumentException.class, () -> service.applyForJob("job-1", "ta-1"));
    }

    @Test
    void applyingRequiresCompletedProfileAndDoesNotCreateSideEffects() {
        ApplicationRepository applicationRepository = new ApplicationRepository(tempDir.resolve("applications-profile-required.csv"));
        JobRepository jobRepository = new JobRepository(tempDir.resolve("jobs-profile-required.csv"));
        ProfileRepository profileRepository = new ProfileRepository(tempDir.resolve("profiles-profile-required.csv"));
        UserRepository userRepository = new UserRepository(tempDir.resolve("users-profile-required.csv"));
        NotificationRepository notificationRepository = new NotificationRepository(tempDir.resolve("notifications-profile-required.csv"));

        jobRepository.saveAll(List.of(new JobPosting(
                "job-1",
                "Programming TA",
                "CS101",
                "Programming",
                "Semester A",
                "8",
                "Java",
                "Java",
                "Support labs",
                2
        )));
        userRepository.saveAll(List.of(
                new UserAccount("ta-missing-profile", Role.APPLICANT, "Missing Profile", "missing@school.edu", "password123"),
                new UserAccount("ta-incomplete", Role.APPLICANT, "Incomplete Profile", "incomplete@school.edu", "password123")
        ));
        profileRepository.saveAll(List.of(new ApplicantProfile(
                "ta-incomplete",
                "Incomplete Profile",
                "",
                "CS",
                "Year 2",
                "Java",
                "Mon",
                "3.8",
                "",
                "",
                "",
                "",
                ""
        )));

        ApplicationService service = new ApplicationService(
                applicationRepository,
                jobRepository,
                profileRepository,
                userRepository,
                new NotificationService(notificationRepository)
        );

        assertThrows(IllegalArgumentException.class, () -> service.applyForJob("job-1", "ta-missing-profile"));
        assertThrows(IllegalArgumentException.class, () -> service.applyForJob("job-1", "ta-incomplete"));
        assertTrue(applicationRepository.findAll().isEmpty());
        assertTrue(notificationRepository.findAll().isEmpty());
    }

    @Test
    void exportsApplicantsForSelectedJobToCsv() throws Exception {
        ApplicationRepository applicationRepository = new ApplicationRepository(tempDir.resolve("applications-export-job.csv"));
        JobRepository jobRepository = new JobRepository(tempDir.resolve("jobs-export-job.csv"));
        ProfileRepository profileRepository = new ProfileRepository(tempDir.resolve("profiles-export-job.csv"));
        UserRepository userRepository = new UserRepository(tempDir.resolve("users-export-job.csv"));
        NotificationRepository notificationRepository = new NotificationRepository(tempDir.resolve("notifications-export-job.csv"));

        jobRepository.saveAll(List.of(new JobPosting(
                "job-1",
                "Academic Writing TA",
                "IS201",
                "English for Academic Purposes",
                "Semester A",
                "4",
                "Writing support, feedback literacy",
                "Writing|Support",
                "Support essays",
                1
        )));
        userRepository.saveAll(List.of(new UserAccount("ta-1", Role.APPLICANT, "Amy Parker", "amy@school.edu", "password123")));
        profileRepository.saveAll(List.of(new ApplicantProfile(
                "ta-1",
                "Amy Parker",
                "20240001",
                "BSc Software Engineering",
                "Year 2",
                "Writing, feedback",
                "Fri AM",
                "3.8",
                "amy_cv.pdf",
                "cv.pdf",
                "",
                "",
                "2026-04-01T10:00:00"
        )));
        applicationRepository.saveAll(List.of(new JobApplication(
                "app-1",
                "job-1",
                "ta-1",
                ApplicationStatus.SHORTLISTED,
                "2026-04-01T10:00:00",
                "Strong writing profile.",
                ""
        )));

        ApplicationService service = new ApplicationService(
                applicationRepository,
                jobRepository,
                profileRepository,
                userRepository,
                new NotificationService(notificationRepository)
        );

        Path output = tempDir.resolve("writing-applicants.csv");
        ApplicationService.ExportResult result = service.exportApplicantsForJob("job-1", output);
        List<String> lines = Files.readAllLines(output);

        assertEquals(1, result.rowCount());
        assertTrue(lines.getFirst().contains("jobTitle,moduleCode,semester,applicantName"));
        assertTrue(lines.get(1).contains("Academic Writing TA,IS201,Semester A,Amy Parker"));
        assertTrue(lines.get(1).contains("Shortlisted"));
    }

    @Test
    void rejectingApplicantUpdatesStatusAndCreatesNotification() {
        ApplicationRepository applicationRepository = new ApplicationRepository(tempDir.resolve("applications-reject.csv"));
        JobRepository jobRepository = new JobRepository(tempDir.resolve("jobs-reject.csv"));
        ProfileRepository profileRepository = new ProfileRepository(tempDir.resolve("profiles-reject.csv"));
        UserRepository userRepository = new UserRepository(tempDir.resolve("users-reject.csv"));
        NotificationRepository notificationRepository = new NotificationRepository(tempDir.resolve("notifications-reject.csv"));

        jobRepository.saveAll(List.of(new JobPosting(
                "job-1",
                "Programming TA",
                "CS101",
                "Introduction to Programming",
                "Semester A",
                "8",
                "Java basics; lab support",
                "Java|Support",
                "Help in labs",
                2
        )));
        userRepository.saveAll(List.of(new UserAccount("ta-1", Role.APPLICANT, "Amy Parker", "amy@school.edu", "password123")));
        profileRepository.saveAll(List.of(new ApplicantProfile("ta-1", "Amy Parker", "20240001", "CS", "Year 2", "Java", "Mon", "3.8", "", "", "", "", "")));
        applicationRepository.saveAll(List.of(new JobApplication(
                "app-1",
                "job-1",
                "ta-1",
                ApplicationStatus.UNDER_REVIEW,
                "2026-04-01T10:00:00",
                ""
        )));

        ApplicationService service = new ApplicationService(
                applicationRepository,
                jobRepository,
                profileRepository,
                userRepository,
                new NotificationService(notificationRepository)
        );

        service.rejectApplicant("app-1", "Stronger lab experience required.");

        JobApplication updated = applicationRepository.findById("app-1").orElseThrow();
        assertEquals(ApplicationStatus.REJECTED, updated.status());
        assertEquals("Stronger lab experience required.", updated.note());
        assertEquals(1, notificationRepository.findAll().size());
        assertThrows(IllegalArgumentException.class, () -> service.rejectApplicant("app-1", "Second rejection"));
    }

    @Test
    void reopensRejectedApplicationForFurtherReview() {
        ApplicationRepository applicationRepository = new ApplicationRepository(tempDir.resolve("applications-reopen.csv"));
        JobRepository jobRepository = new JobRepository(tempDir.resolve("jobs-reopen.csv"));
        ProfileRepository profileRepository = new ProfileRepository(tempDir.resolve("profiles-reopen.csv"));
        UserRepository userRepository = new UserRepository(tempDir.resolve("users-reopen.csv"));
        NotificationRepository notificationRepository = new NotificationRepository(tempDir.resolve("notifications-reopen.csv"));

        jobRepository.saveAll(List.of(new JobPosting(
                "job-1",
                "Programming TA",
                "CS101",
                "Introduction to Programming",
                "Semester A",
                "8",
                "Java basics; lab support",
                "Java|Support",
                "Help in labs",
                2
        )));
        userRepository.saveAll(List.of(new UserAccount("ta-1", Role.APPLICANT, "Amy Parker", "amy@school.edu", "password123")));
        profileRepository.saveAll(List.of(new ApplicantProfile("ta-1", "Amy Parker", "20240001", "CS", "Year 2", "Java", "Mon", "3.8", "", "", "", "", "")));
        applicationRepository.saveAll(List.of(new JobApplication(
                "app-1",
                "job-1",
                "ta-1",
                ApplicationStatus.REJECTED,
                "2026-04-01T10:00:00",
                "Rejected by mistake.",
                ""
        )));

        ApplicationService service = new ApplicationService(
                applicationRepository,
                jobRepository,
                profileRepository,
                userRepository,
                new NotificationService(notificationRepository)
        );

        service.reopenApplication("app-1");

        JobApplication reopened = applicationRepository.findById("app-1").orElseThrow();
        assertEquals(ApplicationStatus.UNDER_REVIEW, reopened.status());
        assertEquals("Application reopened for further review.", reopened.note());
        assertEquals(1, notificationRepository.findAll().size());

        service.shortlistApplicant("app-1", "Review continued.");
        assertEquals(ApplicationStatus.SHORTLISTED, applicationRepository.findById("app-1").orElseThrow().status());
    }

    @Test
    void shortlistPersistsAndSearchIsCaseInsensitive() {
        ApplicationRepository applicationRepository = new ApplicationRepository(tempDir.resolve("applications-shortlist.csv"));
        JobRepository jobRepository = new JobRepository(tempDir.resolve("jobs-shortlist.csv"));
        ProfileRepository profileRepository = new ProfileRepository(tempDir.resolve("profiles-shortlist.csv"));
        UserRepository userRepository = new UserRepository(tempDir.resolve("users-shortlist.csv"));
        NotificationRepository notificationRepository = new NotificationRepository(tempDir.resolve("notifications-shortlist.csv"));

        jobRepository.saveAll(List.of(new JobPosting(
                "job-1",
                "Programming TA",
                "CS101",
                "Introduction to Programming",
                "Semester A",
                "8",
                "Java basics; tutoring",
                "Java|Support",
                "Help in labs",
                2
        )));
        userRepository.saveAll(List.of(
                new UserAccount("ta-1", Role.APPLICANT, "Amy Parker", "amy@school.edu", "password123"),
                new UserAccount("ta-2", Role.APPLICANT, "Bob Chen", "bob@school.edu", "password123")
        ));
        profileRepository.saveAll(List.of(
                new ApplicantProfile("ta-1", "Amy Parker", "20240001", "CS", "Year 2", "Java, tutoring", "Mon", "3.8", "", "", "", "", ""),
                new ApplicantProfile("ta-2", "Bob Chen", "20240002", "CS", "Year 2", "Algorithms", "Tue", "3.7", "", "", "", "", "")
        ));
        applicationRepository.saveAll(List.of(
                new JobApplication("app-1", "job-1", "ta-1", ApplicationStatus.UNDER_REVIEW, "2026-04-01T10:00:00", ""),
                new JobApplication("app-2", "job-1", "ta-2", ApplicationStatus.UNDER_REVIEW, "2026-04-01T11:00:00", "")
        ));

        ApplicationService service = new ApplicationService(
                applicationRepository,
                jobRepository,
                profileRepository,
                userRepository,
                new NotificationService(notificationRepository)
        );

        service.shortlistApplicant("app-1", "Strong tutoring profile.");

        assertEquals(ApplicationStatus.SHORTLISTED, applicationRepository.findById("app-1").orElseThrow().status());
        assertEquals(1, service.findApplicantsForJob("job-1", "", ApplicationStatus.SHORTLISTED).size());
        assertTrue(notificationRepository.findAll().getFirst().message().contains("Please wait for an interview invitation"));
        assertEquals(1, service.findApplicantsForJob("job-1", "java").size());
        assertEquals("Amy Parker", service.findApplicantsForJob("job-1", "AMY").getFirst().applicant().displayName());
        assertThrows(IllegalArgumentException.class, () -> service.shortlistApplicant("app-1", "Again"));
    }

    @Test
    void schedulingInterviewPersistsTimeAndDetectsConflicts() {
        ApplicationRepository applicationRepository = new ApplicationRepository(tempDir.resolve("applications-interview.csv"));
        JobRepository jobRepository = new JobRepository(tempDir.resolve("jobs-interview.csv"));
        ProfileRepository profileRepository = new ProfileRepository(tempDir.resolve("profiles-interview.csv"));
        UserRepository userRepository = new UserRepository(tempDir.resolve("users-interview.csv"));
        NotificationRepository notificationRepository = new NotificationRepository(tempDir.resolve("notifications-interview.csv"));

        jobRepository.saveAll(List.of(
                new JobPosting("job-1", "Programming TA", "CS101", "Programming", "Semester A", "8", "Java", "Java", "Support labs", 2),
                new JobPosting("job-2", "Maths TA", "MA102", "Maths", "Semester A", "6", "Excel", "Excel", "Support workshops", 1)
        ));
        userRepository.saveAll(List.of(new UserAccount("ta-1", Role.APPLICANT, "Amy Parker", "amy@school.edu", "password123")));
        profileRepository.saveAll(List.of(new ApplicantProfile("ta-1", "Amy Parker", "20240001", "CS", "Year 2", "Java", "Mon", "3.8", "", "", "", "", "")));
        applicationRepository.saveAll(List.of(
                new JobApplication("app-1", "job-1", "ta-1", ApplicationStatus.SHORTLISTED, "2026-04-01T10:00:00", ""),
                new JobApplication("app-2", "job-2", "ta-1", ApplicationStatus.UNDER_REVIEW, "2026-04-01T11:00:00", "")
        ));

        ApplicationService service = new ApplicationService(
                applicationRepository,
                jobRepository,
                profileRepository,
                userRepository,
                new NotificationService(notificationRepository)
        );

        service.scheduleInterview("app-1", "2026-05-10T09：00", "Bring portfolio examples.");

        JobApplication updated = applicationRepository.findById("app-1").orElseThrow();
        assertEquals(ApplicationStatus.INTERVIEW_SCHEDULED, updated.status());
        assertEquals("2026-05-10T09:00", updated.interviewAt());
        assertEquals(1, notificationRepository.findAll().size());
        assertThrows(IllegalArgumentException.class,
                () -> service.scheduleInterview("app-2", "2026-05-10T09:00", "Conflict"));
    }

    @Test
    void schedulingInterviewRejectsInvalidDateTimeWithoutChangingApplication() {
        ApplicationRepository applicationRepository = new ApplicationRepository(tempDir.resolve("applications-invalid-interview.csv"));
        JobRepository jobRepository = new JobRepository(tempDir.resolve("jobs-invalid-interview.csv"));
        ProfileRepository profileRepository = new ProfileRepository(tempDir.resolve("profiles-invalid-interview.csv"));
        UserRepository userRepository = new UserRepository(tempDir.resolve("users-invalid-interview.csv"));
        NotificationRepository notificationRepository = new NotificationRepository(tempDir.resolve("notifications-invalid-interview.csv"));

        jobRepository.saveAll(List.of(new JobPosting(
                "job-1",
                "Programming TA",
                "CS101",
                "Programming",
                "Semester A",
                "8",
                "Java",
                "Java",
                "Support labs",
                2
        )));
        userRepository.saveAll(List.of(new UserAccount("ta-1", Role.APPLICANT, "Amy Parker", "amy@school.edu", "password123")));
        profileRepository.saveAll(List.of(new ApplicantProfile("ta-1", "Amy Parker", "20240001", "CS", "Year 2", "Java", "Mon", "3.8", "", "", "", "", "")));
        applicationRepository.saveAll(List.of(new JobApplication(
                "app-1",
                "job-1",
                "ta-1",
                ApplicationStatus.SHORTLISTED,
                "2026-04-01T10:00:00",
                "Ready for scheduling.",
                ""
        )));

        ApplicationService service = new ApplicationService(
                applicationRepository,
                jobRepository,
                profileRepository,
                userRepository,
                new NotificationService(notificationRepository)
        );

        assertThrows(IllegalArgumentException.class, () -> service.scheduleInterview("app-1", "", "Blank time"));
        assertThrows(IllegalArgumentException.class, () -> service.scheduleInterview("app-1", "2026-05-10 09:00", "Missing T"));
        assertThrows(IllegalArgumentException.class, () -> service.scheduleInterview("app-1", "tomorrow", "Natural language"));
        assertThrows(IllegalArgumentException.class, () -> service.scheduleInterview("app-1", "2026-99-99T09:00", "Invalid date"));

        JobApplication unchanged = applicationRepository.findById("app-1").orElseThrow();
        assertEquals(ApplicationStatus.SHORTLISTED, unchanged.status());
        assertEquals("", unchanged.interviewAt());
        assertEquals("Ready for scheduling.", unchanged.note());
        assertTrue(notificationRepository.findAll().isEmpty());
    }

    @Test
    void terminalApplicationStatesBlockFurtherOrganiserUpdates() {
        ApplicationRepository applicationRepository = new ApplicationRepository(tempDir.resolve("applications-terminal-states.csv"));
        JobRepository jobRepository = new JobRepository(tempDir.resolve("jobs-terminal-states.csv"));
        ProfileRepository profileRepository = new ProfileRepository(tempDir.resolve("profiles-terminal-states.csv"));
        UserRepository userRepository = new UserRepository(tempDir.resolve("users-terminal-states.csv"));
        NotificationRepository notificationRepository = new NotificationRepository(tempDir.resolve("notifications-terminal-states.csv"));

        jobRepository.saveAll(List.of(new JobPosting(
                "job-1",
                "Programming TA",
                "CS101",
                "Programming",
                "Semester A",
                "8",
                "Java",
                "Java",
                "Support labs",
                3
        )));
        userRepository.saveAll(List.of(
                new UserAccount("ta-1", Role.APPLICANT, "Amy Parker", "amy@school.edu", "password123"),
                new UserAccount("ta-2", Role.APPLICANT, "Bob Chen", "bob@school.edu", "password123"),
                new UserAccount("ta-3", Role.APPLICANT, "Cara Li", "cara@school.edu", "password123")
        ));
        profileRepository.saveAll(List.of(
                new ApplicantProfile("ta-1", "Amy Parker", "20240001", "CS", "Year 2", "Java", "Mon", "3.8", "", "", "", "", ""),
                new ApplicantProfile("ta-2", "Bob Chen", "20240002", "CS", "Year 2", "Java", "Tue", "3.7", "", "", "", "", ""),
                new ApplicantProfile("ta-3", "Cara Li", "20240003", "CS", "Year 2", "Java", "Wed", "3.6", "", "", "", "", "")
        ));
        applicationRepository.saveAll(List.of(
                new JobApplication("app-selected", "job-1", "ta-1", ApplicationStatus.SELECTED, "2026-04-01T10:00:00", "Selected", ""),
                new JobApplication("app-rejected", "job-1", "ta-2", ApplicationStatus.REJECTED, "2026-04-01T11:00:00", "Rejected", ""),
                new JobApplication("app-withdrawn", "job-1", "ta-3", ApplicationStatus.WITHDRAWN, "2026-04-01T12:00:00", "Withdrawn", "")
        ));

        ApplicationService service = new ApplicationService(
                applicationRepository,
                jobRepository,
                profileRepository,
                userRepository,
                new NotificationService(notificationRepository)
        );

        assertThrows(IllegalArgumentException.class, () -> service.shortlistApplicant("app-selected", "Try again"));
        assertThrows(IllegalArgumentException.class, () -> service.rejectApplicant("app-selected", "Try again"));
        assertThrows(IllegalArgumentException.class, () -> service.scheduleInterview("app-selected", "2026-05-10T09:00", "Try again"));
        assertThrows(IllegalArgumentException.class, () -> service.selectApplicant("app-rejected"));
        assertThrows(IllegalArgumentException.class, () -> service.scheduleInterview("app-rejected", "2026-05-10T09:00", "Try again"));
        assertThrows(IllegalArgumentException.class, () -> service.shortlistApplicant("app-withdrawn", "Try again"));
        assertThrows(IllegalArgumentException.class, () -> service.selectApplicant("app-withdrawn"));

        assertEquals(ApplicationStatus.SELECTED, applicationRepository.findById("app-selected").orElseThrow().status());
        assertEquals(ApplicationStatus.REJECTED, applicationRepository.findById("app-rejected").orElseThrow().status());
        assertEquals(ApplicationStatus.WITHDRAWN, applicationRepository.findById("app-withdrawn").orElseThrow().status());
        assertTrue(notificationRepository.findAll().isEmpty());
    }

    @Test
    void cancelsScheduledInterviewAndNotifiesApplicant() {
        ApplicationRepository applicationRepository = new ApplicationRepository(tempDir.resolve("applications-cancel-interview.csv"));
        JobRepository jobRepository = new JobRepository(tempDir.resolve("jobs-cancel-interview.csv"));
        ProfileRepository profileRepository = new ProfileRepository(tempDir.resolve("profiles-cancel-interview.csv"));
        UserRepository userRepository = new UserRepository(tempDir.resolve("users-cancel-interview.csv"));
        NotificationRepository notificationRepository = new NotificationRepository(tempDir.resolve("notifications-cancel-interview.csv"));

        jobRepository.saveAll(List.of(new JobPosting(
                "job-1",
                "Programming TA",
                "CS101",
                "Programming",
                "Semester A",
                "8",
                "Java",
                "Java",
                "Support labs",
                2
        )));
        userRepository.saveAll(List.of(new UserAccount("ta-1", Role.APPLICANT, "Amy Parker", "amy@school.edu", "password123")));
        profileRepository.saveAll(List.of(new ApplicantProfile("ta-1", "Amy Parker", "20240001", "CS", "Year 2", "Java", "Mon", "3.8", "", "", "", "", "")));
        applicationRepository.saveAll(List.of(new JobApplication(
                "app-1",
                "job-1",
                "ta-1",
                ApplicationStatus.INTERVIEW_SCHEDULED,
                "2026-04-01T10:00:00",
                "Interview scheduled.",
                "2026-05-10T09:00"
        )));

        ApplicationService service = new ApplicationService(
                applicationRepository,
                jobRepository,
                profileRepository,
                userRepository,
                new NotificationService(notificationRepository)
        );

        service.cancelInterview("app-1");

        JobApplication updated = applicationRepository.findById("app-1").orElseThrow();
        assertEquals(ApplicationStatus.UNDER_REVIEW, updated.status());
        assertEquals("", updated.interviewAt());
        assertEquals("Interview cancelled. Your application is back under review.", updated.note());
        assertEquals(1, notificationRepository.findAll().size());
        assertThrows(IllegalArgumentException.class, () -> service.cancelInterview("app-1"));
    }

    @Test
    void withdrawRemovesApplicationUnlessSelected() {
        ApplicationRepository applicationRepository = new ApplicationRepository(tempDir.resolve("applications-withdraw.csv"));
        JobRepository jobRepository = new JobRepository(tempDir.resolve("jobs-withdraw.csv"));
        ProfileRepository profileRepository = new ProfileRepository(tempDir.resolve("profiles-withdraw.csv"));
        UserRepository userRepository = new UserRepository(tempDir.resolve("users-withdraw.csv"));
        NotificationRepository notificationRepository = new NotificationRepository(tempDir.resolve("notifications-withdraw.csv"));

        jobRepository.saveAll(List.of(new JobPosting(
                "job-1",
                "Programming TA",
                "CS101",
                "Programming",
                "Semester A",
                "8",
                "Java",
                "Java",
                "Support labs",
                2
        )));
        userRepository.saveAll(List.of(new UserAccount("ta-1", Role.APPLICANT, "Amy Parker", "amy@school.edu", "password123")));
        profileRepository.saveAll(List.of(new ApplicantProfile("ta-1", "Amy Parker", "20240001", "CS", "Year 2", "Java", "Mon", "3.8", "", "", "", "", "")));
        applicationRepository.saveAll(List.of(
                new JobApplication("app-1", "job-1", "ta-1", ApplicationStatus.APPLIED, "2026-04-01T10:00:00", ""),
                new JobApplication("app-2", "job-1", "ta-1", ApplicationStatus.SELECTED, "2026-04-02T10:00:00", "")
        ));

        ApplicationService service = new ApplicationService(
                applicationRepository,
                jobRepository,
                profileRepository,
                userRepository,
                new NotificationService(notificationRepository)
        );

        service.withdrawApplication("app-1", "ta-1");

        assertFalse(applicationRepository.findById("app-1").isPresent());
        assertTrue(notificationRepository.findAll().stream()
                .anyMatch(item -> item.title().equals("Application withdrawn")));
        assertThrows(IllegalArgumentException.class, () -> service.withdrawApplication("app-2", "ta-1"));
    }

    @Test
    void filtersApplicationsBySemesterAndStatus() {
        ApplicationRepository applicationRepository = new ApplicationRepository(tempDir.resolve("applications-filter.csv"));
        JobRepository jobRepository = new JobRepository(tempDir.resolve("jobs-filter.csv"));
        ProfileRepository profileRepository = new ProfileRepository(tempDir.resolve("profiles-filter.csv"));
        UserRepository userRepository = new UserRepository(tempDir.resolve("users-filter.csv"));
        NotificationRepository notificationRepository = new NotificationRepository(tempDir.resolve("notifications-filter.csv"));

        jobRepository.saveAll(List.of(
                new JobPosting("job-1", "Programming TA", "CS101", "Programming", "Semester A", "8", "Java", "Java", "Support labs", 2),
                new JobPosting("job-2", "Writing TA", "IS201", "Writing", "Semester B", "4", "Writing", "Writing", "Support essays", 1)
        ));
        userRepository.saveAll(List.of(new UserAccount("ta-1", Role.APPLICANT, "Amy Parker", "amy@school.edu", "password123")));
        profileRepository.saveAll(List.of(new ApplicantProfile("ta-1", "Amy Parker", "20240001", "CS", "Year 2", "Java", "Mon", "3.8", "", "", "", "", "")));
        applicationRepository.saveAll(List.of(
                new JobApplication("app-1", "job-1", "ta-1", ApplicationStatus.APPLIED, "2026-04-01T10:00:00", ""),
                new JobApplication("app-2", "job-2", "ta-1", ApplicationStatus.SELECTED, "2026-04-02T10:00:00", "")
        ));

        ApplicationService service = new ApplicationService(
                applicationRepository,
                jobRepository,
                profileRepository,
                userRepository,
                new NotificationService(notificationRepository)
        );

        assertEquals(1, service.findApplicationsForApplicant("ta-1", "Semester B", null).size());
        assertEquals(1, service.findApplicationsForApplicant("ta-1", "", ApplicationStatus.SELECTED).size());
        assertEquals(List.of("Semester A", "Semester B"), service.availableApplicationSemestersForApplicant("ta-1"));
    }
}
