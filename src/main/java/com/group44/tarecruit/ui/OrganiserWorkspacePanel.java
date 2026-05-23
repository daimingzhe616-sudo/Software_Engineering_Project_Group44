package com.group44.tarecruit.ui;

import com.group44.tarecruit.model.ApplicantProfile;
import com.group44.tarecruit.model.ApplicationStatus;
import com.group44.tarecruit.model.JobPosting;
import com.group44.tarecruit.model.UserAccount;
import com.group44.tarecruit.service.ApplicationService;
import com.group44.tarecruit.service.AnalyticsService;
import com.group44.tarecruit.service.CvService;
import com.group44.tarecruit.service.JobService;
import com.group44.tarecruit.ui.components.Theme;
import com.group44.tarecruit.ui.components.UiFactory;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.LocalDateTime;
import java.nio.file.Path;
import java.util.List;

public class OrganiserWorkspacePanel extends JPanel {
    private static final String APPLICANTS_PAGE = "applicants";
    private static final String POST_JOB_PAGE = "postJob";

    private final ApplicationService applicationService;
    private final JobService jobService;
    private final AnalyticsService analyticsService;
    private final CvService cvService;
    private final Runnable accountAction;
    private final Runnable logoutAction;

    private final CardLayout pageLayout;
    private final JPanel pagePanel;
    private final JComboBox<JobPosting> jobSelector;
    private final JComboBox<String> statusFilterBox;
    private final JTextField applicantSearchField;
    private final JPanel applicantListPanel;
    private final JLabel applicantSummaryLabel;
    private final JLabel capacityWarningLabel;
    private final JTextField roleField;
    private final JTextField hoursPerWeekField;
    private final JTextField moduleCodeField;
    private final JTextField moduleNameField;
    private final JTextField semesterField;
    private final JTextArea requiredSkillsArea;
    private final JTextArea descriptionArea;
    private final JTextField tagsField;
    private final JTextField openingsField;
    private final JLabel roleValidationLabel;
    private final JLabel hoursValidationLabel;
    private final JLabel moduleCodeValidationLabel;
    private final JLabel moduleNameValidationLabel;
    private final JLabel semesterValidationLabel;
    private final JLabel openingsValidationLabel;
    private final JLabel requiredSkillsValidationLabel;
    private final JLabel descriptionValidationLabel;
    private final JLabel previewTitleLabel;
    private final JLabel previewSummaryLabel;
    private final JTextArea previewRequirementsArea;
    private final JTextArea previewDescriptionArea;

    private UserAccount currentUser;
    private boolean suppressApplicantRefresh;
    private boolean needsActionOnly;
    private boolean postJobValidationSubmitted;
    private JButton needsActionButton;

