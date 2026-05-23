package com.group44.tarecruit.ui;

import com.group44.tarecruit.data.AppPaths;
import com.group44.tarecruit.data.ActivityLogRepository;
import com.group44.tarecruit.data.ApplicationRepository;
import com.group44.tarecruit.data.JobRepository;
import com.group44.tarecruit.data.NotificationRepository;
import com.group44.tarecruit.data.ProfileRepository;
import com.group44.tarecruit.data.SavedJobRepository;
import com.group44.tarecruit.data.SeedDataInitializer;
import com.group44.tarecruit.data.UserRepository;
import com.group44.tarecruit.model.Role;
import com.group44.tarecruit.model.UserAccount;
import com.group44.tarecruit.service.ApplicationService;
import com.group44.tarecruit.service.ActivityLogService;
import com.group44.tarecruit.service.AiConfiguration;
import com.group44.tarecruit.service.AiConfigurationLoader;
import com.group44.tarecruit.service.AnalyticsService;
import com.group44.tarecruit.service.AuthService;
import com.group44.tarecruit.service.CvService;
import com.group44.tarecruit.service.DisabledLlmJsonService;
import com.group44.tarecruit.service.JobService;
import com.group44.tarecruit.service.LlmJsonService;
import com.group44.tarecruit.service.NotificationService;
import com.group44.tarecruit.service.OpenAiCompatibleLlmJsonService;
import com.group44.tarecruit.service.ProfileService;
import com.group44.tarecruit.service.SavedJobService;
import com.group44.tarecruit.service.WorkloadService;
import com.group44.tarecruit.ui.components.Theme;
import com.group44.tarecruit.ui.components.UiFactory;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.CardLayout;
import java.nio.file.Path;

public class AppFrame extends JFrame {
    private static final String LOGIN_CARD = "login";
    private static final String APPLICANT_CARD = "applicant";
    private static final String ORGANISER_CARD = "organiser";
    private static final String ADMIN_CARD = "admin";

    private final AuthService authService;
    private final ProfileService profileService;
    private final JobService jobService;
    private final ApplicationService applicationService;
    private final NotificationService notificationService;
    private final CvService cvService;
    private final WorkloadService workloadService;
    private final AnalyticsService analyticsService;
    private final SavedJobService savedJobService;
    private final JPanel rootPanel;
    private final CardLayout cardLayout;
    private final LoginPanel loginPanel;
    private final ApplicantWorkspacePanel applicantWorkspacePanel;
    private final OrganiserWorkspacePanel organiserWorkspacePanel;
    private final AdminWorkspacePanel adminWorkspacePanel;

    private UserAccount currentUser;

    public AppFrame() {
        Path dataDirectory = AppPaths.dataDirectory();
        new SeedDataInitializer(dataDirectory).ensureSeedData();

        UserRepository userRepository = new UserRepository(dataDirectory.resolve("users.csv"));
        ProfileRepository profileRepository = new ProfileRepository(dataDirectory.resolve("profiles.csv"));
        JobRepository jobRepository = new JobRepository(dataDirectory.resolve("jobs.csv"));
        ApplicationRepository applicationRepository = new ApplicationRepository(dataDirectory.resolve("applications.csv"));
        NotificationRepository notificationRepository = new NotificationRepository(dataDirectory.resolve("notifications.csv"));
        ActivityLogRepository activityLogRepository = new ActivityLogRepository(dataDirectory.resolve("activity_logs.csv"));
        SavedJobRepository savedJobRepository = new SavedJobRepository(dataDirectory.resolve("saved_jobs.csv"));

        ActivityLogService activityLogService = new ActivityLogService(activityLogRepository);
        AiConfiguration aiConfiguration = AiConfigurationLoader.load();
        LlmJsonService llmJsonService = aiConfiguration.isUsable()
                ? new OpenAiCompatibleLlmJsonService(aiConfiguration)
                : new DisabledLlmJsonService();
        notificationService = new NotificationService(notificationRepository, activityLogService);
        authService = new AuthService(userRepository);
        profileService = new ProfileService(profileRepository, activityLogService);
        jobService = new JobService(jobRepository, activityLogService);
        applicationService = new ApplicationService(
                applicationRepository,
                jobRepository,
                profileRepository,
                userRepository,
                notificationService
        );
        cvService = new CvService(dataDirectory.resolve("uploads"));
        workloadService = new WorkloadService(applicationRepository, jobRepository, userRepository);
        analyticsService = new AnalyticsService(
                applicationRepository,
                jobRepository,
                profileRepository,
                userRepository,
                workloadService,
                activityLogService,
                llmJsonService
        );
        savedJobService = new SavedJobService(savedJobRepository, jobRepository);

        setTitle("TA Recruit");
        setSize(1100, 720);
        setMinimumSize(new java.awt.Dimension(780, 560));
        setResizable(true);
        setLocationByPlatform(true);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        cardLayout = new CardLayout();
        rootPanel = new JPanel(cardLayout);
        rootPanel.setBackground(Theme.APP_BACKGROUND);

        loginPanel = new LoginPanel(this::attemptLogin, this::attemptRegistration);
        applicantWorkspacePanel = new ApplicantWorkspacePanel(
                profileService,
                jobService,
                applicationService,
                notificationService,
                analyticsService,
                savedJobService,
                cvService,
                this::openAccountDialog,
                this::logout
        );
        organiserWorkspacePanel = new OrganiserWorkspacePanel(
                applicationService,
                jobService,
                analyticsService,
                cvService,
                this::openAccountDialog,
                this::logout
        );
        adminWorkspacePanel = new AdminWorkspacePanel(
                workloadService,
                analyticsService,
                this::openAccountDialog,
                this::logout
        );

        rootPanel.add(loginPanel, LOGIN_CARD);
        rootPanel.add(applicantWorkspacePanel, APPLICANT_CARD);
        rootPanel.add(organiserWorkspacePanel, ORGANISER_CARD);
        rootPanel.add(adminWorkspacePanel, ADMIN_CARD);
        setContentPane(rootPanel);
        showCard(LOGIN_CARD);
    }

