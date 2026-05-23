package com.group44.tarecruit.ui;

import com.group44.tarecruit.model.ActivityLogItem;
import com.group44.tarecruit.model.UserAccount;
import com.group44.tarecruit.service.AnalyticsService;
import com.group44.tarecruit.service.WorkloadService;
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
import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AdminWorkspacePanel extends JPanel {
    private static final String WORKLOAD_PAGE = "workload";
    private static final String MATCHING_PAGE = "matching";
    private static final String SUGGESTIONS_PAGE = "suggestions";
    private static final String EXPORT_PAGE = "export";
    private static final String LOGS_PAGE = "logs";
    private static final String ALL_LOG_CATEGORIES = "All categories";

    private final WorkloadService workloadService;
    private final AnalyticsService analyticsService;
    private final Runnable accountAction;
    private final Runnable logoutAction;

    private final CardLayout pageLayout;
    private final JPanel pagePanel;
    private final JComboBox<String> workloadSemesterFilterBox;
    private final JLabel workloadIntroLabel;
    private final JPanel summaryListPanel;
    private final JLabel detailHeaderLabel;
    private final JLabel detailMetaLabel;
    private final JTextArea detailArea;
    private final JComboBox<String> matchingSemesterFilterBox;
    private final JLabel matchingFallbackLabel;
    private final JPanel matchingListPanel;
    private final JComboBox<String> suggestionsSemesterFilterBox;
    private final JLabel suggestionsFallbackLabel;
    private final JPanel suggestionsListPanel;
    private final JComboBox<String> exportSemesterFilterBox;
    private final JLabel exportSummaryLabel;
    private final JPanel exportPreviewPanel;
    private final JComboBox<String> logCategoryFilterBox;
    private final JTextField logSearchField;
    private final JPanel logsListPanel;

    private UserAccount currentUser;
    private boolean suppressLogRefresh;

    public AdminWorkspacePanel(
            WorkloadService workloadService,
            AnalyticsService analyticsService,
            Runnable accountAction,
            Runnable logoutAction
    ) {
        this.workloadService = workloadService;
        this.analyticsService = analyticsService;
        this.accountAction = accountAction;
        this.logoutAction = logoutAction;

        setLayout(new BorderLayout());
        setBackground(Theme.APP_BACKGROUND);
        add(buildSidebar(), BorderLayout.WEST);

        pageLayout = new CardLayout();
        pagePanel = new JPanel(pageLayout);
        pagePanel.setOpaque(false);

        workloadSemesterFilterBox = semesterFilterBox(this::refreshWorkload);
        workloadIntroLabel = UiFactory.mutedLabel("Monitor selected TA workload by semester and inspect assignment details.");
        summaryListPanel = verticalPanel();
        detailHeaderLabel = UiFactory.sectionLabel("Workload details");
        detailMetaLabel = UiFactory.mutedLabel("Select a summary to inspect selected TA assignments.");
        detailArea = readOnlyArea(12);

        matchingSemesterFilterBox = semesterFilterBox(this::refreshMatching);
        matchingFallbackLabel = UiFactory.mutedLabel("");
        matchingListPanel = verticalPanel();

        suggestionsSemesterFilterBox = semesterFilterBox(this::refreshSuggestions);
        suggestionsFallbackLabel = UiFactory.mutedLabel("");
        suggestionsListPanel = verticalPanel();

        exportSemesterFilterBox = semesterFilterBox(this::refreshExportSummary);
        exportSummaryLabel = UiFactory.mutedLabel("Export the current applicant analytics view as CSV.");
        exportPreviewPanel = verticalPanel();

        logCategoryFilterBox = new JComboBox<>();
        logCategoryFilterBox.setFont(Theme.BODY_FONT);
        logCategoryFilterBox.addActionListener(event -> {
            if (!suppressLogRefresh) {
                refreshLogs();
            }
        });
        logSearchField = UiFactory.textField();
        logSearchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                refreshLogs();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                refreshLogs();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                refreshLogs();
            }
        });
        logsListPanel = verticalPanel();

        pagePanel.add(UiFactory.scrollPane(buildWorkloadPage()), WORKLOAD_PAGE);
        pagePanel.add(UiFactory.scrollPane(buildMatchingPage()), MATCHING_PAGE);
        pagePanel.add(buildSuggestionsPage(), SUGGESTIONS_PAGE);
        pagePanel.add(UiFactory.scrollPane(buildExportPage()), EXPORT_PAGE);
        pagePanel.add(buildLogsPage(), LOGS_PAGE);
        add(pagePanel, BorderLayout.CENTER);
    }

    public void setCurrentUser(UserAccount currentUser) {
        this.currentUser = currentUser;
    }

    public void refreshAll() {
        refreshIntroLabels();
        reloadSemesterFilters();
        refreshWorkload();
        refreshMatching();
        refreshSuggestions();
        refreshExportSummary();
        refreshLogs();
        showPage(WORKLOAD_PAGE);
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(Theme.SURFACE);
        sidebar.setBorder(BorderFactory.createEmptyBorder(16, 14, 16, 14));
        sidebar.setPreferredSize(new Dimension(176, 620));

        sidebar.add(UiFactory.sectionLabel("TA Recruit"));
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(UiFactory.mutedLabel("Admin recruitment analytics"));
        sidebar.add(Box.createVerticalStrut(28));
        sidebar.add(navButton("Workload", WORKLOAD_PAGE));
        sidebar.add(Box.createVerticalStrut(12));
        sidebar.add(navButton("Skill Matching", MATCHING_PAGE));
        sidebar.add(Box.createVerticalStrut(12));
        sidebar.add(navButton("AI Suggestions", SUGGESTIONS_PAGE));
        sidebar.add(Box.createVerticalStrut(12));
        sidebar.add(navButton("Export", EXPORT_PAGE));
        sidebar.add(Box.createVerticalStrut(12));
        sidebar.add(navButton("Logs", LOGS_PAGE));
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

    private JPanel buildWorkloadPage() {
        JPanel page = pageWrapper();
        page.add(UiFactory.titleLabel("Admin Workload"));
        page.add(Box.createVerticalStrut(8));
        page.add(workloadIntroLabel);
        page.add(Box.createVerticalStrut(24));
        page.add(filterCard("Semester", workloadSemesterFilterBox, this::refreshWorkload));
        page.add(Box.createVerticalStrut(20));

        JPanel contentGrid = new JPanel(new GridLayout(1, 2, 10, 0));
        contentGrid.setOpaque(false);

        JPanel summaryCard = UiFactory.card();
        summaryCard.add(sectionWithBody("Selected TA load", summaryListPanel), BorderLayout.CENTER);

        JPanel detailCard = UiFactory.card();
        JPanel detailBody = verticalPanel();
        detailBody.add(detailHeaderLabel);
        detailBody.add(Box.createVerticalStrut(8));
        detailBody.add(detailMetaLabel);
        detailBody.add(Box.createVerticalStrut(16));
        detailBody.add(detailArea);
        detailCard.add(detailBody, BorderLayout.CENTER);

        contentGrid.add(summaryCard);
        contentGrid.add(detailCard);
        page.add(contentGrid);
        return page;
    }

    private JPanel buildMatchingPage() {
        JPanel page = pageWrapper();
        page.add(UiFactory.titleLabel("Skill Matching"));
        page.add(Box.createVerticalStrut(8));
        page.add(UiFactory.mutedLabel("Rank applicants by skill fit and workload risk."));
        page.add(Box.createVerticalStrut(6));
        page.add(matchingFallbackLabel);
        page.add(Box.createVerticalStrut(24));
        page.add(filterCard("Semester", matchingSemesterFilterBox, this::refreshMatching));
        page.add(Box.createVerticalStrut(20));
        page.add(matchingListPanel);
        return page;
    }

    private JPanel buildSuggestionsPage() {
        JPanel page = new JPanel(new BorderLayout(0, 14));
        page.setOpaque(false);
        page.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel top = verticalPanel();
        top.add(UiFactory.titleLabel("AI Workload Suggestions"));
        top.add(Box.createVerticalStrut(6));
        top.add(UiFactory.mutedLabel("Surface staffing risks and practical next actions before offers are made."));
        top.add(Box.createVerticalStrut(6));
        top.add(suggestionsFallbackLabel);
        top.add(Box.createVerticalStrut(14));
        top.add(filterCard("Semester", suggestionsSemesterFilterBox, this::refreshSuggestions));
        page.add(top, BorderLayout.NORTH);
        page.add(UiFactory.scrollPane(suggestionsListPanel), BorderLayout.CENTER);
        return page;
    }

    private JPanel buildExportPage() {
        JPanel page = pageWrapper();
        page.add(UiFactory.titleLabel("Export Applicant List"));
        page.add(Box.createVerticalStrut(8));
        page.add(UiFactory.mutedLabel("Create a CSV snapshot of the current applicant analytics so you can analyse recruitment outside the app."));
        page.add(Box.createVerticalStrut(24));

        JPanel controlsCard = UiFactory.card();
        JPanel controls = new JPanel(new GridLayout(1, 3, 8, 0));
        controls.setOpaque(false);
        controls.add(labeledField("Semester", exportSemesterFilterBox));

        JPanel summaryField = verticalPanel();
        summaryField.add(UiFactory.bodyLabel("Current export view"));
        summaryField.add(Box.createVerticalStrut(8));
        summaryField.add(exportSummaryLabel);
        controls.add(summaryField);

        JPanel actionField = verticalPanel();
        actionField.add(UiFactory.bodyLabel("Action"));
        actionField.add(Box.createVerticalStrut(8));
        JButton exportButton = UiFactory.primaryButton("Export CSV");
        exportButton.addActionListener(event -> exportApplicantList());
        actionField.add(exportButton);
        controls.add(actionField);
        controlsCard.add(controls, BorderLayout.CENTER);
        page.add(controlsCard);
        page.add(Box.createVerticalStrut(20));

        JPanel previewCard = UiFactory.card();
        previewCard.add(sectionWithBody("Export preview", exportPreviewPanel), BorderLayout.CENTER);
        page.add(previewCard);
        return page;
    }

    private JPanel buildLogsPage() {
        JPanel page = new JPanel(new BorderLayout(0, 14));
        page.setOpaque(false);
        page.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel top = verticalPanel();
        top.add(UiFactory.titleLabel("System Notification Logs"));
        top.add(Box.createVerticalStrut(6));
        top.add(UiFactory.mutedLabel("Track the latest 30 profile, notification, export, and vacancy activity logs."));
        top.add(Box.createVerticalStrut(14));

        JPanel filterCard = UiFactory.card();
        JPanel actions = new JPanel(new GridLayout(1, 4, 10, 0));
        actions.setOpaque(false);
        actions.add(labeledField("Category", logCategoryFilterBox));
        actions.add(labeledField("Search logs", logSearchField));
        JButton refreshButton = UiFactory.lightButton("Refresh logs");
        refreshButton.addActionListener(event -> refreshLogs());
        JButton clearButton = UiFactory.lightButton("Clear filters");
        clearButton.addActionListener(event -> clearLogFilters());
        JPanel actionField = verticalPanel();
        actionField.add(UiFactory.bodyLabel("Actions"));
        actionField.add(Box.createVerticalStrut(8));
        JPanel buttonRow = UiFactory.flowPanel(FlowLayout.LEFT, 8, 0);
        buttonRow.add(refreshButton);
        buttonRow.add(clearButton);
        actionField.add(buttonRow);
        actions.add(actionField);
        actions.add(Box.createHorizontalGlue());
        filterCard.add(actions, BorderLayout.CENTER);
        top.add(filterCard);
        page.add(top, BorderLayout.NORTH);
        page.add(UiFactory.scrollPane(logsListPanel), BorderLayout.CENTER);
        return page;
    }

    private void refreshIntroLabels() {
        workloadIntroLabel.setText(currentUser == null
                ? "Monitor selected TA workload by semester and inspect assignment details."
                : "Signed in as " + currentUser.displayName() + ". Monitor selected TA workload by semester.");
        updateAiFallbackLabels();
    }

    private JButton navButton(String label, String page) {
        JButton button = UiFactory.navButton(label);
        button.addActionListener(event -> showPage(page));
        return button;
    }

    private void updateAiFallbackLabels() {
        String message = analyticsService.isAiFallbackActive()
                ? "AI service unavailable. Showing rule-based analysis."
                : "";
        matchingFallbackLabel.setText(message);
        matchingFallbackLabel.setVisible(!message.isBlank());
        suggestionsFallbackLabel.setText(message);
        suggestionsFallbackLabel.setVisible(!message.isBlank());
    }

    private void reloadSemesterFilters() {
        List<String> semesters = workloadService.availableSemesters();
        reloadSemesterFilter(workloadSemesterFilterBox, semesters, true);
        reloadSemesterFilter(matchingSemesterFilterBox, semesters, true);
        reloadSemesterFilter(suggestionsSemesterFilterBox, semesters, true);
        reloadSemesterFilter(exportSemesterFilterBox, semesters, true);
    }

    private void reloadSemesterFilter(JComboBox<String> comboBox, List<String> semesters, boolean includeCurrentSemester) {
        Object currentSelection = comboBox.getSelectedItem();
        comboBox.removeAllItems();
        if (includeCurrentSemester) {
            comboBox.addItem(WorkloadService.CURRENT_SEMESTER_FILTER);
        }
        comboBox.addItem(WorkloadService.ALL_SEMESTERS_FILTER);
        for (String semester : semesters) {
            if (!WorkloadService.CURRENT_SEMESTER_FILTER.equalsIgnoreCase(semester)
                    && !WorkloadService.ALL_SEMESTERS_FILTER.equalsIgnoreCase(semester)) {
                comboBox.addItem(semester);
            }
        }
        if (currentSelection != null) {
            comboBox.setSelectedItem(currentSelection);
        }
        if (comboBox.getSelectedItem() == null && comboBox.getItemCount() > 0) {
            comboBox.setSelectedIndex(0);
        }
    }

    private void refreshWorkload() {
        String filter = selectedFilter(workloadSemesterFilterBox);
        List<WorkloadService.WorkloadSummary> summaries = workloadService.getWorkload(filter);

        summaryListPanel.removeAll();
        if (summaries.isEmpty()) {
            summaryListPanel.add(UiFactory.mutedLabel("No selected TA assignments are available for this view."));
            showSummaryDetail(null);
        } else {
            for (int index = 0; index < summaries.size(); index++) {
                summaryListPanel.add(workloadSummaryCard(summaries.get(index)));
                if (index < summaries.size() - 1) {
                    summaryListPanel.add(Box.createVerticalStrut(12));
                }
            }
            showSummaryDetail(summaries.getFirst());
        }
        summaryListPanel.revalidate();
        summaryListPanel.repaint();
    }

    private void refreshMatching() {
        updateAiFallbackLabels();
        List<Component> cards = analyticsService.getLocalJobMatchInsights(selectedFilter(matchingSemesterFilterBox)).stream()
                .map(this::jobMatchCard)
                .toList();
        rebuildVerticalList(
                matchingListPanel,
                cards,
                "No jobs or applicants are available for the selected view."
        );
    }

    private void refreshSuggestions() {
        updateAiFallbackLabels();
        List<Component> cards = analyticsService.getLocalWorkloadSuggestions(selectedFilter(suggestionsSemesterFilterBox)).stream()
                .map(this::suggestionCard)
                .toList();
        rebuildVerticalList(
                suggestionsListPanel,
                cards,
                "No balancing suggestions are needed right now."
        );
    }

    private void refreshExportSummary() {
        updateAiFallbackLabels();
        List<AnalyticsService.JobMatchInsight> insights = analyticsService.getLocalJobMatchInsights(selectedFilter(exportSemesterFilterBox));
        long applicantRows = insights.stream()
                .mapToLong(insight -> insight.applicants().size())
                .sum();
        exportSummaryLabel.setText(insights.isEmpty()
                ? "No vacancies are available for this export."
                : applicantRows + " applicant row(s) across " + insights.size() + " vacancy view(s) will be exported.");

        exportPreviewPanel.removeAll();
        if (insights.isEmpty()) {
            exportPreviewPanel.add(UiFactory.mutedLabel("No preview data is available."));
        } else {
            for (int index = 0; index < Math.min(3, insights.size()); index++) {
                exportPreviewPanel.add(exportPreviewCard(insights.get(index)));
                if (index < Math.min(3, insights.size()) - 1) {
                    exportPreviewPanel.add(Box.createVerticalStrut(12));
                }
            }
        }
        exportPreviewPanel.revalidate();
        exportPreviewPanel.repaint();
    }

    private void refreshLogs() {
        List<ActivityLogItem> logs = analyticsService.getSystemLogs();
        reloadLogCategoryFilter(logs);
        String selectedCategory = selectedLogCategory();
        String query = logSearchField.getText().trim().toLowerCase(Locale.ROOT);
        rebuildVerticalList(
                logsListPanel,
                logs.stream()
                        .filter(item -> ALL_LOG_CATEGORIES.equals(selectedCategory)
                                || item.category().equalsIgnoreCase(selectedCategory))
                        .filter(item -> matchesLogQuery(item, query))
                        .map(this::logCard)
                        .toList(),
                hasActiveLogFilters() ? "No logs match the selected filters." : "No activity logs are available yet."
        );
    }

    private void reloadLogCategoryFilter(List<ActivityLogItem> logs) {
        Object previousSelection = logCategoryFilterBox.getSelectedItem();
        suppressLogRefresh = true;
        logCategoryFilterBox.removeAllItems();
        logCategoryFilterBox.addItem(ALL_LOG_CATEGORIES);
        Set<String> categories = new LinkedHashSet<>();
        logs.stream()
                .map(ActivityLogItem::category)
                .filter(category -> !category.isBlank())
                .sorted(String::compareToIgnoreCase)
                .forEach(categories::add);
        categories.forEach(logCategoryFilterBox::addItem);
        if (previousSelection != null) {
            logCategoryFilterBox.setSelectedItem(previousSelection);
        }
        if (logCategoryFilterBox.getSelectedItem() == null) {
            logCategoryFilterBox.setSelectedItem(ALL_LOG_CATEGORIES);
        }
        suppressLogRefresh = false;
    }

    private String selectedLogCategory() {
        Object selected = logCategoryFilterBox.getSelectedItem();
        return selected == null ? ALL_LOG_CATEGORIES : selected.toString();
    }

    private boolean matchesLogQuery(ActivityLogItem item, String query) {
        if (query.isBlank()) {
            return true;
        }
        String searchableText = String.join(" ",
                item.category(),
                item.actorUserId(),
                item.targetUserId(),
                item.title(),
                item.message(),
                item.createdAt()
        ).toLowerCase(Locale.ROOT);
        return searchableText.contains(query);
    }

    private boolean hasActiveLogFilters() {
        return !ALL_LOG_CATEGORIES.equals(selectedLogCategory())
                || !logSearchField.getText().isBlank();
    }

    private void clearLogFilters() {
        logSearchField.setText("");
        logCategoryFilterBox.setSelectedItem(ALL_LOG_CATEGORIES);
        refreshLogs();
    }

    private JPanel workloadSummaryCard(WorkloadService.WorkloadSummary summary) {
        JPanel card = UiFactory.card();
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 132));

        JPanel content = verticalPanel();
        JLabel nameLabel = UiFactory.bodyLabel(summary.summaryLine());
        nameLabel.setFont(Theme.BUTTON_FONT);
        content.add(nameLabel);
        content.add(Box.createVerticalStrut(6));
        content.add(UiFactory.mutedLabel(summary.selectedRoles() + " selected role(s) in " + summary.semester()));
        content.add(Box.createVerticalStrut(14));

        JPanel actions = UiFactory.flowPanel(FlowLayout.LEFT, 10, 0);
        JButton viewDetailsButton = UiFactory.primaryButton("View details");
        viewDetailsButton.addActionListener(event -> showSummaryDetail(summary));
        actions.add(viewDetailsButton);
        content.add(actions);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private Component jobMatchCard(AnalyticsService.JobMatchInsight insight) {
        JPanel card = UiFactory.card();
        JPanel content = verticalPanel();

        JLabel title = UiFactory.sectionLabel(insight.jobTitle());
        content.add(title);
        content.add(Box.createVerticalStrut(6));
        content.add(UiFactory.mutedLabel(insight.moduleCode() + " • " + insight.moduleName() + " • " + insight.semester()));
        content.add(Box.createVerticalStrut(8));
        content.add(UiFactory.bodyLabel("<html><b>Required skills:</b> " + insight.requiredSkills() + "</html>"));
        content.add(Box.createVerticalStrut(8));
        content.add(UiFactory.mutedLabel(insight.summary()));
        content.add(Box.createVerticalStrut(14));

        if (insight.applicants().isEmpty()) {
            content.add(UiFactory.mutedLabel("No applicants to compare yet."));
        } else {
            for (int index = 0; index < insight.applicants().size(); index++) {
                content.add(applicantMatchCard(insight.applicants().get(index)));
                if (index < insight.applicants().size() - 1) {
                    content.add(Box.createVerticalStrut(10));
                }
            }
        }
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private Component applicantMatchCard(AnalyticsService.ApplicantMatchInsight insight) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(true);
        panel.setBackground(Theme.SURFACE_MUTED);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1, true),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)
        ));

        JPanel content = verticalPanel();
        JLabel title = UiFactory.bodyLabel(insight.applicantName());
        title.setFont(Theme.BUTTON_FONT);
        content.add(title);
        content.add(Box.createVerticalStrut(6));

        JPanel meta = UiFactory.flowPanel(FlowLayout.LEFT, 8, 0);
        meta.add(pillLabel(insight.matchScore() + "% match", Theme.PRIMARY_DARK, Color.WHITE));
        meta.add(pillLabel(insight.statusLabel(), Theme.ACCENT, Color.WHITE));
        meta.add(pillLabel(insight.currentWorkloadHours() + " hrs/week selected", Theme.SURFACE, Theme.TEXT));
        content.add(meta);
        content.add(Box.createVerticalStrut(8));
        content.add(UiFactory.mutedLabel(insight.studentId().isBlank() ? "Student ID not available." : "Student ID: " + insight.studentId()));
        content.add(Box.createVerticalStrut(8));
        content.add(UiFactory.bodyLabel("<html><b>Matched:</b> " + formatList(insight.matchedSkills(), "No direct overlaps detected") + "</html>"));
        content.add(Box.createVerticalStrut(4));
        content.add(UiFactory.bodyLabel("<html><b>Missing:</b> " + formatList(insight.missingSkills(), "No missing skills identified") + "</html>"));
        content.add(Box.createVerticalStrut(6));
        content.add(UiFactory.mutedLabel(insight.explanation()));
        if (!insight.note().isBlank()) {
            content.add(Box.createVerticalStrut(6));
            content.add(UiFactory.mutedLabel("Latest note: " + insight.note()));
        }
        if (!insight.interviewAt().isBlank()) {
            content.add(Box.createVerticalStrut(6));
            content.add(UiFactory.mutedLabel("Interview: " + insight.interviewAt().replace('T', ' ')));
        }
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private Component suggestionCard(AnalyticsService.WorkloadSuggestion suggestion) {
        JPanel card = UiFactory.card();
        JPanel content = verticalPanel();
        JLabel title = UiFactory.sectionLabel(suggestion.subject());
        content.add(title);
        content.add(Box.createVerticalStrut(6));

        JPanel tagRow = UiFactory.flowPanel(FlowLayout.LEFT, 8, 0);
        tagRow.add(pillLabel(suggestion.category(), Theme.ACCENT, Color.WHITE));
        content.add(tagRow);
        content.add(Box.createVerticalStrut(10));
        content.add(UiFactory.bodyLabel("<html>" + suggestion.summary() + "</html>"));
        content.add(Box.createVerticalStrut(8));
        content.add(UiFactory.bodyLabel("<html><b>Recommendation:</b> " + suggestion.recommendation() + "</html>"));
        if (!suggestion.supportPoints().isEmpty()) {
            content.add(Box.createVerticalStrut(10));
            content.add(UiFactory.bodyLabel("<html><b>Support points:</b> " + String.join(" | ", suggestion.supportPoints()) + "</html>"));
        }
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private Component exportPreviewCard(AnalyticsService.JobMatchInsight insight) {
        JPanel card = UiFactory.card();
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 128));
        JPanel content = verticalPanel();
        JLabel title = UiFactory.bodyLabel(insight.jobTitle());
        title.setFont(Theme.BUTTON_FONT);
        content.add(title);
        content.add(Box.createVerticalStrut(6));
        content.add(UiFactory.mutedLabel(insight.moduleCode() + " • " + insight.semester()));
        content.add(Box.createVerticalStrut(8));
        content.add(UiFactory.bodyLabel(insight.applicants().isEmpty()
                ? "No applicants in this vacancy yet."
                : insight.applicants().size() + " applicant(s) will be included for this vacancy."));
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private Component logCard(ActivityLogItem item) {
        JPanel card = UiFactory.card();
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 136));
        JPanel content = verticalPanel();
        JLabel title = UiFactory.bodyLabel(item.title());
        title.setFont(Theme.BUTTON_FONT);
        content.add(title);
        content.add(Box.createVerticalStrut(6));

        JPanel meta = UiFactory.flowPanel(FlowLayout.LEFT, 8, 0);
        meta.add(pillLabel(item.category(), Theme.PRIMARY_DARK, Color.WHITE));
        if (!item.targetUserId().isBlank()) {
            meta.add(pillLabel("Target: " + item.targetUserId(), Theme.SURFACE_MUTED, Theme.TEXT));
        }
        content.add(meta);
        content.add(Box.createVerticalStrut(8));
        content.add(UiFactory.bodyLabel("<html>" + item.message() + "</html>"));
        content.add(Box.createVerticalStrut(8));
        content.add(UiFactory.mutedLabel(item.createdAt().replace('T', ' ')));
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private void showSummaryDetail(WorkloadService.WorkloadSummary summary) {
        if (summary == null) {
            detailHeaderLabel.setText("Workload details");
            detailMetaLabel.setText("Select a summary to inspect selected TA assignments.");
            detailArea.setText("No workload data available.");
            return;
        }

        detailHeaderLabel.setText(summary.displayName());
        detailMetaLabel.setText(summary.totalHoursPerWeek()
                + " hrs/week across "
                + summary.selectedRoles()
                + " selected role(s) for "
                + summary.semester());

        StringBuilder builder = new StringBuilder();
        for (WorkloadService.WorkloadAssignment assignment : summary.assignments()) {
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append(assignment.jobTitle())
                    .append(" (")
                    .append(assignment.moduleCode())
                    .append(")\n")
                    .append(assignment.moduleName())
                    .append("\n")
                    .append(assignment.hoursPerWeek())
                    .append(" hrs/week • ")
                    .append(assignment.semester());
        }
        detailArea.setText(builder.toString());
        detailArea.setCaretPosition(0);
    }

    private void exportApplicantList() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("applicant-analytics.csv"));
        int selection = chooser.showSaveDialog(this);
        if (selection != JFileChooser.APPROVE_OPTION) {
            return;
        }

        try {
            AnalyticsService.ExportResult result = analyticsService.exportApplicantList(
                    Path.of(chooser.getSelectedFile().getAbsolutePath()),
                    selectedFilter(exportSemesterFilterBox),
                    currentUser == null ? "" : currentUser.id()
            );
            refreshLogs();
            JOptionPane.showMessageDialog(this, "Export completed successfully. " + result.rowCount() + " row(s) written.");
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage());
        }
    }

    private JPanel filterCard(String labelText, JComboBox<String> comboBox, Runnable refreshAction) {
        JPanel card = UiFactory.card();
        JPanel row = UiFactory.flowPanel(FlowLayout.LEFT, 12, 0);
        row.add(UiFactory.bodyLabel(labelText));
        row.add(comboBox);
        JButton refreshButton = UiFactory.lightButton("Refresh");
        refreshButton.addActionListener(event -> refreshAction.run());
        row.add(refreshButton);
        card.add(row, BorderLayout.CENTER);
        return card;
    }

    private JComboBox<String> semesterFilterBox(Runnable refreshAction) {
        JComboBox<String> comboBox = new JComboBox<>();
        comboBox.setFont(Theme.BODY_FONT);
        comboBox.addActionListener(event -> refreshAction.run());
        return comboBox;
    }

    private JPanel pageWrapper() {
        JPanel page = new JPanel();
        page.setOpaque(false);
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
        page.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        return page;
    }

    private JPanel sectionWithBody(String title, JPanel contentBody) {
        JPanel panel = verticalPanel();
        panel.add(UiFactory.sectionLabel(title));
        panel.add(Box.createVerticalStrut(16));
        panel.add(contentBody);
        return panel;
    }

    private JPanel labeledField(String labelText, Component field) {
        JPanel panel = verticalPanel();
        panel.add(UiFactory.bodyLabel(labelText));
        panel.add(Box.createVerticalStrut(8));
        panel.add(field);
        return panel;
    }

    private JPanel verticalPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    private JTextArea readOnlyArea(int rows) {
        JTextArea area = UiFactory.textArea(rows);
        area.setEditable(false);
        area.setBackground(Theme.SURFACE_MUTED);
        area.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        return area;
    }

    private JLabel pillLabel(String text, Color background, Color foreground) {
        JLabel label = UiFactory.bodyLabel("  " + text + "  ");
        label.setOpaque(true);
        label.setBackground(background);
        label.setForeground(foreground);
        label.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        return label;
    }

    private String selectedFilter(JComboBox<String> comboBox) {
        Object item = comboBox.getSelectedItem();
        return item == null ? WorkloadService.ALL_SEMESTERS_FILTER : item.toString();
    }

    private String formatList(List<String> items, String fallback) {
        return items.isEmpty() ? fallback : String.join(" | ", items);
    }

    private void showPage(String page) {
        pageLayout.show(pagePanel, page);
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