    public OrganiserWorkspacePanel(
            ApplicationService applicationService,
            JobService jobService,
            AnalyticsService analyticsService,
            CvService cvService,
            Runnable accountAction,
            Runnable logoutAction
    ) {
        this.applicationService = applicationService;
        this.jobService = jobService;
        this.analyticsService = analyticsService;
        this.cvService = cvService;
        this.accountAction = accountAction;
        this.logoutAction = logoutAction;

        setLayout(new BorderLayout());
        setBackground(Theme.APP_BACKGROUND);
        add(buildSidebar(), BorderLayout.WEST);

        jobSelector = new JComboBox<>();
        jobSelector.setFont(Theme.BODY_FONT);
        statusFilterBox = new JComboBox<>();
        statusFilterBox.setFont(Theme.BODY_FONT);
        for (String label : List.of("All statuses", "Applied", "Under Review", "Shortlisted", "Interview", "Selected", "Rejected")) {
            statusFilterBox.addItem(label);
        }
        statusFilterBox.addActionListener(event -> refreshApplicantList());
        applicantSearchField = UiFactory.textField();
        applicantListPanel = new JPanel();
        applicantListPanel.setLayout(new BoxLayout(applicantListPanel, BoxLayout.Y_AXIS));
        applicantListPanel.setOpaque(false);
        applicantSummaryLabel = UiFactory.mutedLabel("No vacancy selected.");
        capacityWarningLabel = UiFactory.mutedLabel("");

        roleField = UiFactory.textField();
        hoursPerWeekField = UiFactory.numericTextField(3);
        moduleCodeField = UiFactory.textField();
        moduleNameField = UiFactory.textField();
        semesterField = UiFactory.textField();
        requiredSkillsArea = UiFactory.textArea(3);
        descriptionArea = UiFactory.textArea(4);
        tagsField = UiFactory.textField();
        openingsField = UiFactory.numericTextField(2);
        roleValidationLabel = UiFactory.validationLabel();
        hoursValidationLabel = UiFactory.validationLabel();
        moduleCodeValidationLabel = UiFactory.validationLabel();
        moduleNameValidationLabel = UiFactory.validationLabel();
        semesterValidationLabel = UiFactory.validationLabel();
        openingsValidationLabel = UiFactory.validationLabel();
        requiredSkillsValidationLabel = UiFactory.validationLabel();
        descriptionValidationLabel = UiFactory.validationLabel();
        previewTitleLabel = UiFactory.sectionLabel("Vacancy preview");
        previewSummaryLabel = UiFactory.mutedLabel("Enter vacancy details and use Preview to confirm the listing.");
        previewRequirementsArea = readOnlyArea(4);
        previewDescriptionArea = readOnlyArea(5);

        pageLayout = new CardLayout();
        pagePanel = new JPanel(pageLayout);
        pagePanel.setOpaque(false);
        pagePanel.add(buildApplicantsPage(), APPLICANTS_PAGE);
        pagePanel.add(buildPostJobPage(), POST_JOB_PAGE);
        add(pagePanel, BorderLayout.CENTER);

        applicantSearchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refreshApplicantList();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshApplicantList();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshApplicantList();
            }
        });
        attachPostJobValidation();
    }

    public void setCurrentUser(UserAccount currentUser) {
        this.currentUser = currentUser;
    }

    public void refreshAll() {
        applicantSearchField.setText("");
        statusFilterBox.setSelectedItem("All statuses");
        needsActionOnly = false;
        updateNeedsActionButton();
        refreshJobSelector(null);
        refreshApplicantList();
        showPage(APPLICANTS_PAGE);
        updatePreviewFromForm();
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(Theme.SURFACE);
        sidebar.setBorder(BorderFactory.createEmptyBorder(16, 14, 16, 14));
        sidebar.setPreferredSize(new Dimension(176, 620));

        sidebar.add(UiFactory.sectionLabel("TA Recruit"));
        sidebar.add(Box.createVerticalStrut(8));
        JLabel userHint = UiFactory.mutedLabel("Module organiser workspace");
        sidebar.add(userHint);
        sidebar.add(Box.createVerticalStrut(28));

        JButton postJobButton = UiFactory.navButton("Post Job");
        postJobButton.addActionListener(event -> showPage(POST_JOB_PAGE));
        JButton applicantsButton = UiFactory.navButton("Applications");
        applicantsButton.addActionListener(event -> showPage(APPLICANTS_PAGE));

        sidebar.add(postJobButton);
        sidebar.add(Box.createVerticalStrut(12));
        sidebar.add(applicantsButton);
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

    private JPanel buildApplicantsPage() {
        JPanel page = new JPanel(new BorderLayout(0, 14));
        page.setOpaque(false);
        page.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(UiFactory.titleLabel("Review Applicants (MO)"));
        top.add(Box.createVerticalStrut(6));
        top.add(UiFactory.mutedLabel("Open one vacancy at a time, access applicant CVs and update the selection decision."));
        top.add(Box.createVerticalStrut(12));

        JPanel controls = UiFactory.card();
        JPanel controlsContent = new JPanel();
        controlsContent.setOpaque(false);
        controlsContent.setLayout(new BoxLayout(controlsContent, BoxLayout.Y_AXIS));
        JPanel row = new JPanel(new GridLayout(1, 4, 10, 0));
        row.setOpaque(false);
        JPanel vacancyField = labeledField("Vacancy", jobSelector);
        jobSelector.addActionListener(event -> {
            if (!suppressApplicantRefresh) {
                refreshApplicantList();
            }
        });
        jobSelector.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "" : value.title() + " — " + value.moduleCode()));
        row.add(vacancyField);
        row.add(labeledField("Status", statusFilterBox));
        row.add(labeledField("Search applicants", applicantSearchField));
        JPanel actionField = new JPanel();
        actionField.setOpaque(false);
        actionField.setLayout(new BoxLayout(actionField, BoxLayout.Y_AXIS));
        actionField.add(UiFactory.bodyLabel("Actions"));
        actionField.add(Box.createVerticalStrut(8));
        needsActionButton = UiFactory.lightButton("Needs action");
        needsActionButton.addActionListener(event -> {
            needsActionOnly = !needsActionOnly;
            if (needsActionOnly) {
                statusFilterBox.setSelectedItem("All statuses");
            }
            updateNeedsActionButton();
            refreshApplicantList();
        });
        actionField.add(needsActionButton);
        actionField.add(Box.createVerticalStrut(8));
        JButton shortlistOnlyButton = UiFactory.secondaryButton("Shortlist list");
        shortlistOnlyButton.addActionListener(event -> {
            needsActionOnly = false;
            updateNeedsActionButton();
            statusFilterBox.setSelectedItem(ApplicationStatus.SHORTLISTED.label());
        });
        actionField.add(shortlistOnlyButton);
        actionField.add(Box.createVerticalStrut(8));
        JButton exportButton = UiFactory.lightButton("Export CSV");
        exportButton.addActionListener(event -> exportApplicantsForSelectedJob());
        actionField.add(exportButton);
        actionField.add(Box.createVerticalStrut(8));
        JButton clearFiltersButton = UiFactory.lightButton("Clear filters");
        clearFiltersButton.addActionListener(event -> {
            needsActionOnly = false;
            applicantSearchField.setText("");
            statusFilterBox.setSelectedItem("All statuses");
            updateNeedsActionButton();
            refreshApplicantList();
        });
        actionField.add(clearFiltersButton);
        row.add(actionField);
        controlsContent.add(row);
        controlsContent.add(Box.createVerticalStrut(12));
        JPanel summaryRow = UiFactory.flowPanel(FlowLayout.LEFT, 14, 0);
        summaryRow.add(applicantSummaryLabel);
        summaryRow.add(capacityWarningLabel);
        controlsContent.add(summaryRow);
        controls.add(controlsContent, BorderLayout.CENTER);
        top.add(controls);
        page.add(top, BorderLayout.NORTH);
        page.add(UiFactory.scrollPane(applicantListPanel), BorderLayout.CENTER);
        return page;
    }

    private JPanel buildPostJobPage() {
        JPanel page = new JPanel(new BorderLayout(0, 14));
        page.setOpaque(false);
        page.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(UiFactory.titleLabel("Post Job (MO)"));
        header.add(Box.createVerticalStrut(6));
        header.add(UiFactory.mutedLabel("Create a new vacancy with the role, module details, required skills and workload information."));
        page.add(header, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(1, 2, 10, 0));
        grid.setOpaque(false);
        grid.add(buildPostJobFormCard());
        grid.add(buildPostJobPreviewCard());
        page.add(grid, BorderLayout.CENTER);
        return page;
    }

    private JPanel pageWrapper() {
        JPanel page = new JPanel();
        page.setOpaque(false);
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
        page.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        return page;
    }

    private void refreshApplicantList() {
        applicantListPanel.removeAll();
        JobPosting selectedJob = (JobPosting) jobSelector.getSelectedItem();
        if (selectedJob == null) {
            applicantSummaryLabel.setText("No vacancy selected.");
            capacityWarningLabel.setText("");
            applicantListPanel.add(UiFactory.mutedLabel("No vacancies are available."));
        } else {
            ApplicationStatus statusFilter = selectedStatusFilter();
            List<ApplicationService.ApplicantReviewItem> allItems = applicationService.findApplicantsForJob(selectedJob.id(), "", null);
            List<ApplicationService.ApplicantReviewItem> items = applicationService.findApplicantsForJob(selectedJob.id(), applicantSearchField.getText(), statusFilter);
            if (needsActionOnly) {
                items = items.stream()
                        .filter(this::needsAction)
                        .toList();
            }
            updateApplicantSummary(selectedJob, allItems);
            if (items.isEmpty()) {
                applicantListPanel.add(UiFactory.mutedLabel(emptyApplicantMessage(statusFilter)));
            } else {
                for (int index = 0; index < items.size(); index++) {
                    applicantListPanel.add(applicantCard(selectedJob, items.get(index)));
                    if (index < items.size() - 1) {
                        applicantListPanel.add(Box.createVerticalStrut(12));
                    }
                }
            }
        }
        applicantListPanel.revalidate();
        applicantListPanel.repaint();
    }

    private String emptyApplicantMessage(ApplicationStatus statusFilter) {
        if (needsActionOnly) {
            return "No applicants currently need organiser action.";
        }
        if (!applicantSearchField.getText().isBlank()) {
            return "No applicants matched the current search.";
        }
        if (statusFilter != null) {
            return "No applicants match this status.";
        }
        return "No applicants yet for this vacancy.";
    }

    private void updateApplicantSummary(JobPosting job, List<ApplicationService.ApplicantReviewItem> items) {
        long needsActionCount = items.stream()
                .filter(this::needsAction)
                .count();
        String filterHint = needsActionOnly ? " | Showing " + needsActionCount + " needing action" : "";
        applicantSummaryLabel.setText(String.format(
                "Applied %d | Review %d | Shortlisted %d | Interview %d | Selected %d | Rejected %d%s",
                countStatus(items, ApplicationStatus.APPLIED),
                countStatus(items, ApplicationStatus.UNDER_REVIEW),
                countStatus(items, ApplicationStatus.SHORTLISTED),
                countStatus(items, ApplicationStatus.INTERVIEW_SCHEDULED),
                countStatus(items, ApplicationStatus.SELECTED),
                countStatus(items, ApplicationStatus.REJECTED),
                filterHint
        ));

        long selectedCount = applicationService.selectedCountForJob(job.id());
        capacityWarningLabel.setText("Capacity: " + selectedCount + "/" + job.openings() + " selected");
        capacityWarningLabel.setForeground(selectedCount >= job.openings() ? new Color(138, 42, 42) : Theme.SUBTLE_TEXT);
    }

    private long countStatus(List<ApplicationService.ApplicantReviewItem> items, ApplicationStatus status) {
        return items.stream()
                .filter(item -> item.application().status() == status)
                .count();
    }

    private boolean needsAction(ApplicationService.ApplicantReviewItem item) {
        return switch (item.application().status()) {
            case APPLIED, UNDER_REVIEW, SHORTLISTED, INTERVIEW_SCHEDULED -> true;
            case SELECTED, REJECTED, WITHDRAWN -> false;
        };
    }

    private void updateNeedsActionButton() {
        if (needsActionButton == null) {
            return;
        }
        needsActionButton.setText(needsActionOnly ? "Needs action: on" : "Needs action");
        needsActionButton.setOpaque(true);
        needsActionButton.setBackground(needsActionOnly ? Theme.ACCENT : Theme.SURFACE_MUTED);
        needsActionButton.setForeground(needsActionOnly ? Color.WHITE : Theme.PRIMARY_DARK);
    }

    private JPanel buildPostJobFormCard() {
        JPanel card = UiFactory.card();

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JPanel fieldsGrid = new JPanel(new GridLayout(0, 2, 12, 10));
        fieldsGrid.setOpaque(false);
        fieldsGrid.add(labeledField("Role", roleField, roleValidationLabel));
        fieldsGrid.add(labeledField("Hours/week", hoursPerWeekField, hoursValidationLabel));
        fieldsGrid.add(labeledField("Module code", moduleCodeField, moduleCodeValidationLabel));
        fieldsGrid.add(labeledField("Module/activity", moduleNameField, moduleNameValidationLabel));
        fieldsGrid.add(labeledField("Semester", semesterField, semesterValidationLabel));
        fieldsGrid.add(labeledField("Openings", openingsField, openingsValidationLabel));
        fieldsGrid.add(labeledField("Tags", tagsField));
        fieldsGrid.add(scrollField("Required skills", requiredSkillsArea, requiredSkillsValidationLabel));

        content.add(fieldsGrid);
        content.add(Box.createVerticalStrut(16));
        content.add(scrollField("Description", descriptionArea, descriptionValidationLabel));
        content.add(Box.createVerticalStrut(18));

        JPanel actions = new JPanel(new GridLayout(1, 3, 8, 0));
        actions.setOpaque(false);
        JButton publishButton = UiFactory.primaryButton("Publish Job");
        publishButton.addActionListener(event -> publishJob());
        JButton previewButton = UiFactory.secondaryButton("Preview");
        previewButton.addActionListener(event -> updatePreviewFromForm());
        JButton clearButton = UiFactory.lightButton("Clear");
        clearButton.addActionListener(event -> {
            clearPostJobForm();
            updatePreviewFromForm();
        });
        actions.add(publishButton);
        actions.add(previewButton);
        actions.add(clearButton);
        content.add(actions);

        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildPostJobPreviewCard() {
        JPanel card = UiFactory.card();
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(previewTitleLabel);
        content.add(Box.createVerticalStrut(8));
        content.add(previewSummaryLabel);
        content.add(Box.createVerticalStrut(16));
        content.add(UiFactory.bodyLabel("Required skills"));
        content.add(Box.createVerticalStrut(8));
        content.add(new JScrollPane(previewRequirementsArea));
        content.add(Box.createVerticalStrut(14));
        content.add(UiFactory.bodyLabel("Description"));
        content.add(Box.createVerticalStrut(8));
        content.add(new JScrollPane(previewDescriptionArea));

        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private Component applicantCard(JobPosting job, ApplicationService.ApplicantReviewItem item) {
        JPanel card = UiFactory.card();
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 305));
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JLabel title = UiFactory.bodyLabel(item.applicant().displayName());
        title.setFont(Theme.BUTTON_FONT);
        content.add(title);
        content.add(Box.createVerticalStrut(6));
        JPanel metaRow = UiFactory.flowPanel(FlowLayout.LEFT, 10, 0);
        metaRow.add(statusPill(item.application().status()));
        metaRow.add(pillLabel(matchScore(job, item) + "% match", Theme.PRIMARY_DARK, Color.WHITE));
        if (item.application().hasInterviewScheduled()) {
            metaRow.add(pillLabel("Interview " + item.application().interviewAt().replace('T', ' '), Theme.ACCENT, Color.WHITE));
        }
        content.add(metaRow);
        content.add(Box.createVerticalStrut(8));

        ApplicantProfile profile = item.profile();
        content.add(UiFactory.mutedLabel(profile.studentId().isBlank()
                ? "Profile not completed yet."
                : profile.studentId() + " • " + profile.programme() + " • " + profile.year()));
        content.add(Box.createVerticalStrut(10));
        content.add(UiFactory.bodyLabel("Skills: " + (profile.skills().isBlank() ? "No skills provided" : profile.skills())));
        content.add(Box.createVerticalStrut(8));
        content.add(UiFactory.bodyLabel("Availability: " + (profile.availability().isBlank() ? "Not provided" : profile.availability())));
        content.add(Box.createVerticalStrut(8));
        if (item.application().status() == ApplicationStatus.INTERVIEW_SCHEDULED && item.application().hasInterviewScheduled()) {
            content.add(UiFactory.bodyLabel("Interview time: " + item.application().interviewAt().replace('T', ' ')));
            content.add(Box.createVerticalStrut(8));
        }
        if (!item.application().note().isBlank()) {
            content.add(Box.createVerticalStrut(8));
            content.add(UiFactory.mutedLabel("Notes: " + item.application().note()));
        }
        content.add(Box.createVerticalStrut(16));

        JPanel actions = UiFactory.flowPanel(FlowLayout.LEFT, 8, 8);
        actions.setOpaque(false);
        JButton viewProfileButton = UiFactory.lightButton("View profile");
        viewProfileButton.addActionListener(event -> showApplicantProfile(item));
        JButton openCvButton = UiFactory.lightButton("Open CV");
        openCvButton.setEnabled(!profile.cvStoredPath().isBlank());
        openCvButton.addActionListener(event -> openCv(profile.cvStoredPath()));
        JButton downloadCvButton = UiFactory.lightButton("Download CV");
        downloadCvButton.setEnabled(!profile.cvStoredPath().isBlank());
        downloadCvButton.addActionListener(event -> downloadCv(profile.cvStoredPath(), profile.cvOriginalFileName()));

        actions.add(viewProfileButton);
        actions.add(openCvButton);
        actions.add(downloadCvButton);
        if (item.application().status() == ApplicationStatus.REJECTED) {
            JButton reopenButton = UiFactory.secondaryButton("Reopen");
            reopenButton.addActionListener(event -> reopenApplication(job, item.application().id()));
            actions.add(reopenButton);
        } else if (item.application().status() != ApplicationStatus.SELECTED) {
            if (item.application().status() != ApplicationStatus.SHORTLISTED) {
                JButton shortlistButton = UiFactory.secondaryButton("Shortlist");
                shortlistButton.addActionListener(event -> shortlistApplicant(job, item.application().id()));
                actions.add(shortlistButton);
            }
            JButton interviewButton = UiFactory.secondaryButton("Schedule Interview");
            interviewButton.addActionListener(event -> scheduleInterview(job, item.application().id()));
            actions.add(interviewButton);
            if (item.application().status() == ApplicationStatus.INTERVIEW_SCHEDULED) {
                JButton cancelInterviewButton = UiFactory.lightButton("Cancel Interview");
                cancelInterviewButton.addActionListener(event -> cancelInterview(job, item.application().id()));
                actions.add(cancelInterviewButton);
            }
            JButton selectButton = UiFactory.primaryButton("Select");
            selectButton.addActionListener(event -> selectApplicant(job, item.application().id()));
            actions.add(selectButton);
            JButton rejectButton = UiFactory.lightButton("Reject");
            rejectButton.addActionListener(event -> rejectApplicant(job, item.application().id()));
            actions.add(rejectButton);
        }
        content.add(actions);

        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private JLabel statusPill(ApplicationStatus status) {
        return switch (status) {
            case APPLIED -> pillLabel(status.label(), new Color(210, 226, 247), Theme.PRIMARY_DARK);
            case UNDER_REVIEW -> pillLabel(status.label(), new Color(230, 236, 249), Theme.PRIMARY_DARK);
            case SHORTLISTED -> pillLabel(status.label(), new Color(235, 246, 214), new Color(67, 109, 21));
            case INTERVIEW_SCHEDULED -> pillLabel(status.label(), new Color(252, 238, 191), new Color(130, 79, 13));
            case SELECTED -> pillLabel(status.label(), new Color(208, 244, 220), new Color(29, 102, 68));
            case REJECTED -> pillLabel(status.label(), new Color(249, 217, 217), new Color(138, 42, 42));
            case WITHDRAWN -> pillLabel(status.label(), new Color(229, 231, 235), Theme.SUBTLE_TEXT);
        };
    }

    private ApplicationStatus selectedStatusFilter() {
        Object item = statusFilterBox.getSelectedItem();
        if (item == null || "All statuses".equalsIgnoreCase(item.toString())) {
            return null;
        }
        return ApplicationStatus.fromLabel(item.toString());
    }

    private int matchScore(JobPosting job, ApplicationService.ApplicantReviewItem item) {
        return analyticsService.getLocalJobMatchInsights(job.semester()).stream()
                .filter(insight -> insight.jobId().equals(job.id()))
                .flatMap(insight -> insight.applicants().stream())
                .filter(applicant -> applicant.applicationId().equals(item.application().id()))
                .mapToInt(AnalyticsService.ApplicantMatchInsight::matchScore)
                .findFirst()
                .orElse(item.fitScore());
    }

    private JLabel pillLabel(String text, Color background, Color foreground) {
        JLabel label = UiFactory.bodyLabel("  " + text + "  ");
        label.setOpaque(true);
        label.setBackground(background);
        label.setForeground(foreground);
        label.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        return label;
    }

    private JPanel labeledField(String labelText, Component component) {
        return labeledField(labelText, component, null);
    }

    private JPanel labeledField(String labelText, Component component, JLabel validationLabel) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(UiFactory.bodyLabel(labelText));
        panel.add(Box.createVerticalStrut(8));
        if (component instanceof javax.swing.JComponent jComponent
                && !(component instanceof JPanel)
                && !(component instanceof JScrollPane)
                && !(component instanceof JTextArea)) {
            UiFactory.fixedHeight(jComponent, 36);
        }
        panel.add(component);
        if (validationLabel != null) {
            panel.add(Box.createVerticalStrut(4));
            panel.add(validationLabel);
        }
        return panel;
    }

    private JPanel scrollField(String labelText, JTextArea area) {
        return scrollField(labelText, area, null);
    }

    private JPanel scrollField(String labelText, JTextArea area, JLabel validationLabel) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(UiFactory.bodyLabel(labelText));
        panel.add(Box.createVerticalStrut(8));
        JScrollPane scrollPane = new JScrollPane(area);
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1, true));
        scrollPane.setPreferredSize(new Dimension(220, area.getRows() <= 3 ? 92 : 126));
        panel.add(scrollPane);
        if (validationLabel != null) {
            panel.add(Box.createVerticalStrut(4));
            panel.add(validationLabel);
        }
        return panel;
    }

    private JTextArea readOnlyArea(int rows) {
        JTextArea area = UiFactory.textArea(rows);
        area.setEditable(false);
        area.setBackground(Theme.SURFACE_MUTED);
        area.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        UiFactory.fixedHeight(area, rows <= 4 ? 112 : 136);
        return area;
    }

    private void updatePreviewFromForm() {
        updatePostJobValidation(postJobValidationSubmitted);
        String role = roleField.getText().trim();
        String moduleCode = moduleCodeField.getText().trim();
        String moduleName = moduleNameField.getText().trim();
        String semester = semesterField.getText().trim();
        String hours = hoursPerWeekField.getText().trim();
        String openings = openingsField.getText().trim();
        String requiredSkills = requiredSkillsArea.getText().trim();
        String description = descriptionArea.getText().trim();

        previewTitleLabel.setText(role.isBlank() ? "Vacancy preview" : role);
        StringBuilder summaryBuilder = new StringBuilder();
        if (!moduleCode.isBlank()) {
            summaryBuilder.append(moduleCode);
        }
        if (!moduleName.isBlank()) {
            if (!summaryBuilder.isEmpty()) {
                summaryBuilder.append(" • ");
            }
            summaryBuilder.append(moduleName);
        }
        if (!semester.isBlank()) {
            if (!summaryBuilder.isEmpty()) {
                summaryBuilder.append(" • ");
            }
            summaryBuilder.append(semester);
        }
        if (!hours.isBlank()) {
            if (!summaryBuilder.isEmpty()) {
                summaryBuilder.append(" • ");
            }
            summaryBuilder.append(hours).append(" hrs/week");
        }
        if (!openings.isBlank()) {
            if (!summaryBuilder.isEmpty()) {
                summaryBuilder.append(" • ");
            }
            summaryBuilder.append(openings).append(" opening(s)");
        }
        previewSummaryLabel.setText(summaryBuilder.isEmpty()
                ? "Enter vacancy details and use Preview to confirm the listing."
                : summaryBuilder.toString());
        previewRequirementsArea.setText(requiredSkills.isBlank() ? "Required skills will appear here." : "• " + requiredSkills.replace(";", "\n• "));
        previewDescriptionArea.setText(description.isBlank() ? "Description will appear here." : description);
        previewRequirementsArea.setCaretPosition(0);
        previewDescriptionArea.setCaretPosition(0);
    }

    private void publishJob() {
        postJobValidationSubmitted = true;
        updatePostJobValidation(true);
        try {
            JobPosting savedJob = jobService.createJob(new JobPosting(
                    "",
                    roleField.getText(),
                    moduleCodeField.getText(),
                    moduleNameField.getText(),
                    semesterField.getText(),
                    hoursPerWeekField.getText(),
                    requiredSkillsArea.getText(),
                    tagsField.getText(),
                    descriptionArea.getText(),
                    parseOpenings()
            ));
            clearPostJobForm();
            refreshJobSelector(savedJob.id());
            updatePreview(savedJob);
            JOptionPane.showMessageDialog(this, "Job published successfully.");
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage());
        }
    }

    private int parseOpenings() {
        String value = openingsField.getText().trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Openings is required.");
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Openings must be a whole number.");
        }
    }

    private void clearPostJobForm() {
        roleField.setText("");
        hoursPerWeekField.setText("");
        moduleCodeField.setText("");
        moduleNameField.setText("");
        semesterField.setText("");
        requiredSkillsArea.setText("");
        descriptionArea.setText("");
        tagsField.setText("");
        openingsField.setText("");
        postJobValidationSubmitted = false;
        updatePostJobValidation(false);
    }

    private void attachPostJobValidation() {
        DocumentListener listener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                updatePreviewFromForm();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                updatePreviewFromForm();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                updatePreviewFromForm();
            }
        };
        for (JTextField field : List.of(roleField, hoursPerWeekField, moduleCodeField, moduleNameField, semesterField, tagsField, openingsField)) {
            field.getDocument().addDocumentListener(listener);
        }
        requiredSkillsArea.getDocument().addDocumentListener(listener);
        descriptionArea.getDocument().addDocumentListener(listener);
        updatePostJobValidation(false);
    }

    private void updatePostJobValidation(boolean showRequiredErrors) {
        setRequiredHint(roleValidationLabel, roleField.getText(), "Role is required.", "", showRequiredErrors);
        setPositiveIntegerHint(hoursValidationLabel, hoursPerWeekField.getText(), "Hours/week", "Positive whole number", showRequiredErrors);
        setRequiredHint(moduleCodeValidationLabel, moduleCodeField.getText(), "Module code is required.", "Example: CS101", showRequiredErrors);
        setRequiredHint(moduleNameValidationLabel, moduleNameField.getText(), "Module/activity is required.", "", showRequiredErrors);
        setRequiredHint(semesterValidationLabel, semesterField.getText(), "Semester is required.", "Example: Semester A", showRequiredErrors);
        setPositiveIntegerHint(openingsValidationLabel, openingsField.getText(), "Openings", "Positive whole number", showRequiredErrors);
        setStructuredTextHint(requiredSkillsValidationLabel, requiredSkillsArea.getText(), "Required skills are required.", "Separate skills with commas or semicolons", showRequiredErrors);
        setRequiredHint(descriptionValidationLabel, descriptionArea.getText(), "Description is required.", "", showRequiredErrors);
    }

    private void setRequiredHint(JLabel label, String value, String errorMessage, String helperMessage, boolean showRequiredError) {
        boolean blank = value == null || value.trim().isBlank();
        UiFactory.setValidationMessage(label, blank ? (showRequiredError ? errorMessage : helperMessage) : "", blank && showRequiredError);
    }

    private void setStructuredTextHint(JLabel label, String value, String errorMessage, String helperMessage, boolean showRequiredError) {
        String normalized = value == null ? "" : value.replace(",", "").replace(";", "").replace("|", "").trim();
        UiFactory.setValidationMessage(label, normalized.isBlank() ? (showRequiredError ? errorMessage : helperMessage) : "", normalized.isBlank() && showRequiredError);
    }

    private void setPositiveIntegerHint(JLabel label, String value, String fieldName, String helperMessage, boolean showRequiredError) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isBlank()) {
            UiFactory.setValidationMessage(label, showRequiredError ? fieldName + " is required." : helperMessage, showRequiredError);
            return;
        }
        try {
            int parsed = Integer.parseInt(trimmed);
            UiFactory.setValidationMessage(label, parsed <= 0 ? fieldName + " must be greater than 0." : "", parsed <= 0);
        } catch (NumberFormatException exception) {
            UiFactory.setValidationMessage(label, fieldName + " must be a whole number.", true);
        }
    }

    private void updatePreview(JobPosting job) {
        previewTitleLabel.setText(job.title());
        previewSummaryLabel.setText(job.moduleCode()
                + " • "
                + job.moduleName()
                + " • "
                + job.semester()
                + " • "
                + job.hoursPerWeek()
                + " hrs/week • "
                + job.openings()
                + " opening(s)");
        previewRequirementsArea.setText("• " + job.requiredSkills().replace("; ", "\n• "));
        previewDescriptionArea.setText(job.description());
    }

    private void refreshJobSelector(String selectedJobId) {
        suppressApplicantRefresh = true;
        jobSelector.removeAllItems();
        for (JobPosting job : jobService.getAllJobs()) {
            jobSelector.addItem(job);
        }
        if (selectedJobId != null) {
            for (int index = 0; index < jobSelector.getItemCount(); index++) {
                JobPosting job = jobSelector.getItemAt(index);
                if (job.id().equals(selectedJobId)) {
                    jobSelector.setSelectedIndex(index);
                    suppressApplicantRefresh = false;
                    return;
                }
            }
        }
        if (jobSelector.getItemCount() > 0) {
            jobSelector.setSelectedIndex(0);
        }
        suppressApplicantRefresh = false;
    }

    private void showApplicantProfile(ApplicationService.ApplicantReviewItem item) {
        ApplicantProfile profile = item.profile();
        JPanel panel = new JPanel(new GridLayout(0, 2, 14, 8));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(UiFactory.bodyLabel("Name"));
        panel.add(UiFactory.mutedLabel(item.applicant().displayName()));
        panel.add(UiFactory.bodyLabel("Email"));
        panel.add(UiFactory.mutedLabel(item.applicant().email()));
        panel.add(UiFactory.bodyLabel("Student ID"));
        panel.add(UiFactory.mutedLabel(blankAsDash(profile.studentId())));
        panel.add(UiFactory.bodyLabel("Programme"));
        panel.add(UiFactory.mutedLabel(blankAsDash(profile.programme())));
        panel.add(UiFactory.bodyLabel("Year"));
        panel.add(UiFactory.mutedLabel(blankAsDash(profile.year())));
        panel.add(UiFactory.bodyLabel("Skills"));
        panel.add(UiFactory.mutedLabel(blankAsDash(profile.skills())));
        panel.add(UiFactory.bodyLabel("Availability"));
        panel.add(UiFactory.mutedLabel(blankAsDash(profile.availability())));
        panel.add(UiFactory.bodyLabel("GPA"));
        panel.add(UiFactory.mutedLabel(blankAsDash(profile.gpa())));
        panel.add(UiFactory.bodyLabel("CV"));
        panel.add(UiFactory.mutedLabel(blankAsDash(profile.cvOriginalFileName())));
        panel.add(UiFactory.bodyLabel("Status"));
        panel.add(UiFactory.mutedLabel(item.application().status().label()));
        panel.add(UiFactory.bodyLabel("Applied at"));
        panel.add(UiFactory.mutedLabel(item.application().appliedAt().replace('T', ' ')));
        panel.add(UiFactory.bodyLabel("Interview"));
        panel.add(UiFactory.mutedLabel(blankAsDash(item.application().interviewAt().replace('T', ' '))));
        panel.add(UiFactory.bodyLabel("Note"));
        panel.add(UiFactory.mutedLabel(blankAsDash(item.application().note())));

        JOptionPane.showMessageDialog(this, panel, "Applicant Profile", JOptionPane.PLAIN_MESSAGE);
    }

    private String blankAsDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private void exportApplicantsForSelectedJob() {
        JobPosting selectedJob = (JobPosting) jobSelector.getSelectedItem();
        if (selectedJob == null) {
            JOptionPane.showMessageDialog(this, "Please select a vacancy before exporting.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File(safeFileName(selectedJob.title()) + "-applicants.csv"));
        int selection = chooser.showSaveDialog(this);
        if (selection != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            ApplicationService.ExportResult result = applicationService.exportApplicantsForJob(
                    selectedJob.id(),
                    Path.of(chooser.getSelectedFile().getAbsolutePath())
            );
            JOptionPane.showMessageDialog(this, "Export completed. " + result.rowCount() + " applicant row(s) written.");
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage());
        }
    }

    private String safeFileName(String value) {
        String normalized = value == null ? "applicants" : value.trim().replaceAll("[^A-Za-z0-9._-]+", "-");
        return normalized.isBlank() ? "applicants" : normalized;
    }

    private void openCv(String storedPath) {
        try {
            cvService.openCv(storedPath);
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage());
        }
    }

    private void downloadCv(String storedPath, String originalFileName) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File(originalFileName.isBlank() ? "cv.txt" : originalFileName));
        int selection = chooser.showSaveDialog(this);
        if (selection != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            cvService.exportCv(storedPath, Path.of(chooser.getSelectedFile().getAbsolutePath()));
            JOptionPane.showMessageDialog(this, "CV downloaded successfully.");
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage());
        }
    }

    private void selectApplicant(JobPosting job, String applicationId) {
        try {
            applicationService.selectApplicant(applicationId);
            refreshApplicantList();
            JOptionPane.showMessageDialog(this, "Applicant selected for " + job.title() + ".");
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage());
        }
    }

    private void shortlistApplicant(JobPosting job, String applicationId) {
        String note = promptForNote(
                "Shortlist Applicant",
                "Optional shortlist note for the applicant:",
                "Shortlisted for final review."
        );
        if (note == null) {
            return;
        }
        try {
            applicationService.shortlistApplicant(applicationId, note);
            refreshApplicantList();
            JOptionPane.showMessageDialog(this, "Applicant shortlisted for " + job.title() + ".");
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage());
        }
    }

    private void rejectApplicant(JobPosting job, String applicationId) {
        String note = promptForNote(
                "Reject Applicant",
                "Optional rejection feedback for the applicant:",
                "Thank you for applying. This application was not selected."
        );
        if (note == null) {
            return;
        }
        try {
            applicationService.rejectApplicant(applicationId, note);
            refreshApplicantList();
            JOptionPane.showMessageDialog(this, "Applicant rejected for " + job.title() + ".");
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage());
        }
    }

    private void reopenApplication(JobPosting job, String applicationId) {
        int result = JOptionPane.showOptionDialog(
                this,
                "Reopen this rejected application for further review?",
                "Reopen Application",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                new Object[]{"Reopen", "Cancel"},
                "Reopen"
        );
        if (result != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            applicationService.reopenApplication(applicationId);
            refreshApplicantList();
            JOptionPane.showMessageDialog(this, "Application reopened for " + job.title() + ".");
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage());
        }
    }

    private void scheduleInterview(JobPosting job, String applicationId) {
        JTextField dateTimeField = UiFactory.textField();
        dateTimeField.setText(LocalDateTime.now().plusDays(1).withMinute(0).withSecond(0).withNano(0).toString());
        JLabel interviewTimeValidationLabel = UiFactory.validationLabel();
        JTextArea noteArea = UiFactory.textArea(3);
        noteArea.setText("Interview scheduled. Please arrive 10 minutes early.");
        dateTimeField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                updateInterviewTimeValidation(dateTimeField, interviewTimeValidationLabel);
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                updateInterviewTimeValidation(dateTimeField, interviewTimeValidationLabel);
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                updateInterviewTimeValidation(dateTimeField, interviewTimeValidationLabel);
            }
        });
        updateInterviewTimeValidation(dateTimeField, interviewTimeValidationLabel);

        JPanel form = new JPanel(new GridLayout(0, 1, 0, 10));
        form.setOpaque(false);
        form.add(labeledField("Interview time (yyyy-MM-ddTHH:mm)", dateTimeField, interviewTimeValidationLabel));
        form.add(UiFactory.mutedLabel("Example: 2026-05-24T15:00. Full-width colon will be converted automatically."));
        form.add(scrollField("Applicant note", noteArea));

        int result = JOptionPane.showOptionDialog(
                this,
                form,
                "Schedule Interview",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                new Object[]{"Schedule interview", "Cancel"},
                "Schedule interview"
        );
        if (result != JOptionPane.OK_OPTION) {
            return;
        }
        if (!updateInterviewTimeValidation(dateTimeField, interviewTimeValidationLabel)) {
            JOptionPane.showMessageDialog(this, "Please fix the interview time before scheduling.");
            return;
        }

        try {
            applicationService.scheduleInterview(applicationId, dateTimeField.getText().replace('：', ':'), noteArea.getText());
            refreshApplicantList();
            JOptionPane.showMessageDialog(this, "Interview scheduled for " + job.title() + ".");
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage());
        }
    }

    private void cancelInterview(JobPosting job, String applicationId) {
        int result = JOptionPane.showOptionDialog(
                this,
                "Cancel this interview and return the application to review?",
                "Cancel Interview",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                new Object[]{"Cancel interview", "Keep interview"},
                "Cancel interview"
        );
        if (result != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            applicationService.cancelInterview(applicationId);
            refreshApplicantList();
            JOptionPane.showMessageDialog(this, "Interview cancelled for " + job.title() + ".");
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage());
        }
    }

    private boolean updateInterviewTimeValidation(JTextField dateTimeField, JLabel validationLabel) {
        String value = dateTimeField.getText().trim().replace('：', ':');
        if (value.isBlank()) {
            UiFactory.setValidationMessage(validationLabel, "Interview time is required.", true);
            return false;
        }
        try {
            LocalDateTime.parse(value);
            UiFactory.setValidationMessage(validationLabel, "", false);
            return true;
        } catch (RuntimeException exception) {
            UiFactory.setValidationMessage(validationLabel, "Use yyyy-MM-ddTHH:mm, e.g. 2026-05-24T15:00.", true);
            return false;
        }
    }

    private String promptForNote(String title, String label, String defaultText) {
        JTextArea noteArea = UiFactory.textArea(4);
        noteArea.setText(defaultText);
        int result = JOptionPane.showConfirmDialog(
                this,
                scrollField(label, noteArea),
                title,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        return result == JOptionPane.OK_OPTION ? noteArea.getText() : null;
    }

    private void showPage(String page) {
        pageLayout.show(pagePanel, page);
    }
}
