package com.group44.tarecruit.service;

import com.group44.tarecruit.data.ApplicationRepository;
import com.group44.tarecruit.data.CsvUtils;
import com.group44.tarecruit.data.JobRepository;
import com.group44.tarecruit.data.ProfileRepository;
import com.group44.tarecruit.data.UserRepository;
import com.group44.tarecruit.model.ApplicantProfile;
import com.group44.tarecruit.model.ApplicationStatus;
import com.group44.tarecruit.model.JobApplication;
import com.group44.tarecruit.model.JobPosting;
import com.group44.tarecruit.model.UserAccount;

import java.nio.file.Path;
import java.time.format.DateTimeParseException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ApplicationService {
    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public ApplicationService(
            ApplicationRepository applicationRepository,
            JobRepository jobRepository,
            ProfileRepository profileRepository,
            UserRepository userRepository,
            NotificationService notificationService
    ) {
        this.applicationRepository = applicationRepository;
        this.jobRepository = jobRepository;
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    public List<JobApplication> findApplicationsForApplicant(String applicantId) {
        return findApplicationsForApplicant(applicantId, "", null);
    }

    public List<JobApplication> findApplicationsForApplicant(String applicantId, String semesterFilter, ApplicationStatus statusFilter) {
        return applicationRepository.findAll().stream()
                .filter(application -> application.applicantId().equals(applicantId))
                .filter(application -> matchesApplicationFilters(application, semesterFilter, statusFilter))
                .sorted(Comparator.comparing(JobApplication::appliedAt).reversed())
                .toList();
    }

    public List<ApplicantReviewItem> findApplicantsForJob(String jobId) {
        return findApplicantsForJob(jobId, "");
    }

    public List<ApplicantReviewItem> findApplicantsForJob(String jobId, String searchQuery) {
        return findApplicantsForJob(jobId, searchQuery, null);
    }

    public List<ApplicantReviewItem> findApplicantsForJob(String jobId, String searchQuery, ApplicationStatus statusFilter) {
        JobPosting job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found."));
        String normalizedQuery = normalize(searchQuery);
        return applicationRepository.findAll().stream()
                .filter(application -> application.jobId().equals(jobId))
                .filter(application -> statusFilter == null || application.status() == statusFilter)
                .map(application -> buildReviewItem(job, application))
                .filter(item -> matchesApplicantQuery(item, normalizedQuery))
                .sorted(Comparator.comparingInt(ApplicantReviewItem::fitScore).reversed())
                .toList();
    }

    public JobApplication applyForJob(String jobId, String applicantId) {
        JobPosting job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found."));
        ApplicantProfile profile = profileRepository.findByApplicantId(applicantId)
                .orElseThrow(() -> new IllegalArgumentException("Please complete your profile before applying."));
        if (!profile.isComplete()) {
            throw new IllegalArgumentException("Please complete your profile before applying.");
        }

        boolean duplicate = applicationRepository.findAll().stream()
                .anyMatch(existing -> existing.jobId().equals(jobId) && existing.applicantId().equals(applicantId));
        if (duplicate) {
            throw new IllegalArgumentException("You have already applied for this role.");
        }

        JobApplication application = new JobApplication(
                UUID.randomUUID().toString(),
                jobId,
                applicantId,
                ApplicationStatus.APPLIED,
                LocalDateTime.now().toString(),
                "Application received and queued for organiser review.",
                ""
        );
        applicationRepository.upsert(application);
        notificationService.notifyUser(
                applicantId,
                "Application submitted",
                "Your application for " + job.title() + " has been recorded successfully."
        );
        return application;
    }

    public void selectApplicant(String applicationId) {
        JobApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));
        if (application.status() == ApplicationStatus.SELECTED) {
            throw new IllegalArgumentException("This applicant has already been selected.");
        }

        JobPosting job = jobRepository.findById(application.jobId()).orElseThrow();
        if (selectedCountForJob(job.id()) >= job.openings()) {
            throw new IllegalArgumentException("All available openings for this job have already been filled.");
        }
        if (application.status() == ApplicationStatus.REJECTED) {
            throw new IllegalArgumentException("Rejected applicants cannot be selected.");
        }
        if (application.status() == ApplicationStatus.WITHDRAWN) {
            throw new IllegalArgumentException("Withdrawn applications cannot be selected.");
        }

        applicationRepository.upsert(updateApplication(
                application,
                ApplicationStatus.SELECTED,
                resolveNote("", "You have been selected for this role."),
                application.interviewAt()
        ));

        notificationService.notifyUser(
                application.applicantId(),
                "Selection result",
                "You have been selected for " + job.title() + "."
        );
    }

    public void shortlistApplicant(String applicationId, String organiserNote) {
        JobApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));
        if (application.status() == ApplicationStatus.SHORTLISTED) {
            throw new IllegalArgumentException("This applicant has already been shortlisted.");
        }
        ensureActionAllowed(application);
        JobPosting job = jobRepository.findById(application.jobId()).orElseThrow();
        applicationRepository.upsert(updateApplication(
                application,
                ApplicationStatus.SHORTLISTED,
                resolveNote(organiserNote, "Shortlisted for the next review step."),
                ""
        ));
        notificationService.notifyUser(
                application.applicantId(),
                "Application shortlisted",
                "You have been shortlisted for " + job.title() + ". Please wait for an interview invitation or further review."
        );
    }

    public void rejectApplicant(String applicationId, String organiserNote) {
        JobApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));
        if (application.status() == ApplicationStatus.REJECTED) {
            throw new IllegalArgumentException("This applicant has already been rejected.");
        }
        ensureActionAllowed(application);
        JobPosting job = jobRepository.findById(application.jobId()).orElseThrow();
        applicationRepository.upsert(updateApplication(
                application,
                ApplicationStatus.REJECTED,
                resolveNote(organiserNote, "Thank you for applying. This application was not selected."),
                ""
        ));
        notificationService.notifyUser(
                application.applicantId(),
                "Application update",
            "Your application for " + job.title() + " was not successful."
        );
    }

    public void reopenApplication(String applicationId) {
        JobApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));
        if (application.status() != ApplicationStatus.REJECTED) {
            throw new IllegalArgumentException("Only rejected applications can be reopened.");
        }
        JobPosting job = jobRepository.findById(application.jobId()).orElseThrow();
        applicationRepository.upsert(updateApplication(
                application,
                ApplicationStatus.UNDER_REVIEW,
                "Application reopened for further review.",
                ""
        ));
        notificationService.notifyUser(
                application.applicantId(),
                "Application reopened",
                "Your application for " + job.title() + " has been reopened for further review."
        );
    }

    public void scheduleInterview(String applicationId, String requestedInterviewAt, String organiserNote) {
        JobApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));
        ensureActionAllowed(application);
        String interviewAt = parseInterviewAt(requestedInterviewAt);
        boolean conflict = applicationRepository.findAll().stream()
                .filter(existing -> !existing.id().equals(application.id()))
                .filter(existing -> existing.applicantId().equals(application.applicantId()))
                .filter(existing -> existing.status() == ApplicationStatus.INTERVIEW_SCHEDULED)
                .anyMatch(existing -> interviewAt.equals(existing.interviewAt()));
        if (conflict) {
            throw new IllegalArgumentException("This applicant already has an interview scheduled at that time.");
        }

        JobPosting job = jobRepository.findById(application.jobId()).orElseThrow();
        applicationRepository.upsert(updateApplication(
                application,
                ApplicationStatus.INTERVIEW_SCHEDULED,
                resolveNote(organiserNote, "Interview scheduled. Please attend on time."),
                interviewAt
        ));
        notificationService.notifyUser(
                application.applicantId(),
                "Interview scheduled",
            "Your interview for " + job.title() + " is scheduled for " + formatDateTime(interviewAt) + "."
        );
    }

    public void cancelInterview(String applicationId) {
        JobApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));
        if (application.status() != ApplicationStatus.INTERVIEW_SCHEDULED) {
            throw new IllegalArgumentException("Only scheduled interviews can be cancelled.");
        }
        JobPosting job = jobRepository.findById(application.jobId()).orElseThrow();
        applicationRepository.upsert(updateApplication(
                application,
                ApplicationStatus.UNDER_REVIEW,
                "Interview cancelled. Your application is back under review.",
                ""
        ));
        notificationService.notifyUser(
                application.applicantId(),
                "Interview cancelled",
                "Your interview for " + job.title() + " has been cancelled. Your application is back under review."
        );
    }

    public void withdrawApplication(String applicationId, String applicantId) {
        JobApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));
        if (!application.applicantId().equals(applicantId)) {
            throw new IllegalArgumentException("You can only withdraw your own application.");
        }
        if (application.status() == ApplicationStatus.SELECTED) {
            throw new IllegalArgumentException("Selected applications cannot be withdrawn.");
        }
        applicationRepository.deleteById(applicationId);

        jobRepository.findById(application.jobId()).ifPresent(job -> notificationService.notifyUser(
                applicantId,
                "Application withdrawn",
                "Your application for " + job.title() + " has been withdrawn."
        ));
    }

    public List<JobPosting> jobsWithApplicants() {
        return jobRepository.findAll().stream()
                .sorted(Comparator.comparing(JobPosting::title))
                .toList();
    }

    public Optional<JobPosting> findJobForApplication(JobApplication application) {
        return jobRepository.findById(application.jobId());
    }

    public Optional<JobApplication> findApplicationForApplicantAndJob(String applicantId, String jobId) {
        return applicationRepository.findAll().stream()
                .filter(application -> application.applicantId().equals(applicantId))
                .filter(application -> application.jobId().equals(jobId))
                .findFirst();
    }

    public boolean hasApplicantApplied(String applicantId, String jobId) {
        return findApplicationForApplicantAndJob(applicantId, jobId).isPresent();
    }

    public long selectedCountForJob(String jobId) {
        return applicationRepository.findAll().stream()
                .filter(application -> application.jobId().equals(jobId))
                .filter(application -> application.status() == ApplicationStatus.SELECTED)
                .count();
    }

    public ExportResult exportApplicantsForJob(String jobId, Path filePath) {
        JobPosting job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found."));
        List<List<String>> rows = findApplicantsForJob(jobId, "", null).stream()
                .map(item -> List.of(
                        job.title(),
                        job.moduleCode(),
                        job.semester(),
                        item.applicant().displayName(),
                        item.applicant().email(),
                        item.profile().studentId(),
                        item.profile().programme(),
                        item.profile().year(),
                        item.profile().skills(),
                        item.profile().availability(),
                        item.profile().gpa(),
                        item.application().status().label(),
                        item.application().appliedAt(),
                        item.application().interviewAt(),
                        item.application().note()
                ))
                .toList();
        CsvUtils.write(filePath, List.of(
                "jobTitle",
                "moduleCode",
                "semester",
                "applicantName",
                "email",
                "studentId",
                "programme",
                "year",
                "skills",
                "availability",
                "gpa",
                "status",
                "appliedAt",
                "interviewAt",
                "note"
        ), rows);
        return new ExportResult(filePath.toAbsolutePath().toString(), rows.size());
    }

    public List<String> availableApplicationSemestersForApplicant(String applicantId) {
        return findApplicationsForApplicant(applicantId).stream()
                .map(this::findJobForApplication)
                .flatMap(Optional::stream)
                .map(JobPosting::semester)
                .filter(semester -> !semester.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private ApplicantReviewItem buildReviewItem(JobPosting job, JobApplication application) {
        UserAccount applicant = userRepository.findAll().stream()
                .filter(user -> user.id().equals(application.applicantId()))
                .findFirst()
                .orElseThrow();
        ApplicantProfile profile = profileRepository.findByApplicantId(application.applicantId())
                .orElse(new ApplicantProfile(
                        application.applicantId(),
                        applicant.displayName(),
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        ""
                ));

        int fitScore = calculateFitScore(job.requiredSkills(), profile.skills());
        return new ApplicantReviewItem(application, applicant, profile, fitScore);
    }

    private boolean matchesApplicationFilters(JobApplication application, String semesterFilter, ApplicationStatus statusFilter) {
        if (statusFilter != null && application.status() != statusFilter) {
            return false;
        }
        if (semesterFilter == null || semesterFilter.isBlank() || "All semesters".equalsIgnoreCase(semesterFilter)) {
            return true;
        }
        return findJobForApplication(application)
                .map(JobPosting::semester)
                .map(semester -> semester.equalsIgnoreCase(semesterFilter.trim()))
                .orElse(false);
    }

    private boolean matchesApplicantQuery(ApplicantReviewItem item, String normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            return true;
        }
        String combined = String.join(" ",
                item.applicant().displayName(),
                item.profile().fullName(),
                item.profile().studentId(),
                item.profile().programme(),
                item.profile().skills(),
                item.profile().availability(),
                item.application().status().label()
        ).toLowerCase();
        return combined.contains(normalizedQuery);
    }

    private int calculateFitScore(String requiredSkills, String applicantSkills) {
        if (requiredSkills.isBlank() || applicantSkills.isBlank()) {
            return 0;
        }
        List<String> requiredTokens = splitSkills(requiredSkills);
        List<String> applicantTokens = splitSkills(applicantSkills);
        long matched = requiredTokens.stream()
                .filter(token -> applicantTokens.stream().anyMatch(skill -> skill.contains(token) || token.contains(skill)))
                .count();
        return (int) Math.round((matched * 100.0) / requiredTokens.size());
    }

    private List<String> splitSkills(String value) {
        return List.of(value.toLowerCase()
                        .replace(';', ',')
                        .split(","))
                .stream()
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .toList();
    }

    private JobApplication updateApplication(
            JobApplication source,
            ApplicationStatus status,
            String note,
            String interviewAt
    ) {
        return new JobApplication(
                source.id(),
                source.jobId(),
                source.applicantId(),
                status,
                source.appliedAt(),
                note,
                interviewAt
        );
    }

    private void ensureActionAllowed(JobApplication application) {
        if (application.status() == ApplicationStatus.SELECTED) {
            throw new IllegalArgumentException("Selected applicants cannot be updated again.");
        }
        if (application.status() == ApplicationStatus.REJECTED) {
            throw new IllegalArgumentException("Rejected applicants cannot be updated again.");
        }
        if (application.status() == ApplicationStatus.WITHDRAWN) {
            throw new IllegalArgumentException("Withdrawn applications cannot be updated again.");
        }
    }

    private String resolveNote(String requestedNote, String fallbackNote) {
        String normalizedRequested = normalize(requestedNote);
        if (!normalizedRequested.isBlank()) {
            return requestedNote.trim();
        }
        return fallbackNote;
    }

    private String parseInterviewAt(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Interview time is required.");
        }
        try {
            return LocalDateTime.parse(value.trim().replace('：', ':')).toString();
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Interview time must use the format yyyy-MM-ddTHH:mm, for example 2026-05-24T15:00.");
        }
    }

    private String formatDateTime(String value) {
        try {
            return LocalDateTime.parse(value).toString().replace('T', ' ');
        } catch (DateTimeParseException exception) {
            return value;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    public record ApplicantReviewItem(
            JobApplication application,
            UserAccount applicant,
            ApplicantProfile profile,
            int fitScore
    ) {
    }

    public record ExportResult(String path, int rowCount) {
    }
}
