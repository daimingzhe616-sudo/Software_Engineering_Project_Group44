package com.group44.tarecruit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group44.tarecruit.data.ActivityLogRepository;
import com.group44.tarecruit.data.ApplicationRepository;
import com.group44.tarecruit.data.JobRepository;
import com.group44.tarecruit.data.ProfileRepository;
import com.group44.tarecruit.data.UserRepository;
import com.group44.tarecruit.model.ApplicantProfile;
import com.group44.tarecruit.model.ApplicationStatus;
import com.group44.tarecruit.model.JobApplication;
import com.group44.tarecruit.model.JobPosting;
import com.group44.tarecruit.model.Role;
import com.group44.tarecruit.model.UserAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyticsServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void buildsExplainableSkillMatchesForAdminAndApplicants() {
        AnalyticsService service = buildService();

        List<AnalyticsService.JobMatchInsight> insights = service.getJobMatchInsights("Semester A");
        AnalyticsService.JobMatchInsight programmingInsight = insights.getFirst();

        assertEquals("Programming TA", programmingInsight.jobTitle());
        assertEquals(2, programmingInsight.applicants().size());
        assertEquals("Amy Parker", programmingInsight.applicants().getFirst().applicantName());
        assertTrue(programmingInsight.applicants().getFirst().matchScore() > programmingInsight.applicants().get(1).matchScore());
        assertFalse(programmingInsight.applicants().getFirst().matchedSkills().isEmpty());

        List<AnalyticsService.ApplicantSkillGap> amyGaps = service.getApplicantSkillGaps("ta-1", "Semester A");
        AnalyticsService.ApplicantSkillGap amyProgrammingGap = amyGaps.stream()
                .filter(gap -> gap.jobId().equals("job-programming"))
                .findFirst()
                .orElseThrow();

        assertTrue(amyProgrammingGap.matchScore() >= 50);
        assertTrue(amyProgrammingGap.alreadyApplied());
        assertFalse(amyProgrammingGap.suggestions().isEmpty());
    }

    @Test
    void exportsApplicantAnalyticsAndWritesActivityLog() throws Exception {
        AnalyticsService service = buildService();
        Path exportPath = tempDir.resolve("analytics-export.csv");

        AnalyticsService.ExportResult result = service.exportApplicantList(exportPath, "Semester A", "admin-1");

        assertEquals(3, result.rowCount());
        assertTrue(Files.exists(exportPath));
        String exportedCsv = Files.readString(exportPath);
        assertTrue(exportedCsv.contains("jobTitle,moduleCode,semester,applicantName"));
        assertTrue(exportedCsv.contains("Programming TA"));
        assertTrue(service.getSystemLogs().stream()
                .anyMatch(log -> log.category().equals("Export") && log.message().contains("analytics-export.csv")));
    }

    @Test
    void generatesWorkloadSuggestionsForUnfilledRolesAndHeavyLoads() {
        AnalyticsService service = buildService();

        List<AnalyticsService.WorkloadSuggestion> suggestions = service.getWorkloadSuggestions("Semester A");

        assertTrue(suggestions.stream().anyMatch(item -> item.category().equals("High workload")));
        assertTrue(suggestions.stream().anyMatch(item -> item.category().equals("Unfilled role")));
    }

    @Test
    void llmEnhancementCanRefineExplanationsAndSuggestions() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        LlmJsonService fakeLlm = new LlmJsonService() {
            @Override
            public Optional<JsonNode> completeJson(String cacheKey, String systemPrompt, String userPrompt) {
                try {
                    if (cacheKey.startsWith("job-match::")) {
                        return Optional.of(objectMapper.readTree("""
                                {
                                  "summary": "LLM-enhanced vacancy summary.",
                                  "applicants": [
                                    {
                                      "applicationId": "app-1",
                                      "matchScore": 92,
                                      "matchedSkills": ["Java basics", "lab support"],
                                      "missingSkills": [],
                                      "explanation": "The applicant demonstrates direct evidence for both core requirements."
                                    }
                                  ]
                                }
                                """));
                    }
                    if (cacheKey.startsWith("applicant-gap::")) {
                        return Optional.of(objectMapper.readTree("""
                                {
                                  "matchScore": 88,
                                  "matchedSkills": ["Java basics", "lab support"],
                                  "missingSkills": [],
                                  "suggestions": ["Use one lab-support example in your profile summary."],
                                  "readinessLabel": "Ready to apply"
                                }
                                """));
                    }
                    if (cacheKey.startsWith("workload-suggestions::")) {
                        return Optional.of(objectMapper.readTree("""
                                {
                                  "suggestions": [
                                    {
                                      "category": "Unfilled role",
                                      "subject": "Programming TA",
                                      "summary": "One strong applicant is ready to be prioritised.",
                                      "recommendation": "Advance Amy Parker first because her profile aligns closely with the vacancy.",
                                      "supportPoints": ["92% fit in the latest model review", "Direct lab-support evidence"]
                                    }
                                  ]
                                }
                                """));
                    }
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
                return Optional.empty();
            }
        };

        AnalyticsService service = buildService(fakeLlm);

        AnalyticsService.JobMatchInsight matchInsight = service.getJobMatchInsights("Semester A").getFirst();
        assertEquals("LLM-enhanced vacancy summary.", matchInsight.summary());
        assertEquals(92, matchInsight.applicants().getFirst().matchScore());
        assertTrue(matchInsight.applicants().getFirst().explanation().contains("direct evidence"));

        AnalyticsService.ApplicantSkillGap gap = service.getApplicantSkillGaps("ta-1", "Semester A").getFirst();
        assertEquals("Ready to apply", gap.readinessLabel());
        assertTrue(gap.suggestions().getFirst().contains("lab-support"));

        AnalyticsService.WorkloadSuggestion suggestion = service.getWorkloadSuggestions("Semester A").getFirst();
        assertEquals("Unfilled role", suggestion.category());
        assertTrue(suggestion.recommendation().contains("Amy Parker"));
    }

    @Test
    void keepsLocalAnalyticsWhenLlmReturnsNoData() {
        AnalyticsService service = buildService((cacheKey, systemPrompt, userPrompt) -> Optional.empty());

        AnalyticsService.JobMatchInsight matchInsight = service.getJobMatchInsights("Semester A").getFirst();
        assertEquals("Programming TA", matchInsight.jobTitle());
        assertEquals(2, matchInsight.applicants().size());
        assertTrue(matchInsight.summary().contains("applicant(s) analysed"));

        AnalyticsService.ApplicantSkillGap gap = service.getApplicantSkillGaps("ta-1", "Semester A").getFirst();
        assertFalse(gap.suggestions().isEmpty());

        assertFalse(service.getWorkloadSuggestions("Semester A").isEmpty());
        assertTrue(service.isAiFallbackActive());
    }

    @Test
    void keepsLocalAnalyticsWhenLlmThrows() {
        AnalyticsService service = buildService((cacheKey, systemPrompt, userPrompt) -> {
            throw new RuntimeException("AI service timeout");
        });

        assertFalse(service.getJobMatchInsights("Semester A").isEmpty());
        assertFalse(service.getApplicantSkillGaps("ta-1", "Semester A").isEmpty());
        assertFalse(service.getWorkloadSuggestions("Semester A").isEmpty());
        assertTrue(service.isAiFallbackActive());
    }

    @Test
    void ignoresMalformedLlmShapeAndKeepsBaselineValues() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode malformedResponse = objectMapper.readTree("""
                {
                  "summary": 42,
                  "applicants": [{"applicationId": "app-1", "matchScore": "not-a-number"}],
                  "suggestions": [{"category": 100}]
                }
                """);
        LlmJsonService malformedLlm = (cacheKey, systemPrompt, userPrompt) -> Optional.of(malformedResponse);
        AnalyticsService service = buildService(malformedLlm);

        AnalyticsService.JobMatchInsight matchInsight = service.getJobMatchInsights("Semester A").getFirst();
        AnalyticsService.ApplicantMatchInsight applicant = matchInsight.applicants().getFirst();
        assertEquals("Amy Parker", applicant.applicantName());
        assertTrue(applicant.matchScore() >= 50);
        assertTrue(matchInsight.summary().contains("applicant(s) analysed"));

        AnalyticsService.ApplicantSkillGap gap = service.getApplicantSkillGaps("ta-1", "Semester A").getFirst();
        assertFalse(gap.suggestions().isEmpty());
    }

    private AnalyticsService buildService() {
        return buildService(new DisabledLlmJsonService());
    }

    private AnalyticsService buildService(LlmJsonService llmJsonService) {
        ApplicationRepository applicationRepository = new ApplicationRepository(tempDir.resolve("applications.csv"));
        JobRepository jobRepository = new JobRepository(tempDir.resolve("jobs.csv"));
        ProfileRepository profileRepository = new ProfileRepository(tempDir.resolve("profiles.csv"));
        UserRepository userRepository = new UserRepository(tempDir.resolve("users.csv"));
        ActivityLogRepository activityLogRepository = new ActivityLogRepository(tempDir.resolve("activity_logs.csv"));

        jobRepository.saveAll(List.of(
                new JobPosting(
                        "job-programming",
                        "Programming TA",
                        "CS101",
                        "Programming",
                        "Semester A",
                        "8",
                        "Java basics; lab support",
                        "Java|Labs",
                        "Support weekly labs",
                        2
                ),
                new JobPosting(
                        "job-workshop",
                        "Workshop TA",
                        "CS202",
                        "Advanced Workshop",
                        "Semester A",
                        "10",
                        "Workshop support; communication",
                        "Workshops|Communication",
                        "Run support workshops",
                        1
                )
        ));
        userRepository.saveAll(List.of(
                new UserAccount("ta-1", Role.APPLICANT, "Amy Parker", "amy@school.edu", "password123"),
                new UserAccount("ta-2", Role.APPLICANT, "Bob Chen", "bob@school.edu", "password123"),
                new UserAccount("admin-1", Role.ADMIN, "Cindy Admin", "admin@school.edu", "password123")
        ));
        profileRepository.saveAll(List.of(
                new ApplicantProfile("ta-1", "Amy Parker", "20240001", "CS", "Year 2", "Java basics, lab support", "Mon", "3.8", "", "", "", "", ""),
                new ApplicantProfile("ta-2", "Bob Chen", "20240002", "CS", "Year 3", "Communication", "Tue", "3.7", "", "", "", "", "")
        ));
        applicationRepository.saveAll(List.of(
                new JobApplication("app-1", "job-programming", "ta-1", ApplicationStatus.UNDER_REVIEW, "2026-04-01T10:00:00", ""),
                new JobApplication("app-2", "job-programming", "ta-2", ApplicationStatus.APPLIED, "2026-04-01T11:00:00", ""),
                new JobApplication("app-3", "job-workshop", "ta-2", ApplicationStatus.SELECTED, "2026-04-02T10:00:00", "")
        ));

        ActivityLogService activityLogService = new ActivityLogService(activityLogRepository);
        WorkloadService workloadService = new WorkloadService(applicationRepository, jobRepository, userRepository);
        return new AnalyticsService(
                applicationRepository,
                jobRepository,
                profileRepository,
                userRepository,
                workloadService,
                activityLogService,
                llmJsonService
        );
    }
}