    private void attemptLogin(LoginPanel.LoginRequest request) {
        authService.login(request.email(), request.password())
                .ifPresentOrElse(this::startSession, loginPanel::loginFailed);
    }

    private void attemptRegistration(LoginPanel.RegistrationRequest request) {
        try {
            UserAccount user = authService.registerApplicant(
                    request.displayName(),
                    request.email(),
                    request.password(),
                    request.confirmPassword()
            );
            JOptionPane.showMessageDialog(this, "Account created successfully. Signing you in as " + user.email() + ".");
            startSession(user);
        } catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage());
        }
    }

    private void startSession(UserAccount user) {
        currentUser = user;
        if (user.role() == Role.APPLICANT) {
            applicantWorkspacePanel.setCurrentUser(user);
            applicantWorkspacePanel.refreshAll();
            showCard(APPLICANT_CARD);
        } else if (user.role() == Role.ORGANISER) {
            organiserWorkspacePanel.setCurrentUser(user);
            organiserWorkspacePanel.refreshAll();
            showCard(ORGANISER_CARD);
        } else {
            adminWorkspacePanel.setCurrentUser(user);
            adminWorkspacePanel.refreshAll();
            showCard(ADMIN_CARD);
        }
    }

    private void logout() {
        currentUser = null;
        showCard(LOGIN_CARD);
        JOptionPane.showMessageDialog(this, "You have been signed out.");
    }

    private void openAccountDialog() {
        if (currentUser == null) {
            JOptionPane.showMessageDialog(this, "Please sign in first.");
            return;
        }

        JPasswordField currentPasswordField = UiFactory.passwordField();
        JPasswordField newPasswordField = UiFactory.passwordField();
        JPasswordField confirmPasswordField = UiFactory.passwordField();
        JLabel currentPasswordValidationLabel = UiFactory.validationLabel();
        JLabel newPasswordValidationLabel = UiFactory.validationLabel();
        JLabel confirmPasswordValidationLabel = UiFactory.validationLabel();
        DocumentListener validationListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                updatePasswordDialogValidation(currentPasswordField, newPasswordField, confirmPasswordField,
                        currentPasswordValidationLabel, newPasswordValidationLabel, confirmPasswordValidationLabel, false);
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                updatePasswordDialogValidation(currentPasswordField, newPasswordField, confirmPasswordField,
                        currentPasswordValidationLabel, newPasswordValidationLabel, confirmPasswordValidationLabel, false);
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                updatePasswordDialogValidation(currentPasswordField, newPasswordField, confirmPasswordField,
                        currentPasswordValidationLabel, newPasswordValidationLabel, confirmPasswordValidationLabel, false);
            }
        };
        currentPasswordField.getDocument().addDocumentListener(validationListener);
        newPasswordField.getDocument().addDocumentListener(validationListener);
        confirmPasswordField.getDocument().addDocumentListener(validationListener);
        updatePasswordDialogValidation(currentPasswordField, newPasswordField, confirmPasswordField,
                currentPasswordValidationLabel, newPasswordValidationLabel, confirmPasswordValidationLabel, false);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setOpaque(false);
        form.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        form.add(UiFactory.mutedLabel("Enter your current password, then choose a new password."));
        form.add(Box.createVerticalStrut(14));
        form.add(labelledField("Current password", currentPasswordField, currentPasswordValidationLabel));
        form.add(Box.createVerticalStrut(12));
        form.add(labelledField("New password", UiFactory.passwordFieldWithToggle(newPasswordField, "Show"), newPasswordValidationLabel));
        form.add(Box.createVerticalStrut(8));
        form.add(UiFactory.passwordStrengthMeter(newPasswordField));
        form.add(Box.createVerticalStrut(4));
        form.add(UiFactory.mutedLabel("Password must contain 6 to 20 characters."));
        form.add(Box.createVerticalStrut(12));
        form.add(labelledField("Confirm new password", UiFactory.passwordFieldWithToggle(confirmPasswordField, "Show"), confirmPasswordValidationLabel));

        int result = JOptionPane.showOptionDialog(
                this,
                form,
                "Change Password",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                new Object[]{"Update password", "Cancel"},
                "Update password"
        );
        if (result != JOptionPane.OK_OPTION) {
            return;
        }
        updatePasswordDialogValidation(currentPasswordField, newPasswordField, confirmPasswordField,
                currentPasswordValidationLabel, newPasswordValidationLabel, confirmPasswordValidationLabel, true);

        try {
            UserAccount updated = authService.changePassword(
                    currentUser.id(),
                    new String(currentPasswordField.getPassword()),
                    new String(newPasswordField.getPassword()),
                    new String(confirmPasswordField.getPassword())
            );
            currentUser = updated;
            JOptionPane.showMessageDialog(this, "Password updated successfully.");
        } catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage());
        }
    }

    private JPanel labelledField(String label, JComponent field) {
        return labelledField(label, field, null);
    }

    private JPanel labelledField(String label, JComponent field, JLabel validationLabel) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.add(com.group44.tarecruit.ui.components.UiFactory.bodyLabel(label));
        panel.add(Box.createVerticalStrut(6));
        panel.add(field);
        if (validationLabel != null) {
            panel.add(Box.createVerticalStrut(4));
            panel.add(validationLabel);
        }
        return panel;
    }

    private void updatePasswordDialogValidation(
            JPasswordField currentPasswordField,
            JPasswordField newPasswordField,
            JPasswordField confirmPasswordField,
            JLabel currentPasswordValidationLabel,
            JLabel newPasswordValidationLabel,
            JLabel confirmPasswordValidationLabel,
            boolean showRequiredErrors
    ) {
        String currentPassword = new String(currentPasswordField.getPassword());
        String newPassword = new String(newPasswordField.getPassword());
        String confirmPassword = new String(confirmPasswordField.getPassword());
        UiFactory.setValidationMessage(
                currentPasswordValidationLabel,
                currentPassword.isBlank() ? (showRequiredErrors ? "Current password is required." : "") : "",
                currentPassword.isBlank() && showRequiredErrors
        );
        boolean invalidNewPassword = newPassword.length() < 6 || newPassword.length() > 20;
        UiFactory.setValidationMessage(
                newPasswordValidationLabel,
                newPassword.isBlank() ? (showRequiredErrors ? "New password is required." : "6 to 20 characters") : (invalidNewPassword ? "New password must be 6 to 20 characters." : ""),
                invalidNewPassword && (!newPassword.isBlank() || showRequiredErrors)
        );
        boolean mismatch = !confirmPassword.isBlank() && !newPassword.equals(confirmPassword);
        UiFactory.setValidationMessage(
                confirmPasswordValidationLabel,
                confirmPassword.isBlank() ? (showRequiredErrors ? "Confirm your new password." : "") : (mismatch ? "New passwords must match." : ""),
                mismatch || (confirmPassword.isBlank() && showRequiredErrors)
        );
    }

    private void showCard(String card) {
        cardLayout.show(rootPanel, card);
    }
}
