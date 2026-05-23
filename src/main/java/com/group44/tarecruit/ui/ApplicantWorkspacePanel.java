package com.group44.tarecruit.ui;

import com.group44.tarecruit.model.ApplicantProfile;
import com.group44.tarecruit.model.ApplicationStatus;
import com.group44.tarecruit.model.JobApplication;
import com.group44.tarecruit.model.JobPosting;
import com.group44.tarecruit.model.NotificationItem;
import com.group44.tarecruit.model.UserAccount;
import com.group44.tarecruit.service.ApplicationService;
import com.group44.tarecruit.service.AnalyticsService;
import com.group44.tarecruit.service.CvService;
import com.group44.tarecruit.service.JobService;
import com.group44.tarecruit.service.NotificationService;
import com.group44.tarecruit.service.ProfileService;
import com.group44.tarecruit.service.SavedJobService;
import com.group44.tarecruit.service.WorkloadService;
import com.group44.tarecruit.ui.components.Theme;
import com.group44.tarecruit.ui.components.UiFactory;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ApplicantWorkspacePanel extends JPanel {
    private static final String DASHBOARD_PAGE = "dashboard";
    private static final String PROFILE_PAGE = "profile";
    private static final String JOBS_PAGE = "jobs";
    private static final String APPLICATIONS_PAGE = "applications";
    private static final String INSIGHTS_PAGE = "insights";
    private static final String ALL_TAGS = "All tags";
    private static final String ALL_SEMESTERS = "All semesters";
    private static final String ALL_STATUSES = "All statuses";

    private final ProfileService profileService;
    private final JobService jobService;
    private final ApplicationService applicationService;
    private final NotificationService notificationService;
    private final AnalyticsService analyticsService;
    private final SavedJobService savedJobService;
    private final CvService cvService;
    private final Runnable accountAction;
    private final Runnable logoutAction;

    private final CardLayout pageLayout;
    private final JPanel pagePanel;
    private final JPanel dashboardNotifications;
    private final JPanel dashboardJobs;
    private final JPanel dashboardInsights;
    private final JLabel dashboardGreeting;
    private final JLabel dashboardProfileStatus;
    private final JTextField fullNameField;
    private final JTextField studentIdField;
    private final JTextField programmeField;
    private final JTextField yearField;
    private final JTextField skillsField;
    private final JTextField availabilityField;
    private final JTextField gpaField;
    private final JLabel fullNameValidationLabel;
    private final JLabel studentIdValidationLabel;
    private final JLabel programmeValidationLabel;
    private final JLabel yearValidationLabel;
    private final JLabel availabilityValidationLabel;
    private final JLabel gpaValidationLabel;
    private final JLabel skillsValidationLabel;
    private final JLabel cvStatusLabel;
    private final JLabel avatarStatusLabel;
    private final JLabel resumeStatusLabel;
    private final JLabel profileTimestampLabel;
    private final JTextField jobSearchField;
    private final JComboBox<String> jobTagFilterBox;
    private final JComboBox<String> jobSemesterFilterBox;
    private final JPanel jobsListPanel;
    private final JLabel detailTitleLabel;
    private final JLabel detailSummaryLabel;
    private final JLabel detailApplicationStatusLabel;
    private final JLabel detailSkillInsightLabel;
    private final JTextArea detailRequirementsArea;
    private final JTextArea detailDescriptionArea;
    private final JButton applyButton;
    private final JButton saveForLaterButton;
    private final JComboBox<String> applicationSemesterFilterBox;
    private final JPanel applicationStatusFilterPanel;
    private final JPanel applicationsListPanel;
    private final JComboBox<String> skillInsightSemesterFilterBox;
    private final JPanel skillInsightsListPanel;

    private UserAccount currentUser;
    private JobPosting selectedJob;
    private String currentCvOriginalFileName = "";
    private String currentCvStoredPath = "";
    private String currentAvatarOriginalFileName = "";
    private String currentAvatarStoredPath = "";
    private Map<String, JobApplication> applicationsByJobId = new LinkedHashMap<>();
    private boolean suppressFilterRefresh;
    private boolean profileValidationSubmitted;

    public ApplicantWorkspacePanel(
            ProfileService profileService,
            JobService jobService,
            ApplicationService applicationService,
            NotificationService notificationService,
            AnalyticsService analyticsService,
            SavedJobService savedJobService,
            CvService cvService,
            Runnable accountAction,
            Runnable logoutAction
    ) {
        this.profileService = profileService;
        this.jobService = jobService;
        this.applicationService = applicationService;
        this.notificationService = notificationService;
        this.analyticsService = analyticsService;
        this.savedJobService = savedJobService;
        this.cvService = cvService;
        this.accountAction = accountAction;
        this.logoutAction = logoutAction;

        setLayout(new BorderLayout());
        setBackground(Theme.APP_BACKGROUND);

        add(buildSidebar(), BorderLayout.WEST);

        pageLayout = new CardLayout();
        pagePanel = new JPanel(pageLayout);
        pagePanel.setOpaque(false);

        dashboardGreeting = UiFactory.sectionLabel("");
        dashboardProfileStatus = UiFactory.mutedLabel("");
        dashboardNotifications = new JPanel();
        dashboardNotifications.setLayout(new BoxLayout(dashboardNotifications, BoxLayout.Y_AXIS));
        dashboardNotifications.setOpaque(false);
        dashboardJobs = new JPanel();
        dashboardJobs.setLayout(new BoxLayout(dashboardJobs, BoxLayout.Y_AXIS));
        dashboardJobs.setOpaque(false);
        dashboardInsights = new JPanel();
        dashboardInsights.setLayout(new BoxLayout(dashboardInsights, BoxLayout.Y_AXIS));
        dashboardInsights.setOpaque(false);

        fullNameField = UiFactory.textField();
        studentIdField = UiFactory.numericTextField(12);
        programmeField = UiFactory.textField();
        yearField = UiFactory.textField();
        skillsField = UiFactory.textField();
        availabilityField = UiFactory.textField();
        gpaField = UiFactory.textField();
        fullNameValidationLabel = UiFactory.validationLabel();
        studentIdValidationLabel = UiFactory.validationLabel();
        programmeValidationLabel = UiFactory.validationLabel();
        yearValidationLabel = UiFactory.validationLabel();
        availabilityValidationLabel = UiFactory.validationLabel();
        gpaValidationLabel = UiFactory.validationLabel();
        skillsValidationLabel = UiFactory.validationLabel();
        cvStatusLabel = UiFactory.mutedLabel("No CV uploaded yet.");
        avatarStatusLabel = UiFactory.mutedLabel("No avatar uploaded yet.");
        resumeStatusLabel = UiFactory.mutedLabel("No PDF resume generated yet.");
        profileTimestampLabel = UiFactory.mutedLabel("");

        jobSearchField = UiFactory.textField();
        jobTagFilterBox = new JComboBox<>();
        jobTagFilterBox.setFont(Theme.BODY_FONT);
        jobSemesterFilterBox = new JComboBox<>();
        jobSemesterFilterBox.setFont(Theme.BODY_FONT);

        jobsListPanel = new JPanel();
        jobsListPanel.setOpaque(false);
        jobsListPanel.setLayout(new BoxLayout(jobsListPanel, BoxLayout.Y_AXIS));
        detailTitleLabel = UiFactory.sectionLabel("Job Detail");
        detailSummaryLabel = UiFactory.mutedLabel("Select a role to review its details.");
        detailApplicationStatusLabel = UiFactory.mutedLabel("Apply to see this role appear in your application tracker.");
        detailSkillInsightLabel = UiFactory.mutedLabel("Match analysis appears here after a role is selected.");
        detailRequirementsArea = readOnlyArea();
        detailDescriptionArea = readOnlyArea();
        applyButton = UiFactory.primaryButton("Apply now");
        applyButton.addActionListener(event -> applyForSelectedJob());
        saveForLaterButton = UiFactory.lightButton("Save for later");
        saveForLaterButton.addActionListener(event -> toggleSavedJob());

        applicationSemesterFilterBox = new JComboBox<>();
        applicationSemesterFilterBox.setFont(Theme.BODY_FONT);
        applicationStatusFilterPanel = UiFactory.flowPanel(FlowLayout.LEFT, 10, 0);
        applicationsListPanel = new JPanel();
        applicationsListPanel.setOpaque(false);
        applicationsListPanel.setLayout(new BoxLayout(applicationsListPanel, BoxLayout.Y_AXIS));
        skillInsightSemesterFilterBox = new JComboBox<>();
        skillInsightSemesterFilterBox.setFont(Theme.BODY_FONT);
        skillInsightsListPanel = new JPanel();
        skillInsightsListPanel.setOpaque(false);
        skillInsightsListPanel.setLayout(new BoxLayout(skillInsightsListPanel, BoxLayout.Y_AXIS));

        attachRefreshOnChange(jobSearchField, this::refreshJobs);
        jobTagFilterBox.addActionListener(event -> {
            if (!suppressFilterRefresh) {
                refreshJobs();
            }
        });
        jobSemesterFilterBox.addActionListener(event -> {
            if (!suppressFilterRefresh) {
                refreshJobs();
            }
        });
        applicationSemesterFilterBox.addActionListener(event -> {
            if (!suppressFilterRefresh) {
                refreshApplications();
            }
        });
        skillInsightSemesterFilterBox.addActionListener(event -> {
            if (!suppressFilterRefresh) {
                refreshSkillInsights();
            }
        });
        attachProfileValidation();

        pagePanel.add(UiFactory.scrollPane(buildDashboardPage()), DASHBOARD_PAGE);
        pagePanel.add(buildProfilePage(), PROFILE_PAGE);
        pagePanel.add(buildJobsPage(), JOBS_PAGE);
        pagePanel.add(UiFactory.scrollPane(buildApplicationsPage()), APPLICATIONS_PAGE);
        pagePanel.add(UiFactory.scrollPane(buildSkillInsightsPage()), INSIGHTS_PAGE);
        add(pagePanel, BorderLayout.CENTER);
    }

    public void setCurrentUser(UserAccount currentUser) {
        this.currentUser = currentUser;
    }

    public void refreshAll() {
        refreshProfile();
        refreshJobs();
        refreshDashboard();
        refreshApplications();
        refreshSkillInsights();
        showPage(DASHBOARD_PAGE);
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(Theme.SURFACE);
        sidebar.setBorder(BorderFactory.createEmptyBorder(16, 14, 16, 14));
        sidebar.setPreferredSize(new Dimension(176, 620));

        JLabel brand = UiFactory.sectionLabel("TA Recruit");
        sidebar.add(brand);
        sidebar.add(Box.createVerticalStrut(28));

        sidebar.add(navButton("Dashboard", DASHBOARD_PAGE));
        sidebar.add(Box.createVerticalStrut(12));
        sidebar.add(navButton("My Profile", PROFILE_PAGE));
        sidebar.add(Box.createVerticalStrut(12));
        sidebar.add(navButton("Jobs", JOBS_PAGE));
        sidebar.add(Box.createVerticalStrut(12));
        sidebar.add(navButton("Applications", APPLICATIONS_PAGE));
        sidebar.add(Box.createVerticalStrut(12));
        sidebar.add(navButton("Skill Insights", INSIGHTS_PAGE));
        sidebar.add(Box.createVerticalStrut(12));
        JButton accountButton = UiFactory.navButton("Account");
        accountButton.addActionListener(event -> accountAction.run());
        sidebar.add(accountButton);
        sidebar.add(Box.createVerticalGlue());

        JButton logoutButton = UiFactory.lightButton("Sign out");
        logoutButton.addActionListener(event -> logoutAction.run());
        sidebar.add(logoutButton);
        return sidebar;
    }

    private JButton navButton(String label, String page) {
        JButton button = UiFactory.navButton(label);
        button.addActionListener(event -> showPage(page));
        return button;
    }

    private JPanel buildDashboardPage() {
        JPanel page = pageWrapper();
        page.add(UiFactory.titleLabel("Dashboard"));
        page.add(Box.createVerticalStrut(12));
        page.add(dashboardGreeting);
        page.add(Box.createVerticalStrut(4));
        page.add(dashboardProfileStatus);
        page.add(Box.createVerticalStrut(24));

        JPanel jobsCard = UiFactory.card();
        jobsCard.add(sectionWithBody("Open teaching assistant vacancies", scrollBody(dashboardJobs)), BorderLayout.CENTER);
        JPanel notificationCard = UiFactory.card();
        notificationCard.add(sectionWithBody("Latest updates", scrollBody(dashboardNotifications)), BorderLayout.CENTER);
        JPanel insightCard = UiFactory.card();
        insightCard.add(sectionWithBody("Profile improvement focus", scrollBody(dashboardInsights)), BorderLayout.CENTER);

        page.add(jobsCard);
        page.add(Box.createVerticalStrut(12));
        page.add(notificationCard);
        page.add(Box.createVerticalStrut(12));
        page.add(insightCard);
        return page;
    }

    private JPanel buildProfilePage() {
        JPanel page = new JPanel(new BorderLayout(0, 16));
        page.setOpaque(false);
        page.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(UiFactory.titleLabel("Applicant Profile"));
        header.add(Box.createVerticalStrut(6));
        header.add(UiFactory.mutedLabel("Create or update your reusable profile. Required fields must be completed before saving."));
        page.add(header, BorderLayout.NORTH);

        JPanel card = UiFactory.card();
        JPanel grid = new JPanel();
        grid.setOpaque(false);
        grid.setLayout(new BoxLayout(grid, BoxLayout.Y_AXIS));
        grid.add(profileRow(
                labeledField("Full name", fullNameField, fullNameValidationLabel),
                labeledField("Student ID", studentIdField, studentIdValidationLabel)
        ));
        grid.add(Box.createVerticalStrut(10));
        grid.add(profileRow(
                labeledField("Programme", programmeField, programmeValidationLabel),
                labeledField("Year", yearField, yearValidationLabel)
        ));
        grid.add(Box.createVerticalStrut(10));
        grid.add(profileRow(
                labeledField("Availability", availabilityField, availabilityValidationLabel),
                labeledField("GPA", gpaField, gpaValidationLabel)
        ));
        grid.add(Box.createVerticalStrut(10));
        grid.add(profileRow(
                labeledField("Skills", skillsField, skillsValidationLabel),
                buildCvCard()
        ));

        card.add(grid, BorderLayout.CENTER);
        page.add(card, BorderLayout.CENTER);

        JPanel actionRow = UiFactory.flowPanel(java.awt.FlowLayout.LEFT, 12, 0);
        JButton saveButton = UiFactory.primaryButton("Save profile");
        saveButton.addActionListener(event -> saveProfile());
        actionRow.add(saveButton);
        actionRow.add(profileTimestampLabel);
        page.add(actionRow, BorderLayout.SOUTH);
        return page;
    }

    private JPanel profileRow(Component left, Component right) {
        JPanel row = new JPanel(new GridLayout(1, 2, 12, 0));
        row.setOpaque(false);
        row.add(left);
        row.add(right);
        return row;
    }

    private JPanel buildCvCard() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(UiFactory.bodyLabel("Resume & Photo"));
        panel.add(Box.createVerticalStrut(8));
        panel.add(avatarStatusLabel);
        panel.add(Box.createVerticalStrut(6));
        panel.add(cvStatusLabel);
        panel.add(Box.createVerticalStrut(6));
        panel.add(resumeStatusLabel);
        panel.add(Box.createVerticalStrut(12));
        JPanel buttonRow = new JPanel(new GridLayout(0, 2, 8, 8));
        buttonRow.setOpaque(false);
        JButton uploadButton = UiFactory.secondaryButton("Upload PDF CV");
        uploadButton.addActionListener(event -> uploadCv());
        JButton uploadPhotoButton = UiFactory.lightButton("Upload Photo");
        uploadPhotoButton.addActionListener(event -> uploadAvatar());
        JButton generateButton = UiFactory.primaryButton("Generate PDF");
        generateButton.addActionListener(event -> generateResumePdf());
        JButton openButton = UiFactory.lightButton("Open Resume");
        openButton.addActionListener(event -> openGeneratedResume());
        buttonRow.add(uploadButton);
        buttonRow.add(uploadPhotoButton);
        buttonRow.add(generateButton);
        buttonRow.add(openButton);
        panel.add(buttonRow);
        return panel;
    }

    private JPanel buildJobsPage() {
        JPanel page = new JPanel(new BorderLayout(0, 14));
        page.setOpaque(false);
        page.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(UiFactory.titleLabel("Available Jobs"));
        top.add(Box.createVerticalStrut(6));
        top.add(UiFactory.mutedLabel("Browse the current vacancies and review full job requirements before applying."));
        top.add(Box.createVerticalStrut(12));
        top.add(buildJobFiltersCard());
        page.add(top, BorderLayout.NORTH);

        JPanel layout = new JPanel(new GridLayout(1, 2, 10, 0));
        layout.setOpaque(false);
        JPanel listCard = UiFactory.card();
        JScrollPane jobsScrollPane = UiFactory.scrollPane(jobsListPanel);
        listCard.add(jobsScrollPane, BorderLayout.CENTER);
        JPanel detailCard = buildJobDetailCard();
        layout.add(listCard);
        layout.add(detailCard);
        page.add(layout, BorderLayout.CENTER);
        return page;
    }

    private JPanel buildJobFiltersCard() {
        JPanel card = UiFactory.card();
        JPanel content = new JPanel(new GridLayout(1, 4, 10, 0));
        content.setOpaque(false);
        content.add(labeledField("Search", jobSearchField));
        content.add(labeledField("Skill tag", jobTagFilterBox));
        content.add(labeledField("Semester", jobSemesterFilterBox));

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        actions.add(UiFactory.bodyLabel("Actions"));
        actions.add(Box.createVerticalStrut(8));
        JButton clearButton = UiFactory.lightButton("Reset filters");
        clearButton.addActionListener(event -> resetJobFilters());
        actions.add(clearButton);
        content.add(actions);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildJobDetailCard() {
        JPanel detailCard = UiFactory.card();
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(detailTitleLabel);
        content.add(Box.createVerticalStrut(8));
        content.add(detailSummaryLabel);
        content.add(Box.createVerticalStrut(8));
        content.add(detailApplicationStatusLabel);
        content.add(Box.createVerticalStrut(8));
        content.add(detailSkillInsightLabel);
        content.add(Box.createVerticalStrut(16));
        content.add(UiFactory.bodyLabel("Requirements"));
        content.add(Box.createVerticalStrut(8));
        content.add(detailRequirementsArea);
        content.add(Box.createVerticalStrut(14));
        content.add(UiFactory.bodyLabel("Description"));
        content.add(Box.createVerticalStrut(8));
        content.add(detailDescriptionArea);
        content.add(Box.createVerticalStrut(18));

        JPanel actions = new JPanel(new GridLayout(1, 2, 8, 0));
        actions.setOpaque(false);
        actions.add(applyButton);
        actions.add(saveForLaterButton);
        content.add(actions);

        detailCard.add(content, BorderLayout.CENTER);
        return detailCard;
    }

    private JPanel buildApplicationsPage() {
        JPanel page = pageWrapper();
        page.add(UiFactory.titleLabel("My Applications"));
        page.add(Box.createVerticalStrut(8));
        page.add(UiFactory.mutedLabel("Any status changes made by the organiser are reflected here after you reload or log back in."));
        page.add(Box.createVerticalStrut(18));
        page.add(buildApplicationFiltersCard());
        page.add(Box.createVerticalStrut(24));
        page.add(applicationsListPanel);
        return page;
    }

    private JPanel buildSkillInsightsPage() {
        JPanel page = pageWrapper();
        page.add(UiFactory.titleLabel("Skill Insights"));
        page.add(Box.createVerticalStrut(8));
        page.add(UiFactory.mutedLabel("See which requirements you already cover, what is still missing, and how to strengthen your profile before applying."));
        page.add(Box.createVerticalStrut(18));

        JPanel filterCard = UiFactory.card();
        JPanel topRow = UiFactory.flowPanel(FlowLayout.LEFT, 14, 0);
        topRow.add(UiFactory.bodyLabel("Semester"));
        topRow.add(skillInsightSemesterFilterBox);
        JButton clearButton = UiFactory.lightButton("Show all");
        clearButton.addActionListener(event -> resetSkillInsightFilters());
        topRow.add(clearButton);
        JButton refreshButton = UiFactory.secondaryButton("Refresh insights");
        refreshButton.addActionListener(event -> refreshSkillInsights());
        topRow.add(refreshButton);
        filterCard.add(topRow, BorderLayout.CENTER);
        page.add(filterCard);
        page.add(Box.createVerticalStrut(24));
        page.add(skillInsightsListPanel);
        return page;
    }

    private JPanel buildApplicationFiltersCard() {
        JPanel card = UiFactory.card();
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        JPanel topRow = UiFactory.flowPanel(FlowLayout.LEFT, 14, 0);
        topRow.add(UiFactory.bodyLabel("Semester"));
        topRow.add(applicationSemesterFilterBox);
        JButton clearButton = UiFactory.lightButton("Show all");
        clearButton.addActionListener(event -> resetApplicationFilters());
        topRow.add(clearButton);
        content.add(topRow);
        content.add(Box.createVerticalStrut(14));
        content.add(UiFactory.bodyLabel("Status"));
        content.add(Box.createVerticalStrut(10));
        content.add(applicationStatusFilterPanel);
        card.add(content, BorderLayout.CENTER);
        buildApplicationStatusButtons();
        return card;
    }

    private JPanel pageWrapper() {
        JPanel page = new JPanel();
        page.setOpaque(false);
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
        page.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        return page;
    }

    private JPanel sectionWithBody(String title, Component contentBody) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(UiFactory.sectionLabel(title));
        panel.add(Box.createVerticalStrut(16));
        panel.add(contentBody);
        return panel;
    }

    private JScrollPane scrollBody(Component component) {
        JScrollPane scrollPane = UiFactory.scrollPane(component);
        scrollPane.setPreferredSize(new Dimension(320, 220));
        return scrollPane;
    }

    private JPanel labeledField(String labelText, Component field) {
        return labeledField(labelText, field, null);
    }

    private JPanel labeledField(String labelText, Component field, JLabel validationLabel) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(UiFactory.bodyLabel(labelText));
        panel.add(Box.createVerticalStrut(8));
        if (field instanceof JComponent component
                && !(field instanceof JPanel)
                && !(field instanceof JScrollPane)
                && !(field instanceof JTextArea)) {
            UiFactory.fixedHeight(component, 36);
        }
        panel.add(field);
        if (validationLabel != null) {
            panel.add(Box.createVerticalStrut(4));
            panel.add(validationLabel);
        }
        return panel;
    }

    private JTextArea readOnlyArea() {
        JTextArea area = UiFactory.textArea(4);
        area.setEditable(false);
        area.setBackground(Theme.SURFACE_MUTED);
        area.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        UiFactory.fixedHeight(area, 112);
        return area;
    }

    private void buildApplicationStatusButtons() {
        applicationStatusFilterPanel.removeAll();
        for (String label : List.of(
                ALL_STATUSES,
                ApplicationStatus.APPLIED.label(),
                ApplicationStatus.UNDER_REVIEW.label(),
                ApplicationStatus.SHORTLISTED.label(),
                ApplicationStatus.INTERVIEW_SCHEDULED.label(),
                ApplicationStatus.SELECTED.label(),
                ApplicationStatus.REJECTED.label()
        )) {
            JButton button = label.equals(ALL_STATUSES)
                    ? UiFactory.secondaryButton(label)
                    : UiFactory.lightButton(label);
            button.addActionListener(event -> {
                setSelectedApplicationStatus(label);
                refreshApplications();
            });
            applicationStatusFilterPanel.add(button);
        }
        applicationStatusFilterPanel.revalidate();
        applicationStatusFilterPanel.repaint();
    }

    private void setSelectedApplicationStatus(String selectedStatus) {
        for (Component component : applicationStatusFilterPanel.getComponents()) {
            if (!(component instanceof JButton button)) {
                continue;
            }
            boolean selected = button.getText().equalsIgnoreCase(selectedStatus);
            button.setBackground(selected ? Theme.ACCENT : Theme.SURFACE_MUTED);
            button.setForeground(selected ? Color.WHITE : Theme.TEXT);
        }
    }

    private void attachRefreshOnChange(JTextField field, Runnable action) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                action.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                action.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                action.run();
            }
        });
    }

    private void refreshDashboard() {
        dashboardGreeting.setText("Welcome back, " + currentUser.displayName());
        Optional<ApplicantProfile> profile = profileService.findProfile(currentUser.id());
        dashboardProfileStatus.setText(profile.filter(ApplicantProfile::isComplete).isPresent()
                ? "Your profile is complete and ready for applications."
                : "Your profile is incomplete. Finish it before applying for a new role.");

        refreshDashboardJobs();
        rebuildVerticalList(dashboardNotifications, notificationService.getNotificationsForUser(currentUser.id()).stream()
                .map(this::notificationCard)
                .toList(), "No updates yet.");
        rebuildVerticalList(dashboardInsights, analyticsService.getLocalApplicantSkillGaps(currentUser.id(), WorkloadService.ALL_SEMESTERS_FILTER).stream()
                .limit(3)
                .map(this::dashboardSkillInsightCard)
                .toList(), "No skill insights are available yet.");
    }

    private void refreshDashboardJobs() {
        dashboardJobs.removeAll();
        List<JobPosting> savedJobs = savedJobService.findSavedJobPostings(currentUser.id());
        if (!savedJobs.isEmpty()) {
            dashboardJobs.add(UiFactory.bodyLabel("Saved for later"));
            dashboardJobs.add(Box.createVerticalStrut(8));
            for (JobPosting savedJob : savedJobs) {
                dashboardJobs.add(dashboardSavedJobCard(savedJob));
                dashboardJobs.add(Box.createVerticalStrut(10));
            }
            dashboardJobs.add(Box.createVerticalStrut(8));
        }

        dashboardJobs.add(UiFactory.bodyLabel("Open vacancies"));
        dashboardJobs.add(Box.createVerticalStrut(8));
        List<Component> jobCards = jobService.getAllJobs().stream()
                .map(this::dashboardJobCard)
                .toList();
        if (jobCards.isEmpty()) {
            dashboardJobs.add(UiFactory.mutedLabel("No jobs are available right now."));
        } else {
            for (int index = 0; index < jobCards.size(); index++) {
                dashboardJobs.add(jobCards.get(index));
                if (index < jobCards.size() - 1) {
                    dashboardJobs.add(Box.createVerticalStrut(12));
                }
            }
        }
        dashboardJobs.revalidate();
        dashboardJobs.repaint();
    }

    private Component dashboardSavedJobCard(JobPosting job) {
        JPanel card = UiFactory.card();
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 154));
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        JLabel title = UiFactory.bodyLabel(job.title());
        title.setFont(Theme.BUTTON_FONT);
        content.add(title);
        content.add(Box.createVerticalStrut(4));
        content.add(UiFactory.mutedLabel("<html>" + job.summaryLine() + "</html>"));
        content.add(Box.createVerticalStrut(10));
        JPanel row = UiFactory.flowPanel(java.awt.FlowLayout.LEFT, 10, 0);
        JButton viewButton = UiFactory.lightButton("View detail");
        viewButton.addActionListener(event -> {
            selectedJob = job;
            updateJobDetailPanel();
            showPage(JOBS_PAGE);
        });
        JButton removeButton = UiFactory.lightButton("Remove saved");
        removeButton.addActionListener(event -> {
            savedJobService.removeSavedJob(currentUser.id(), job.id());
            refreshJobs();
            refreshDashboard();
        });
        row.add(viewButton);
        row.add(removeButton);
        content.add(row);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private Component dashboardJobCard(JobPosting job) {
        JPanel card = UiFactory.card();
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 142));
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        JLabel title = UiFactory.bodyLabel(job.title());
        title.setFont(Theme.BUTTON_FONT);
        content.add(title);
        content.add(Box.createVerticalStrut(4));
        content.add(UiFactory.mutedLabel("<html>" + job.summaryLine() + "</html>"));
        if (hasApplied(job)) {
            content.add(Box.createVerticalStrut(8));
            content.add(UiFactory.mutedLabel("Status: " + applicationsByJobId.get(job.id()).status().label()));
        }
        content.add(Box.createVerticalStrut(10));
        JButton viewButton = UiFactory.lightButton("View detail");
        viewButton.addActionListener(event -> {
            selectedJob = job;
            updateJobDetailPanel();
            showPage(JOBS_PAGE);
        });
        JPanel row = UiFactory.flowPanel(java.awt.FlowLayout.LEFT, 0, 0);
        row.add(viewButton);
        content.add(row);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private Component notificationCard(NotificationItem item) {
        JPanel card = UiFactory.card();
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 108));
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        JLabel title = UiFactory.bodyLabel(item.title());
        title.setFont(Theme.BUTTON_FONT);
        content.add(title);
        content.add(Box.createVerticalStrut(6));
        content.add(UiFactory.bodyLabel("<html>" + item.message() + "</html>"));
        content.add(Box.createVerticalStrut(8));
        content.add(UiFactory.mutedLabel(item.createdAt().replace('T', ' ')));
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private Component dashboardSkillInsightCard(AnalyticsService.ApplicantSkillGap gap) {
        JPanel card = UiFactory.card();
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        JLabel title = UiFactory.bodyLabel(gap.jobTitle());
        title.setFont(Theme.BUTTON_FONT);
        content.add(title);
        content.add(Box.createVerticalStrut(6));
        content.add(UiFactory.mutedLabel(gap.matchScore() + "% match • " + gap.readinessLabel()));
        content.add(Box.createVerticalStrut(6));
        content.add(UiFactory.bodyLabel("<html>" + (gap.missingSkills().isEmpty()
                ? "You already cover the listed requirements."
                : "Missing: " + String.join(" | ", gap.missingSkills())) + "</html>"));
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private void refreshSkillInsights() {
        reloadSkillInsightFilters();
        List<AnalyticsService.ApplicantSkillGap> gaps = analyticsService.getLocalApplicantSkillGaps(
                currentUser.id(),
                selectedComboValue(skillInsightSemesterFilterBox, ALL_SEMESTERS)
        );
        rebuildVerticalList(
                skillInsightsListPanel,
                gaps.stream().map(this::skillInsightCard).toList(),
                "No skill insights are available for the selected jobs."
        );
    }

    private Component skillInsightCard(AnalyticsService.ApplicantSkillGap gap) {
        JPanel card = UiFactory.card();
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JLabel title = UiFactory.bodyLabel(gap.jobTitle());
        title.setFont(Theme.BUTTON_FONT);
        content.add(title);
        content.add(Box.createVerticalStrut(6));
        content.add(UiFactory.mutedLabel(gap.moduleCode() + " • " + gap.semester()));
        content.add(Box.createVerticalStrut(8));

        JPanel badgeRow = UiFactory.flowPanel(FlowLayout.LEFT, 8, 0);
        badgeRow.add(pillLabel(gap.matchScore() + "% match", Theme.PRIMARY_DARK, Color.WHITE));
        badgeRow.add(pillLabel(gap.readinessLabel(), gap.missingSkills().isEmpty() ? Theme.SUCCESS : Theme.ACCENT, Color.WHITE));
        if (gap.alreadyApplied()) {
            badgeRow.add(pillLabel("Already applied", Theme.SURFACE_MUTED, Theme.TEXT));
        }
        content.add(badgeRow);
        content.add(Box.createVerticalStrut(10));
        content.add(UiFactory.bodyLabel("<html><b>Matched:</b> " + formatInlineList(gap.matchedSkills(), "No direct skill overlap detected") + "</html>"));
        content.add(Box.createVerticalStrut(4));
        content.add(UiFactory.bodyLabel("<html><b>Missing:</b> " + formatInlineList(gap.missingSkills(), "No missing skills identified") + "</html>"));
        content.add(Box.createVerticalStrut(8));
        content.add(UiFactory.bodyLabel("<html><b>Suggestions:</b> " + formatInlineList(gap.suggestions(), "No suggestions available") + "</html>"));
        content.add(Box.createVerticalStrut(14));

        JPanel actionRow = UiFactory.flowPanel(FlowLayout.LEFT, 10, 0);
        JButton viewJobButton = UiFactory.lightButton("View job");
        viewJobButton.addActionListener(event -> openJobFromInsight(gap.jobId()));
        JButton profileButton = UiFactory.secondaryButton("Improve profile");
        profileButton.addActionListener(event -> showPage(PROFILE_PAGE));
        actionRow.add(viewJobButton);
        actionRow.add(profileButton);
        content.add(actionRow);

        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private void refreshProfile() {
        Optional<ApplicantProfile> profileOptional = profileService.findProfile(currentUser.id());
        if (profileOptional.isPresent()) {
            ApplicantProfile profile = profileOptional.get();
            fullNameField.setText(profile.fullName());
            studentIdField.setText(profile.studentId());
            programmeField.setText(profile.programme());
            yearField.setText(profile.year());
            skillsField.setText(profile.skills());
            availabilityField.setText(profile.availability());
            gpaField.setText(profile.gpa());
            currentCvOriginalFileName = profile.cvOriginalFileName();
            currentCvStoredPath = profile.cvStoredPath();
            currentAvatarOriginalFileName = profile.avatarOriginalFileName();
            currentAvatarStoredPath = profile.avatarStoredPath();
            cvStatusLabel.setText(profile.cvOriginalFileName().isBlank()
                    ? "No CV uploaded yet."
                    : "Current CV: " + profile.cvOriginalFileName());
            avatarStatusLabel.setText(profile.avatarOriginalFileName().isBlank()
                    ? "No avatar uploaded yet."
                    : "Avatar: " + profile.avatarOriginalFileName());
            resumeStatusLabel.setText(profile.cvStoredPath().isBlank()
                    ? "No PDF resume generated yet."
                    : "Generated resume ready.");
            profileTimestampLabel.setText("Last updated: " + profile.updatedAt().replace('T', ' '));
        } else {
            fullNameField.setText("");
            studentIdField.setText("");
            programmeField.setText("");
            yearField.setText("");
            skillsField.setText("");
            availabilityField.setText("");
            gpaField.setText("");
            currentCvOriginalFileName = "";
            currentCvStoredPath = "";
            currentAvatarOriginalFileName = "";
            currentAvatarStoredPath = "";
            cvStatusLabel.setText("No CV uploaded yet.");
            avatarStatusLabel.setText("No avatar uploaded yet.");
            resumeStatusLabel.setText("No PDF resume generated yet.");
            profileTimestampLabel.setText("");
        }
    }

    private void refreshJobs() {
        refreshApplicationsByJobId();
        reloadJobFilters();
        List<JobPosting> jobs = jobService.filterJobs(
                jobSearchField.getText(),
                selectedComboValue(jobTagFilterBox, ALL_TAGS),
                selectedComboValue(jobSemesterFilterBox, ALL_SEMESTERS)
        );
        if (selectedJob != null) {
            String selectedJobId = selectedJob.id();
            selectedJob = jobs.stream()
                    .filter(job -> job.id().equals(selectedJobId))
                    .findFirst()
                    .orElse(null);
        }
        if (!jobs.isEmpty() && selectedJob == null) {
            selectedJob = jobs.getFirst();
        }
        if (jobs.isEmpty()) {
            selectedJob = null;
        }
        rebuildVerticalList(
                jobsListPanel,
                jobs.stream().map(this::jobCard).toList(),
                hasActiveJobFilters() ? "No jobs match your current filters." : "No jobs are available right now."
        );
        updateJobDetailPanel();
    }

    private void refreshApplicationsByJobId() {
        applicationsByJobId = applicationService.findApplicationsForApplicant(currentUser.id()).stream()
                .collect(LinkedHashMap::new, (map, application) -> map.put(application.jobId(), application), LinkedHashMap::putAll);
    }

    private Component jobCard(JobPosting job) {
        JPanel card = UiFactory.card();
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JLabel title = UiFactory.bodyLabel(job.title());
        title.setFont(Theme.BUTTON_FONT);
        content.add(title);
        content.add(Box.createVerticalStrut(4));
        content.add(UiFactory.mutedLabel(job.moduleCode() + " • " + job.moduleName() + " • " + job.hoursPerWeek() + " hrs/week"));
        content.add(Box.createVerticalStrut(10));

        JPanel tags = UiFactory.flowPanel(java.awt.FlowLayout.LEFT, 8, 0);
        if (!job.tags().isBlank()) {
            for (String tag : job.tags().split("\\|")) {
                if (tag.isBlank()) {
                    continue;
                }
                tags.add(pillLabel(tag.trim(), Theme.SURFACE_MUTED, Theme.TEXT));
            }
        }
        content.add(tags);
        JobApplication existingApplication = applicationsByJobId.get(job.id());
        if (existingApplication != null) {
            content.add(Box.createVerticalStrut(8));
            content.add(statusPill(existingApplication.status()));
        }
        content.add(Box.createVerticalStrut(12));

        JPanel buttonRow = UiFactory.flowPanel(java.awt.FlowLayout.LEFT, 10, 0);
        JButton viewButton = UiFactory.lightButton("View detail");
        viewButton.addActionListener(event -> {
            selectedJob = job;
            updateJobDetailPanel();
        });
        JButton quickApplyButton = UiFactory.secondaryButton("Apply");
        if (existingApplication != null) {
            quickApplyButton.setText("Applied");
            quickApplyButton.setEnabled(false);
        }
        quickApplyButton.addActionListener(event -> {
            selectedJob = job;
            updateJobDetailPanel();
            applyForSelectedJob();
        });
        buttonRow.add(viewButton);
        buttonRow.add(quickApplyButton);
        if (savedJobService.isSaved(currentUser.id(), job.id())) {
            buttonRow.add(pillLabel("Saved for later", Theme.SURFACE_MUTED, Theme.TEXT));
        }
        content.add(buttonRow);

        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private void updateJobDetailPanel() {
        if (selectedJob == null) {
            detailTitleLabel.setText("Job Detail");
            detailSummaryLabel.setText("Select a role to review its details.");
            detailApplicationStatusLabel.setText("Apply to see this role appear in your application tracker.");
            detailSkillInsightLabel.setText("Match analysis appears here after a role is selected.");
            detailRequirementsArea.setText("No job selected.");
            detailDescriptionArea.setText("");
            applyButton.setText("Apply now");
            applyButton.setEnabled(false);
            saveForLaterButton.setText("Save for later");
            saveForLaterButton.setEnabled(false);
            return;
        }

        detailTitleLabel.setText(selectedJob.title());
        detailSummaryLabel.setText(selectedJob.moduleCode() + " • " + selectedJob.moduleName() + " • " + selectedJob.semester() + " • " + selectedJob.hoursPerWeek() + " hrs/week");
        updateDetailSkillInsight();
        detailRequirementsArea.setText("• " + selectedJob.requiredSkills().replace("; ", "\n• "));
        detailDescriptionArea.setText(selectedJob.description());
        boolean saved = savedJobService.isSaved(currentUser.id(), selectedJob.id());
        saveForLaterButton.setText(saved ? "Remove saved" : "Save for later");
        saveForLaterButton.setEnabled(true);
        Optional<JobApplication> applicationOptional = applicationFor(selectedJob);
        if (applicationOptional.isPresent()) {
            JobApplication application = applicationOptional.get();
            detailApplicationStatusLabel.setText("<html>Application status: "
                    + statusDisplayLabel(application.status())
                    + " • submitted "
                    + application.appliedAt().replace('T', ' ')
                    + "<br>"
                    + statusExplanation(application)
                    + (application.hasInterviewScheduled() && application.status() != ApplicationStatus.INTERVIEW_SCHEDULED
                    ? "<br>Interview: " + application.interviewAt().replace('T', ' ')
                    : "")
                    + "</html>");
            applyButton.setText("Already applied");
            applyButton.setEnabled(false);
            return;
        }

        detailApplicationStatusLabel.setText(profileService.findProfile(currentUser.id()).filter(ApplicantProfile::isComplete).isPresent()
                ? "You can submit an application for this vacancy now."
                : "Complete your profile before you submit an application.");
        applyButton.setText("Apply now");
        applyButton.setEnabled(true);
    }

    private void updateDetailSkillInsight() {
        if (selectedJob == null) {
            detailSkillInsightLabel.setText("Match analysis appears here after a role is selected.");
            return;
        }
        AnalyticsService.ApplicantSkillGap gap = analyticsService.getLocalApplicantSkillGaps(currentUser.id(), selectedJob.semester()).stream()
                .filter(item -> item.jobId().equals(selectedJob.id()))
                .findFirst()
                .orElse(null);
        if (gap == null) {
            detailSkillInsightLabel.setText("No profile match analysis is available for this vacancy yet.");
            return;
        }
        detailSkillInsightLabel.setText("<html>Profile match: "
                + gap.matchScore()
                + "% • "
                + (gap.missingSkills().isEmpty()
                ? "You already cover the listed requirements."
                : "Missing: " + String.join(" | ", gap.missingSkills()))
                + "</html>");
    }

    private void refreshApplications() {
        reloadApplicationFilters();
        List<JobApplication> applications = applicationService.findApplicationsForApplicant(
                currentUser.id(),
                selectedComboValue(applicationSemesterFilterBox, ALL_SEMESTERS),
                selectedApplicationStatus()
        );
        rebuildVerticalList(applicationsListPanel, applications.stream()
                .map(this::applicationCard)
                .toList(), hasActiveApplicationFilters()
                ? "No applications match these filters. Try Show all to see every application."
                : "You have not submitted any applications yet.");
    }

    private Component applicationCard(JobApplication application) {
        JPanel card = UiFactory.card();
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        JobPosting job = applicationService.findJobForApplication(application).orElse(null);
        String jobTitle = job == null ? "Unknown role" : job.title();
        JLabel title = UiFactory.bodyLabel(jobTitle);
        title.setFont(Theme.BUTTON_FONT);
        content.add(title);
        content.add(Box.createVerticalStrut(6));
        JPanel statusRow = UiFactory.flowPanel(FlowLayout.LEFT, 10, 0);
        statusRow.add(statusPill(application.status()));
        if (application.hasInterviewScheduled()) {
            statusRow.add(pillLabel("Interview " + application.interviewAt().replace('T', ' '), Theme.PRIMARY_DARK, Color.WHITE));
        }
        content.add(statusRow);
        content.add(Box.createVerticalStrut(8));
        content.add(UiFactory.mutedLabel("Applied at " + application.appliedAt().replace('T', ' ')));
        if (job != null) {
            content.add(Box.createVerticalStrut(6));
            content.add(UiFactory.mutedLabel(job.moduleCode() + " • " + job.moduleName() + " • " + job.semester()));
        }
        if (!application.note().isBlank()) {
            content.add(Box.createVerticalStrut(8));
            content.add(UiFactory.bodyLabel("<html>" + application.note() + "</html>"));
        }
        content.add(Box.createVerticalStrut(8));
        content.add(UiFactory.mutedLabel(statusExplanation(application)));
        if (application.status() != ApplicationStatus.SELECTED) {
            content.add(Box.createVerticalStrut(14));
            JPanel actions = UiFactory.flowPanel(FlowLayout.LEFT, 10, 0);
            JButton withdrawButton = UiFactory.lightButton("Withdraw");
            withdrawButton.addActionListener(event -> withdrawApplication(application));
            actions.add(withdrawButton);
            content.add(actions);
        }
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private void reloadJobFilters() {
        reloadComboBox(jobTagFilterBox, ALL_TAGS, jobService.availableTags());
        reloadComboBox(jobSemesterFilterBox, ALL_SEMESTERS, jobService.availableSemesters());
    }

    private void resetJobFilters() {
        jobSearchField.setText("");
        jobTagFilterBox.setSelectedItem(ALL_TAGS);
        jobSemesterFilterBox.setSelectedItem(ALL_SEMESTERS);
        refreshJobs();
    }

    private void reloadApplicationFilters() {
        reloadComboBox(applicationSemesterFilterBox, ALL_SEMESTERS, applicationService.availableApplicationSemestersForApplicant(currentUser.id()));
        setSelectedApplicationStatus(selectedApplicationStatusLabel());
    }

    private void reloadSkillInsightFilters() {
        reloadComboBox(skillInsightSemesterFilterBox, ALL_SEMESTERS, jobService.availableSemesters());
    }

    private void resetApplicationFilters() {
        applicationSemesterFilterBox.setSelectedItem(ALL_SEMESTERS);
        setSelectedApplicationStatus(ALL_STATUSES);
        refreshApplications();
    }

    private void resetSkillInsightFilters() {
        skillInsightSemesterFilterBox.setSelectedItem(ALL_SEMESTERS);
        refreshSkillInsights();
    }

    private void reloadComboBox(JComboBox<String> comboBox, String defaultItem, List<String> dynamicItems) {
        suppressFilterRefresh = true;
        Object previousSelection = comboBox.getSelectedItem();
        comboBox.removeAllItems();
        comboBox.addItem(defaultItem);
        for (String item : dynamicItems) {
            if (!defaultItem.equalsIgnoreCase(item)) {
                comboBox.addItem(item);
            }
        }
        if (previousSelection != null) {
            comboBox.setSelectedItem(previousSelection);
        }
        if (comboBox.getSelectedItem() == null) {
            comboBox.setSelectedItem(defaultItem);
        }
        suppressFilterRefresh = false;
    }

    private String selectedComboValue(JComboBox<String> comboBox, String fallback) {
        Object selectedItem = comboBox.getSelectedItem();
        return selectedItem == null ? fallback : selectedItem.toString();
    }

    private boolean hasActiveJobFilters() {
        return !jobSearchField.getText().isBlank()
                || !ALL_TAGS.equalsIgnoreCase(selectedComboValue(jobTagFilterBox, ALL_TAGS))
                || !ALL_SEMESTERS.equalsIgnoreCase(selectedComboValue(jobSemesterFilterBox, ALL_SEMESTERS));
    }

    private boolean hasActiveApplicationFilters() {
        return !ALL_SEMESTERS.equalsIgnoreCase(selectedComboValue(applicationSemesterFilterBox, ALL_SEMESTERS))
                || selectedApplicationStatus() != null;
    }

    private ApplicationStatus selectedApplicationStatus() {
        String selected = selectedApplicationStatusLabel();
        return ALL_STATUSES.equalsIgnoreCase(selected) ? null : ApplicationStatus.fromLabel(selected);
    }

    private String selectedApplicationStatusLabel() {
        for (Component component : applicationStatusFilterPanel.getComponents()) {
            if (component instanceof JButton button && Theme.ACCENT.equals(button.getBackground())) {
                return button.getText();
            }
        }
        return ALL_STATUSES;
    }

    private JLabel statusPill(ApplicationStatus status) {
        return switch (status) {
            case APPLIED -> pillLabel(statusDisplayLabel(status), new Color(210, 226, 247), Theme.PRIMARY_DARK);
            case UNDER_REVIEW -> pillLabel(statusDisplayLabel(status), new Color(230, 236, 249), Theme.PRIMARY_DARK);
            case SHORTLISTED -> pillLabel(statusDisplayLabel(status), new Color(235, 246, 214), new Color(67, 109, 21));
            case INTERVIEW_SCHEDULED -> pillLabel(statusDisplayLabel(status), new Color(252, 238, 191), new Color(130, 79, 13));
            case SELECTED -> pillLabel(statusDisplayLabel(status), new Color(208, 244, 220), new Color(29, 102, 68));
            case REJECTED -> pillLabel(statusDisplayLabel(status), new Color(249, 217, 217), new Color(138, 42, 42));
            case WITHDRAWN -> pillLabel(statusDisplayLabel(status), new Color(229, 231, 235), Theme.SUBTLE_TEXT);
        };
    }

    private JLabel pillLabel(String text, Color background, Color foreground) {
        JLabel label = UiFactory.bodyLabel("  " + text + "  ");
        label.setOpaque(true);
        label.setBackground(background);
        label.setForeground(foreground);
        label.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        return label;
    }

    private void withdrawApplication(JobApplication application) {
        int result = JOptionPane.showConfirmDialog(
                this,
                "Withdraw this application? This cannot be undone.",
                "Confirm Withdrawal",
                JOptionPane.YES_NO_OPTION
        );
        if (result != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            applicationService.withdrawApplication(application.id(), currentUser.id());
            refreshAll();
            showPage(APPLICATIONS_PAGE);
            JOptionPane.showMessageDialog(this, "Application withdrawn successfully.");
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage());
        }
    }

    private void saveProfile() {
        profileValidationSubmitted = true;
        updateProfileValidation(true);
        try {
            ApplicantProfile savedProfile = profileService.saveProfile(new ApplicantProfile(
                    currentUser.id(),
                    fullNameField.getText(),
                    studentIdField.getText(),
                    programmeField.getText(),
                    yearField.getText(),
                    skillsField.getText(),
                    availabilityField.getText(),
                    gpaField.getText(),
                    currentCvOriginalFileName,
                    currentCvStoredPath,
                    currentAvatarOriginalFileName,
                    currentAvatarStoredPath,
                    ""
            ));
            currentCvOriginalFileName = savedProfile.cvOriginalFileName();
            currentCvStoredPath = savedProfile.cvStoredPath();
            refreshAll();
            showPage(PROFILE_PAGE);
            JOptionPane.showMessageDialog(this, "Profile saved successfully.");
        } catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage());
        }
    }

    private void attachProfileValidation() {
        DocumentListener listener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                updateProfileValidation(profileValidationSubmitted);
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                updateProfileValidation(profileValidationSubmitted);
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                updateProfileValidation(profileValidationSubmitted);
            }
        };
        for (JTextField field : List.of(fullNameField, studentIdField, programmeField, yearField, skillsField, availabilityField, gpaField)) {
            field.getDocument().addDocumentListener(listener);
        }
        updateProfileValidation(false);
    }

    private void updateProfileValidation(boolean showRequiredErrors) {
        setRequiredHint(fullNameValidationLabel, fullNameField.getText(), "Full name is required.", "", showRequiredErrors);
        setStudentIdHint(showRequiredErrors);
        setRequiredHint(programmeValidationLabel, programmeField.getText(), "Programme is required.", "", showRequiredErrors);
        setYearHint(showRequiredErrors);
        setRequiredHint(availabilityValidationLabel, availabilityField.getText(), "Availability is required.", "When can you work?", showRequiredErrors);
        setGpaHint(showRequiredErrors);
        setRequiredHint(skillsValidationLabel, skillsField.getText(), "Skills are required.", "Separate skills with commas", showRequiredErrors);
    }

    private void setRequiredHint(JLabel label, String value, String errorMessage, String helperMessage, boolean showRequiredError) {
        boolean blank = value == null || value.trim().isBlank();
        UiFactory.setValidationMessage(label, blank ? (showRequiredError ? errorMessage : helperMessage) : "", blank && showRequiredError);
    }

    private void setStudentIdHint(boolean showRequiredError) {
        String value = studentIdField.getText().trim();
        if (value.isBlank()) {
            UiFactory.setValidationMessage(studentIdValidationLabel, showRequiredError ? "Student ID is required." : "6 to 12 digits", showRequiredError);
            return;
        }
        boolean invalid = !value.matches("\\d{6,12}");
        UiFactory.setValidationMessage(
                studentIdValidationLabel,
                invalid ? "Student ID must be 6 to 12 digits." : "",
                invalid
        );
    }

    private void setYearHint(boolean showRequiredError) {
        String value = yearField.getText().trim();
        if (value.isBlank()) {
            UiFactory.setValidationMessage(yearValidationLabel, showRequiredError ? "Year is required." : "Example: Year 2", showRequiredError);
            return;
        }
        boolean invalid = !value.matches("(?i)(year\\s*)?[1-6]");
        UiFactory.setValidationMessage(
                yearValidationLabel,
                invalid ? "Year must be 1 to 6, e.g. Year 2." : "",
                invalid
        );
    }

    private void setGpaHint(boolean showRequiredError) {
        String value = gpaField.getText().trim();
        if (value.isBlank()) {
            UiFactory.setValidationMessage(gpaValidationLabel, showRequiredError ? "GPA is required." : "0.0 to 4.3", showRequiredError);
            return;
        }
        try {
            double gpa = Double.parseDouble(value);
            boolean invalid = gpa < 0.0 || gpa > 4.3;
            UiFactory.setValidationMessage(gpaValidationLabel, invalid ? "GPA must be between 0.0 and 4.3." : "", invalid);
        } catch (NumberFormatException exception) {
            UiFactory.setValidationMessage(gpaValidationLabel, "GPA must be a number, e.g. 3.7.", true);
        }
    }

    private void uploadCv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PDF resumes (*.pdf)", "pdf"));
        chooser.setAcceptAllFileFilterUsed(false);
        int selection = chooser.showOpenDialog(this);
        if (selection != JFileChooser.APPROVE_OPTION) {
            return;
        }

        try {
            CvService.StoredCv storedCv = cvService.storeCv(chooser.getSelectedFile().toPath());
            ApplicantProfile savedProfile = profileService.saveCvReference(
                    currentUser.id(),
                    storedCv.originalFileName(),
                    storedCv.storedPath()
            );
            currentCvOriginalFileName = savedProfile.cvOriginalFileName();
            currentCvStoredPath = savedProfile.cvStoredPath();
            cvStatusLabel.setText("Current CV: " + currentCvOriginalFileName);
            resumeStatusLabel.setText("Resume ready: " + currentCvOriginalFileName);
            profileTimestampLabel.setText("Last updated: " + savedProfile.updatedAt().replace('T', ' '));
            JOptionPane.showMessageDialog(this, "CV uploaded successfully.");
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage());
        }
    }

    private void uploadAvatar() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Profile photos (*.jpg, *.jpeg, *.png)", "jpg", "jpeg", "png"));
        chooser.setAcceptAllFileFilterUsed(false);
        int selection = chooser.showOpenDialog(this);
        if (selection != JFileChooser.APPROVE_OPTION) {
            return;
        }

        try {
            CvService.StoredCv storedAvatar = cvService.storeAvatar(chooser.getSelectedFile().toPath());
            ApplicantProfile savedProfile = profileService.saveAvatarReference(
                    currentUser.id(),
                    storedAvatar.originalFileName(),
                    storedAvatar.storedPath()
            );
            currentAvatarOriginalFileName = savedProfile.avatarOriginalFileName();
            currentAvatarStoredPath = savedProfile.avatarStoredPath();
            avatarStatusLabel.setText("Avatar: " + currentAvatarOriginalFileName);
            profileTimestampLabel.setText("Last updated: " + savedProfile.updatedAt().replace('T', ' '));
            JOptionPane.showMessageDialog(this, "Profile photo uploaded successfully.");
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage());
        }
    }

    private void generateResumePdf() {
        try {
            ApplicantProfile profile = profileService.saveProfile(new ApplicantProfile(
                    currentUser.id(),
                    fullNameField.getText(),
                    studentIdField.getText(),
                    programmeField.getText(),
                    yearField.getText(),
                    skillsField.getText(),
                    availabilityField.getText(),
                    gpaField.getText(),
                    currentCvOriginalFileName,
                    currentCvStoredPath,
                    currentAvatarOriginalFileName,
                    currentAvatarStoredPath,
                    ""
            ));
            CvService.StoredCv generatedResume = cvService.generateResumePdf(profile);
            ApplicantProfile savedProfile = profileService.saveCvReference(
                    currentUser.id(),
                    generatedResume.originalFileName(),
                    generatedResume.storedPath()
            );
            currentCvOriginalFileName = savedProfile.cvOriginalFileName();
            currentCvStoredPath = savedProfile.cvStoredPath();
            cvStatusLabel.setText("Current CV: " + currentCvOriginalFileName);
            resumeStatusLabel.setText("Generated PDF resume: " + currentCvOriginalFileName);
            profileTimestampLabel.setText("Last updated: " + savedProfile.updatedAt().replace('T', ' '));
            JOptionPane.showMessageDialog(this, "PDF resume generated successfully.");
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage());
        }
    }

    private void openGeneratedResume() {
        openCv();
    }

    private void openCv() {
        if (currentCvStoredPath.isBlank()) {
            JOptionPane.showMessageDialog(this, "Upload or save a CV first.");
            return;
        }
        try {
            cvService.openCv(currentCvStoredPath);
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage());
        }
    }

    private void downloadCv() {
        if (currentCvStoredPath.isBlank()) {
            JOptionPane.showMessageDialog(this, "Upload or save a CV first.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File(currentCvOriginalFileName.isBlank() ? "cv.txt" : currentCvOriginalFileName));
        int selection = chooser.showSaveDialog(this);
        if (selection != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            cvService.exportCv(currentCvStoredPath, Path.of(chooser.getSelectedFile().getAbsolutePath()));
            JOptionPane.showMessageDialog(this, "CV downloaded successfully.");
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage());
        }
    }

    private void applyForSelectedJob() {
        if (selectedJob == null) {
            JOptionPane.showMessageDialog(this, "Select a job before applying.");
            return;
        }
        if (profileService.findProfile(currentUser.id()).filter(ApplicantProfile::isComplete).isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please complete your profile before applying.");
            showPage(PROFILE_PAGE);
            return;
        }
        try {
            applicationService.applyForJob(selectedJob.id(), currentUser.id());
            refreshApplicationsByJobId();
            refreshJobs();
            resetApplicationFilters();
            refreshDashboard();
            refreshSkillInsights();
            showPage(APPLICATIONS_PAGE);
            JOptionPane.showMessageDialog(this, "Application submitted successfully.");
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage());
        }
    }

    private void toggleSavedJob() {
        if (selectedJob == null) {
            JOptionPane.showMessageDialog(this, "Select a job before saving it.");
            return;
        }
        try {
            if (savedJobService.isSaved(currentUser.id(), selectedJob.id())) {
                savedJobService.removeSavedJob(currentUser.id(), selectedJob.id());
                JOptionPane.showMessageDialog(this, "Saved job removed.");
            } else {
                savedJobService.saveJob(currentUser.id(), selectedJob.id());
                JOptionPane.showMessageDialog(this, "Job saved for later.");
            }
            refreshJobs();
            refreshDashboard();
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage());
        }
    }

    private String statusDisplayLabel(ApplicationStatus status) {
        return status == ApplicationStatus.INTERVIEW_SCHEDULED ? "Interview Scheduled" : status.label();
    }

    private String statusExplanation(JobApplication application) {
        return switch (application.status()) {
            case APPLIED -> "Applied: Your application has been received and is waiting for review.";
            case UNDER_REVIEW -> "Under Review: The organiser is reviewing your application.";
            case SHORTLISTED -> "Shortlisted: Please wait for an interview invitation or further review.";
            case INTERVIEW_SCHEDULED -> "Interview Scheduled: Please attend the interview"
                    + (application.hasInterviewScheduled() ? " at " + application.interviewAt().replace('T', ' ') : "")
                    + ".";
            case SELECTED -> "Selected: You have been selected for this role.";
            case REJECTED -> "Rejected: Your application was not selected.";
            case WITHDRAWN -> "Withdrawn: This application has been withdrawn.";
        };
    }

    private boolean hasApplied(JobPosting job) {
        return applicationsByJobId.containsKey(job.id());
    }

    private Optional<JobApplication> applicationFor(JobPosting job) {
        return Optional.ofNullable(applicationsByJobId.get(job.id()));
    }

    private void openJobFromInsight(String jobId) {
        jobService.findById(jobId).ifPresent(job -> {
            selectedJob = job;
            updateJobDetailPanel();
            showPage(JOBS_PAGE);
        });
    }

    private void showPage(String page) {
        pageLayout.show(pagePanel, page);
    }

    private String formatInlineList(List<String> items, String fallback) {
        return items.isEmpty() ? fallback : String.join(" | ", items);
    }

    private void rebuildVerticalList(JPanel container, List<Component> cards, String emptyMessage) {
        container.removeAll();
        if (cards.isEmpty()) {
            container.add(UiFactory.mutedLabel(emptyMessage));
        } else {
            for (int index = 0; index < cards.size(); index++) {
                container.add(cards.get(index));
                if (index < cards.size() - 1) {
                    container.add(Box.createVerticalStrut(12));
                }
            }
        }
        container.revalidate();
        container.repaint();
    }
}
