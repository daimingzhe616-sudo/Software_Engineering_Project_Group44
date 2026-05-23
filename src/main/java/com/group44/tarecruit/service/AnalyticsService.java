package com.group44.tarecruit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group44.tarecruit.data.ApplicationRepository;
import com.group44.tarecruit.data.CsvUtils;
import com.group44.tarecruit.data.JobRepository;
import com.group44.tarecruit.data.ProfileRepository;
import com.group44.tarecruit.data.UserRepository;
import com.group44.tarecruit.model.ActivityLogItem;
import com.group44.tarecruit.model.ApplicantProfile;
import com.group44.tarecruit.model.ApplicationStatus;
import com.group44.tarecruit.model.JobApplication;
import com.group44.tarecruit.model.JobPosting;
import com.group44.tarecruit.model.UserAccount;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class AnalyticsService {
    private static final Set<String> NOISE_WORDS = Set.of(
            "a", "an", "and", "the", "for", "with", "to", "of", "in", "on",
            "can", "be", "is", "are", "hrs", "hour", "hours", "per", "week"
    );

    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final WorkloadService workloadService;
    private final ActivityLogService activityLogService;
    private final LlmJsonService llmJsonService;
    private final ObjectMapper objectMapper;
    private volatile boolean aiFallbackActive;

    public AnalyticsService(
            ApplicationRepository applicationRepository,
            JobRepository jobRepository,
            ProfileRepository profileRepository,
            UserRepository userRepository,
            WorkloadService workloadService,
            ActivityLogService activityLogService
    ) {
        this(
                applicationRepository,
                jobRepository,
                profileRepository,
                userRepository,
                workloadService,
                activityLogService,
                new DisabledLlmJsonService()
        );
    }

    public AnalyticsService(
            ApplicationRepository applicationRepository,
            JobRepository jobRepository,
            ProfileRepository profileRepository,
            UserRepository userRepository,
            WorkloadService workloadService,
            ActivityLogService activityLogService,
            LlmJsonService llmJsonService
    ) {
        this.applicationRepository = applicationRepository;
        this.jobRepository = jobRepository;
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.workloadService = workloadService;
        this.activityLogService = activityLogService;
        this.llmJsonService = llmJsonService;
        this.objectMapper = new ObjectMapper();
        this.aiFallbackActive = llmJsonService instanceof DisabledLlmJsonService;
    }

    public boolean isAiFallbackActive() {
        return aiFallbackActive;
    }

    public List<JobMatchInsight> getJobMatchInsights(String semesterFilter) {
        return getLocalJobMatchInsights(semesterFilter).stream()
                .map(this::enhanceJobMatchInsight)
                .toList();
    }

    public List<JobMatchInsight> getLocalJobMatchInsights(String semesterFilter) {
        String resolvedSemester = resolveSemesterFilter(semesterFilter);
        Map<String, ApplicantProfile> profilesByApplicantId = profilesByApplicantId();
        Map<String, UserAccount> usersById = usersById();

        return jobRepository.findAll().stream()
                .filter(job -> matchesSemester(job.semester(), resolvedSemester))
                .sorted(Comparator.comparing(JobPosting::title))
                .map(job -> buildJobMatchInsight(job, profilesByApplicantId, usersById))
                .toList();
    }

    public List<ApplicantSkillGap> getApplicantSkillGaps(String applicantId, String semesterFilter) {
        return getLocalApplicantSkillGaps(applicantId, semesterFilter).stream()
                .map(gap -> enhanceApplicantSkillGap(gap, profileRepository.findByApplicantId(applicantId)
                        .orElse(new ApplicantProfile(applicantId, "", "", "", "", "", "", "", "", "", "", "", ""))))
                .sorted(Comparator.comparingInt(ApplicantSkillGap::matchScore).reversed()
                        .thenComparing(ApplicantSkillGap::jobTitle))
                .toList();
    }

    public List<ApplicantSkillGap> getLocalApplicantSkillGaps(String applicantId, String semesterFilter) {
        String resolvedSemester = resolveSemesterFilter(semesterFilter);
        ApplicantProfile profile = profileRepository.findByApplicantId(applicantId)
                .orElse(new ApplicantProfile(applicantId, "", "", "", "", "", "", "", "", "", "", "", ""));
        Set<String> appliedJobIds = applicationRepository.findAll().stream()
                .filter(application -> application.applicantId().equals(applicantId))
                .map(JobApplication::jobId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return jobRepository.findAll().stream()
                .filter(job -> matchesSemester(job.semester(), resolvedSemester))
                .sorted(Comparator.comparing(JobPosting::title))
                .map(job -> buildApplicantSkillGap(job, profile, appliedJobIds.contains(job.id())))
                .sorted(Comparator.comparingInt(ApplicantSkillGap::matchScore).reversed()
                        .thenComparing(ApplicantSkillGap::jobTitle))
                .toList();
    }

    public List<WorkloadSuggestion> getWorkloadSuggestions(String semesterFilter) {
        return enhanceWorkloadSuggestions(semesterFilter, resolveSemesterFilter(semesterFilter), getLocalWorkloadSuggestions(semesterFilter));
    }

    public List<WorkloadSuggestion> getLocalWorkloadSuggestions(String semesterFilter) {
        String resolvedSemester = resolveSemesterFilter(semesterFilter);
        Map<String, Integer> currentWorkloads = selectedHoursByApplicant(resolvedSemester);
        Map<String, ApplicantProfile> profilesByApplicantId = profilesByApplicantId();
        Map<String, UserAccount> usersById = usersById();

        return buildBaselineWorkloadSuggestions(
                resolvedSemester,
                currentWorkloads,
                profilesByApplicantId,
                usersById
        );
    }

    public ExportResult exportApplicantList(Path filePath, String semesterFilter, String actorUserId) {
        String resolvedSemester = resolveSemesterFilter(semesterFilter);
        Map<String, UserAccount> usersById = usersById();
        Map<String, ApplicantProfile> profilesByApplicantId = profilesByApplicantId();
        Map<String, JobPosting> jobsById = jobRepository.findAll().stream()
                .collect(Collectors.toMap(JobPosting::id, job -> job, (left, right) -> right, LinkedHashMap::new));
        Map<String, ApplicantMatchInsight> insightsByApplicationId = getLocalJobMatchInsights(semesterFilter).stream()
                .flatMap(insight -> insight.applicants().stream())
                .collect(Collectors.toMap(ApplicantMatchInsight::applicationId, insight -> insight, (left, right) -> right, LinkedHashMap::new));

        List<List<String>> rows = applicationRepository.findAll().stream()
                .filter(application -> jobsById.containsKey(application.jobId()))
                .filter(application -> matchesSemester(jobsById.get(application.jobId()).semester(), resolvedSemester))
                .sorted(Comparator.comparing(JobApplication::appliedAt).reversed())
                .map(application -> {
                    JobPosting job = jobsById.get(application.jobId());
                    UserAccount applicant = usersById.get(application.applicantId());
                    ApplicantProfile profile = profilesByApplicantId.getOrDefault(
                            application.applicantId(),
                            new ApplicantProfile(application.applicantId(), "", "", "", "", "", "", "", "", "", "", "", "")
                    );
                    ApplicantMatchInsight insight = insightsByApplicationId.getOrDefault(
                            application.id(),
                            buildApplicantMatchInsight(job, application, applicant, profile, 0)
                    );
                    return List.of(
                            job.title(),
                            job.moduleCode(),
                            job.semester(),
                            applicant == null ? application.applicantId() : applicant.displayName(),
                            profile.studentId(),
                            application.status().label(),
                            String.valueOf(insight.matchScore()),
                            String.join(" | ", insight.matchedSkills()),
                            String.join(" | ", insight.missingSkills()),
                            String.valueOf(insight.currentWorkloadHours()),
                            insight.explanation(),
                            application.appliedAt(),
                            application.interviewAt(),
                            application.note()
                    );
                })
                .toList();

        CsvUtils.write(filePath, List.of(
                "jobTitle",
                "moduleCode",
                "semester",
                "applicantName",
                "studentId",
                "status",
                "matchScore",
                "matchedSkills",
                "missingSkills",
                "currentWorkloadHours",
                "aiExplanation",
                "appliedAt",
                "interviewAt",
                "note"
        ), rows);

        activityLogService.log(
                "Export",
                actorUserId,
                "",
                "Applicant list exported",
                "Exported " + rows.size() + " applicant row(s) to " + filePath.toAbsolutePath()
        );
        return new ExportResult(filePath.toAbsolutePath().toString(), rows.size());
    }

    public List<ActivityLogItem> getSystemLogs() {
        return activityLogService.getAllLogs();
    }

    private JobMatchInsight buildJobMatchInsight(
            JobPosting job,
            Map<String, ApplicantProfile> profilesByApplicantId,
            Map<String, UserAccount> usersById
    ) {
        Map<String, Integer> currentWorkloads = selectedHoursByApplicant(job.semester());
        List<ApplicantMatchInsight> applicants = applicationRepository.findAll().stream()
                .filter(application -> application.jobId().equals(job.id()))
                .map(application -> {
                    UserAccount user = usersById.get(application.applicantId());
                    ApplicantProfile profile = profilesByApplicantId.getOrDefault(
                            application.applicantId(),
                            new ApplicantProfile(application.applicantId(), "", "", "", "", "", "", "", "", "", "", "", "")
                    );
                    return buildApplicantMatchInsight(job, application, user, profile, currentWorkloads.getOrDefault(application.applicantId(), 0));
                })
                .sorted(Comparator.comparingInt(ApplicantMatchInsight::matchScore).reversed()
                        .thenComparingInt(ApplicantMatchInsight::currentWorkloadHours)
                        .thenComparing(ApplicantMatchInsight::applicantName))
                .toList();

        String summary = applicants.isEmpty()
                ? "No applicants are available for this vacancy yet."
                : applicants.size() + " applicant(s) analysed. Top match score: " + applicants.getFirst().matchScore() + "%.";
        return new JobMatchInsight(
                job.id(),
                job.title(),
                job.moduleCode(),
                job.moduleName(),
                job.semester(),
                job.requiredSkills(),
                applicants,
                summary
        );
    }

    private JobMatchInsight enhanceJobMatchInsight(JobMatchInsight baseline) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("job", Map.of(
                "jobId", baseline.jobId(),
                "jobTitle", baseline.jobTitle(),
                "moduleCode", baseline.moduleCode(),
                "moduleName", baseline.moduleName(),
                "semester", baseline.semester(),
                "requiredSkills", baseline.requiredSkills()
        ));
        payload.put("baselineSummary", baseline.summary());
        payload.put("applicants", baseline.applicants().stream()
                .map(applicant -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("applicationId", applicant.applicationId());
                    map.put("applicantName", applicant.applicantName());
                    map.put("studentId", applicant.studentId());
                    map.put("statusLabel", applicant.statusLabel());
                    map.put("matchScore", applicant.matchScore());
                    map.put("matchedSkills", applicant.matchedSkills());
                    map.put("missingSkills", applicant.missingSkills());
                    map.put("currentWorkloadHours", applicant.currentWorkloadHours());
                    map.put("explanation", applicant.explanation());
                    map.put("note", applicant.note());
                    map.put("interviewAt", applicant.interviewAt());
                    return map;
                })
                .toList());

        Optional<JsonNode> response = completeStructuredPrompt(
                "job-match",
                payload,
                """
                You are an academic recruitment analyst.
                Improve the existing applicant-job skill analysis.
                Return valid JSON only.
                Do not invent applicants, application IDs, or job facts.
                Keep applicationId values exactly as provided.
                You may refine matchScore, matchedSkills, missingSkills, and explanation when the baseline missed nuance.
                """
        );
        if (response.isEmpty()) {
            return baseline;
        }

        JsonNode root = response.get();
        Map<String, JsonNode> updatesByApplicationId = new LinkedHashMap<>();
        if (root.path("applicants").isArray()) {
            for (JsonNode applicantNode : root.path("applicants")) {
                String applicationId = applicantNode.path("applicationId").asText("").trim();
                if (!applicationId.isBlank()) {
                    updatesByApplicationId.put(applicationId, applicantNode);
                }
            }
        }

        List<ApplicantMatchInsight> enhancedApplicants = baseline.applicants().stream()
                .map(applicant -> mergeApplicantInsight(applicant, updatesByApplicationId.get(applicant.applicationId())))
                .toList();
        String summary = textValue(root, "summary").orElse(baseline.summary());
        return new JobMatchInsight(
                baseline.jobId(),
                baseline.jobTitle(),
                baseline.moduleCode(),
                baseline.moduleName(),
                baseline.semester(),
                baseline.requiredSkills(),
                enhancedApplicants,
                summary
        );
    }

    private ApplicantSkillGap buildApplicantSkillGap(JobPosting job, ApplicantProfile profile, boolean alreadyApplied) {
        MatchBreakdown breakdown = analyze(job, profile);
        List<String> suggestions = buildSuggestions(job, breakdown);
        if (!profile.isComplete()) {
            suggestions = new ArrayList<>(suggestions);
            suggestions.addFirst("Complete your profile first so organisers can review your skills and availability.");
        }
        return new ApplicantSkillGap(
                job.id(),
                job.title(),
                job.moduleCode(),
                job.semester(),
                breakdown.score(),
                breakdown.matchedSkills(),
                breakdown.missingSkills(),
                suggestions,
                breakdown.missingSkills().isEmpty() ? "Ready to apply" : "Profile can be strengthened",
                alreadyApplied
        );
    }

    private ApplicantSkillGap enhanceApplicantSkillGap(ApplicantSkillGap baseline, ApplicantProfile profile) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("job", Map.of(
                "jobId", baseline.jobId(),
                "jobTitle", baseline.jobTitle(),
                "moduleCode", baseline.moduleCode(),
                "semester", baseline.semester()
        ));
        Map<String, Object> profileMap = new LinkedHashMap<>();
        profileMap.put("fullName", profile.fullName());
        profileMap.put("programme", profile.programme());
        profileMap.put("year", profile.year());
        profileMap.put("skills", profile.skills());
        profileMap.put("availability", profile.availability());
        profileMap.put("gpa", profile.gpa());
        profileMap.put("profileComplete", profile.isComplete());
        payload.put("profile", profileMap);
        payload.put("baseline", Map.of(
                "matchScore", baseline.matchScore(),
                "matchedSkills", baseline.matchedSkills(),
                "missingSkills", baseline.missingSkills(),
                "suggestions", baseline.suggestions(),
                "readinessLabel", baseline.readinessLabel(),
                "alreadyApplied", baseline.alreadyApplied()
        ));

        Optional<JsonNode> response = completeStructuredPrompt(
                "applicant-gap",
                payload,
                """
                You are a teaching assistant career coach.
                Improve the missing-skill analysis and suggestions for a student applicant.
                Return valid JSON only.
                Do not invent job IDs or profile facts.
                Keep suggestions specific, actionable, and short.
                """
        );
        if (response.isEmpty()) {
            return baseline;
        }

        JsonNode root = response.get();
        List<String> suggestions = stringList(root.path("suggestions"));
        if (!profile.isComplete() && suggestions.stream().noneMatch(text -> text.toLowerCase(Locale.ROOT).contains("complete your profile"))) {
            suggestions = new ArrayList<>(suggestions);
            suggestions.addFirst("Complete your profile first so organisers can review your skills and availability.");
        }

        return new ApplicantSkillGap(
                baseline.jobId(),
                baseline.jobTitle(),
                baseline.moduleCode(),
                baseline.semester(),
                boundedScore(root.path("matchScore"), baseline.matchScore()),
                mergedStringList(root.path("matchedSkills"), baseline.matchedSkills()),
                mergedStringList(root.path("missingSkills"), baseline.missingSkills()),
                suggestions.isEmpty() ? baseline.suggestions() : suggestions,
                textValue(root, "readinessLabel").orElse(baseline.readinessLabel()),
                baseline.alreadyApplied()
        );
    }

    private ApplicantMatchInsight buildApplicantMatchInsight(
            JobPosting job,
            JobApplication application,
            UserAccount applicant,
            ApplicantProfile profile,
            int currentWorkloadHours
    ) {
        MatchBreakdown breakdown = analyze(job, profile);
        String explanation;
        if (breakdown.matchedSkills().isEmpty() && breakdown.missingSkills().isEmpty()) {
            explanation = "No profile skills were available to analyse.";
        } else if (breakdown.missingSkills().isEmpty()) {
            explanation = "All listed requirements are covered by the current profile.";
        } else if (breakdown.matchedSkills().isEmpty()) {
            explanation = "No direct overlap was found with the listed requirements.";
        } else {
            explanation = "Matched " + breakdown.matchedSkills().size() + " requirement(s); "
                    + breakdown.missingSkills().size() + " still need evidence.";
        }

        return new ApplicantMatchInsight(
                application.id(),
                applicant == null ? application.applicantId() : applicant.displayName(),
                profile.studentId(),
                application.status().label(),
                breakdown.score(),
                breakdown.matchedSkills(),
                breakdown.missingSkills(),
                currentWorkloadHours,
                explanation,
                application.note(),
                application.hasInterviewScheduled() ? application.interviewAt() : ""
        );
    }

    private ApplicantMatchInsight mergeApplicantInsight(ApplicantMatchInsight baseline, JsonNode update) {
        if (update == null || update.isMissingNode()) {
            return baseline;
        }
        return new ApplicantMatchInsight(
                baseline.applicationId(),
                baseline.applicantName(),
                baseline.studentId(),
                baseline.statusLabel(),
                boundedScore(update.path("matchScore"), baseline.matchScore()),
                mergedStringList(update.path("matchedSkills"), baseline.matchedSkills()),
                mergedStringList(update.path("missingSkills"), baseline.missingSkills()),
                baseline.currentWorkloadHours(),
                textValue(update, "explanation").orElse(baseline.explanation()),
                baseline.note(),
                baseline.interviewAt()
        );
    }

    private MatchBreakdown analyze(JobPosting job, ApplicantProfile profile) {
        List<String> requiredSkills = splitSkills(job.requiredSkills());
        List<String> applicantSkills = splitApplicantSignals(profile);
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (String requirement : requiredSkills) {
            if (matchesRequirement(requirement, applicantSkills, profile.availability())) {
                matched.add(requirement);
            } else {
                missing.add(requirement);
            }
        }

        int score = requiredSkills.isEmpty()
                ? 0
                : (int) Math.round((matched.size() * 100.0) / requiredSkills.size());
        return new MatchBreakdown(score, matched, missing);
    }

    private List<String> buildSuggestions(JobPosting job, MatchBreakdown breakdown) {
        if (breakdown.missingSkills().isEmpty()) {
            return List.of("You already cover the listed requirements. Tailor your application examples to this role.");
        }
        return breakdown.missingSkills().stream()
                .map(skill -> "Add evidence for \"" + skill + "\" in your profile, CV, or project examples for " + job.moduleCode() + ".")
                .toList();
    }

    private List<WorkloadSuggestion> buildBaselineWorkloadSuggestions(
            String resolvedSemester,
            Map<String, Integer> currentWorkloads,
            Map<String, ApplicantProfile> profilesByApplicantId,
            Map<String, UserAccount> usersById
    ) {
        List<WorkloadSuggestion> suggestions = new ArrayList<>();
        for (WorkloadService.WorkloadSummary summary : workloadService.getWorkload(
                resolvedSemester.isBlank() ? WorkloadService.ALL_SEMESTERS_FILTER : resolvedSemester
        )) {
            if (summary.totalHoursPerWeek() < 10) {
                continue;
            }
            suggestions.add(new WorkloadSuggestion(
                    "High workload",
                    summary.displayName(),
                    summary.totalHoursPerWeek() + " hrs/week in " + summary.semester(),
                    "Review whether one of the selected roles can be reassigned or given backup coverage.",
                    summary.assignments().stream()
                            .map(assignment -> assignment.jobTitle() + " (" + assignment.hoursPerWeek() + " hrs/week)")
                            .toList()
            ));
        }

        for (JobPosting job : jobRepository.findAll()) {
            if (!matchesSemester(job.semester(), resolvedSemester)) {
                continue;
            }
            long selectedCount = applicationRepository.findAll().stream()
                    .filter(application -> application.jobId().equals(job.id()))
                    .filter(application -> application.status() == ApplicationStatus.SELECTED)
                    .count();
            int remainingOpenings = job.openings() - (int) selectedCount;
            if (remainingOpenings <= 0) {
                continue;
            }

            Optional<ApplicantMatchInsight> backupCandidate = applicationRepository.findAll().stream()
                    .filter(application -> application.jobId().equals(job.id()))
                    .filter(application -> application.status() != ApplicationStatus.SELECTED)
                    .filter(application -> application.status() != ApplicationStatus.REJECTED)
                    .filter(application -> application.status() != ApplicationStatus.WITHDRAWN)
                    .map(application -> {
                        UserAccount applicant = usersById.get(application.applicantId());
                        ApplicantProfile profile = profilesByApplicantId.getOrDefault(
                                application.applicantId(),
                                new ApplicantProfile(application.applicantId(), "", "", "", "", "", "", "", "", "", "", "", "")
                        );
                        return buildApplicantMatchInsight(job, application, applicant, profile, currentWorkloads.getOrDefault(application.applicantId(), 0));
                    })
                    .sorted(Comparator.comparingInt(ApplicantMatchInsight::matchScore).reversed()
                            .thenComparingInt(ApplicantMatchInsight::currentWorkloadHours)
                            .thenComparing(ApplicantMatchInsight::applicantName))
                    .findFirst();

            if (backupCandidate.isPresent()) {
                ApplicantMatchInsight candidate = backupCandidate.get();
                suggestions.add(new WorkloadSuggestion(
                        "Unfilled role",
                        job.title(),
                        remainingOpenings + " opening(s) still unfilled for " + job.semester(),
                        "Prioritise " + candidate.applicantName() + " next. They currently hold "
                                + candidate.currentWorkloadHours() + " hrs/week and match "
                                + candidate.matchScore() + "% of the listed skills.",
                        candidate.matchedSkills().isEmpty()
                                ? List.of("No explicit skill overlap was detected. Review the full profile manually.")
                                : candidate.matchedSkills()
                    ));
            } else {
                suggestions.add(new WorkloadSuggestion(
                        "Unfilled role",
                        job.title(),
                        remainingOpenings + " opening(s) still unfilled for " + job.semester(),
                        "No suitable applicant pipeline is available yet. Consider reopening promotion for this role.",
                        List.of("No active applications matched this vacancy.")
                ));
            }
        }
        return suggestions;
    }

    private List<WorkloadSuggestion> enhanceWorkloadSuggestions(
            String originalSemesterFilter,
            String resolvedSemester,
            List<WorkloadSuggestion> baselineSuggestions
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("semesterFilter", originalSemesterFilter);
        payload.put("resolvedSemester", resolvedSemester);
        payload.put("workloadSummaries", workloadService.getWorkload(
                        resolvedSemester.isBlank() ? WorkloadService.ALL_SEMESTERS_FILTER : resolvedSemester)
                .stream()
                .map(summary -> Map.of(
                        "displayName", summary.displayName(),
                        "semester", summary.semester(),
                        "totalHoursPerWeek", summary.totalHoursPerWeek(),
                        "selectedRoles", summary.selectedRoles()
                ))
                .toList());
        payload.put("openRoles", jobRepository.findAll().stream()
                .filter(job -> matchesSemester(job.semester(), resolvedSemester))
                .map(job -> Map.of(
                        "jobId", job.id(),
                        "title", job.title(),
                        "moduleCode", job.moduleCode(),
                        "semester", job.semester(),
                        "hoursPerWeek", job.hoursPerWeek(),
                        "openings", job.openings(),
                        "requiredSkills", job.requiredSkills()
                ))
                .toList());
        payload.put("baselineSuggestions", baselineSuggestions.stream()
                .map(suggestion -> Map.of(
                        "category", suggestion.category(),
                        "subject", suggestion.subject(),
                        "summary", suggestion.summary(),
                        "recommendation", suggestion.recommendation(),
                        "supportPoints", suggestion.supportPoints()
                ))
                .toList());

        Optional<JsonNode> response = completeStructuredPrompt(
                "workload-suggestions",
                payload,
                """
                You are a recruitment planning assistant for a teaching assistant system.
                Produce balanced, explainable workload suggestions for an admin.
                Return valid JSON only.
                Use the baseline suggestions as anchors, but you may refine, prioritise, merge, or rephrase them.
                Do not invent students or roles that are not present in the payload.
                """
        );
        if (response.isEmpty()) {
            return baselineSuggestions;
        }

        List<WorkloadSuggestion> suggestions = parseWorkloadSuggestions(response.get().path("suggestions"));
        return suggestions.isEmpty() ? baselineSuggestions : suggestions;
    }

    private Map<String, Integer> selectedHoursByApplicant(String semesterFilter) {
        Map<String, JobPosting> jobsById = jobRepository.findAll().stream()
                .collect(Collectors.toMap(JobPosting::id, job -> job, (left, right) -> right, LinkedHashMap::new));
        Map<String, Integer> hoursByApplicant = new LinkedHashMap<>();
        for (JobApplication application : applicationRepository.findAll()) {
            if (application.status() != ApplicationStatus.SELECTED) {
                continue;
            }
            JobPosting job = jobsById.get(application.jobId());
            if (job == null || !matchesSemester(job.semester(), semesterFilter)) {
                continue;
            }
            hoursByApplicant.merge(application.applicantId(), parseHours(job.hoursPerWeek()), Integer::sum);
        }
        return hoursByApplicant;
    }

    private boolean matchesRequirement(String requirement, List<String> applicantSignals, String availability) {
        String normalizedRequirement = normalize(requirement);
        if (normalizedRequirement.contains("available") && availability != null && !availability.isBlank()) {
            return true;
        }
        Set<String> requirementTokens = keywords(requirement);
        for (String applicantSignal : applicantSignals) {
            String normalizedApplicantSignal = normalize(applicantSignal);
            if (normalizedApplicantSignal.contains(normalizedRequirement) || normalizedRequirement.contains(normalizedApplicantSignal)) {
                return true;
            }
            Set<String> applicantTokens = keywords(applicantSignal);
            if (!requirementTokens.isEmpty() && overlapScore(requirementTokens, applicantTokens) >= 0.5) {
                return true;
            }
        }
        return false;
    }

    private double overlapScore(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        long shared = left.stream()
                .filter(right::contains)
                .count();
        return shared / (double) left.size();
    }

    private List<String> splitSkills(String value) {
        return List.of((value == null ? "" : value)
                        .replace('|', ';')
                        .replace(',', ';')
                        .split(";"))
                .stream()
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .distinct()
                .toList();
    }

    private List<String> splitApplicantSignals(ApplicantProfile profile) {
        List<String> signals = new ArrayList<>(splitSkills(profile.skills()));
        if (!profile.programme().isBlank()) {
            signals.add(profile.programme());
        }
        if (!profile.availability().isBlank()) {
            signals.add(profile.availability());
        }
        return signals;
    }

    private Set<String> keywords(String value) {
        return List.of(normalize(value).split(" "))
                .stream()
                .map(String::trim)
                .filter(token -> token.length() > 2)
                .filter(token -> !NOISE_WORDS.contains(token))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String resolveSemesterFilter(String semesterFilter) {
        if (semesterFilter == null || semesterFilter.isBlank() || WorkloadService.ALL_SEMESTERS_FILTER.equalsIgnoreCase(semesterFilter)) {
            return "";
        }
        if (WorkloadService.CURRENT_SEMESTER_FILTER.equalsIgnoreCase(semesterFilter)) {
            return workloadService.resolveSemester(semesterFilter);
        }
        return semesterFilter.trim();
    }

    private boolean matchesSemester(String semester, String filter) {
        return filter == null || filter.isBlank() || semester.equalsIgnoreCase(filter.trim());
    }

    private int parseHours(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    private int boundedScore(JsonNode scoreNode, int fallback) {
        if (!scoreNode.canConvertToInt()) {
            return fallback;
        }
        return Math.max(0, Math.min(100, scoreNode.asInt()));
    }

    private Optional<JsonNode> completeStructuredPrompt(String task, Object payload, String systemPrompt) {
        String payloadJson = toJson(payload);
        String userPrompt = """
                Analyze the following recruitment data and return a JSON object only.
                Data:
                %s
                """.formatted(payloadJson);
        try {
            Optional<JsonNode> response = llmJsonService.completeJson(task + "::" + payloadJson, systemPrompt, userPrompt);
            aiFallbackActive = response.isEmpty();
            return response;
        } catch (RuntimeException exception) {
            aiFallbackActive = true;
            return Optional.empty();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize analytics prompt payload.", exception);
        }
    }

    private Optional<String> textValue(JsonNode node, String fieldName) {
        if (node == null) {
            return Optional.empty();
        }
        JsonNode valueNode = node.path(fieldName);
        if (!valueNode.isTextual()) {
            return Optional.empty();
        }
        String value = valueNode.asText().trim();
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private List<String> stringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                values.add(item.asText().trim());
            }
        }
        return values.stream().distinct().toList();
    }

    private List<String> mergedStringList(JsonNode node, List<String> fallback) {
        List<String> values = stringList(node);
        return values.isEmpty() ? fallback : values;
    }

    private List<WorkloadSuggestion> parseWorkloadSuggestions(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<WorkloadSuggestion> suggestions = new ArrayList<>();
        for (JsonNode item : node) {
            String category = textValue(item, "category").orElse("");
            String subject = textValue(item, "subject").orElse("");
            String summary = textValue(item, "summary").orElse("");
            String recommendation = textValue(item, "recommendation").orElse("");
            List<String> supportPoints = stringList(item.path("supportPoints"));
            if (category.isBlank() || subject.isBlank() || summary.isBlank() || recommendation.isBlank()) {
                continue;
            }
            suggestions.add(new WorkloadSuggestion(category, subject, summary, recommendation, supportPoints));
        }
        return suggestions;
    }

    private Map<String, ApplicantProfile> profilesByApplicantId() {
        return profileRepository.findAll().stream()
                .collect(Collectors.toMap(ApplicantProfile::applicantId, profile -> profile, (left, right) -> right, LinkedHashMap::new));
    }

    private Map<String, UserAccount> usersById() {
        return userRepository.findAll().stream()
                .collect(Collectors.toMap(UserAccount::id, user -> user, (left, right) -> right, LinkedHashMap::new));
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public record JobMatchInsight(
            String jobId,
            String jobTitle,
            String moduleCode,
            String moduleName,
            String semester,
            String requiredSkills,
            List<ApplicantMatchInsight> applicants,
            String summary
    ) {
    }

    public record ApplicantMatchInsight(
            String applicationId,
            String applicantName,
            String studentId,
            String statusLabel,
            int matchScore,
            List<String> matchedSkills,
            List<String> missingSkills,
            int currentWorkloadHours,
            String explanation,
            String note,
            String interviewAt
    ) {
    }

    public record ApplicantSkillGap(
            String jobId,
            String jobTitle,
            String moduleCode,
            String semester,
            int matchScore,
            List<String> matchedSkills,
            List<String> missingSkills,
            List<String> suggestions,
            String readinessLabel,
            boolean alreadyApplied
    ) {
    }

    public record WorkloadSuggestion(
            String category,
            String subject,
            String summary,
            String recommendation,
            List<String> supportPoints
    ) {
    }

    public record ExportResult(String path, int rowCount) {
    }

    private record MatchBreakdown(int score, List<String> matchedSkills, List<String> missingSkills) {
    }
}
