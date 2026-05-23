package com.group44.tarecruit.ui;

import com.group44.tarecruit.ui.components.Theme;
import com.group44.tarecruit.ui.components.UiFactory;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.BorderFactory;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.function.Consumer;

public class LoginPanel extends JPanel {
    private final Consumer<LoginRequest> loginHandler;
    private final Consumer<RegistrationRequest> registrationHandler;
    private final JComponent emailField;
    private final JPasswordField passwordField;

    public LoginPanel(Consumer<LoginRequest> loginHandler, Consumer<RegistrationRequest> registrationHandler) {
        this.loginHandler = loginHandler;
        this.registrationHandler = registrationHandler;
        this.emailField = UiFactory.textField();
        this.passwordField = new JPasswordField();
        passwordField.setFont(Theme.BODY_FONT);
        passwordField.setBorder(((JComponent) emailField).getBorder());

        setLayout(new BorderLayout());
        setBackground(Theme.APP_BACKGROUND);
        add(buildHeroPanel(), BorderLayout.WEST);
        add(buildFormPanel(), BorderLayout.CENTER);
    }

    private JPanel buildHeroPanel() {
        JPanel hero = new JPanel();
        hero.setBackground(Theme.PRIMARY_DARK);
        hero.setPreferredSize(new Dimension(286, 620));
        hero.setLayout(new BoxLayout(hero, BoxLayout.Y_AXIS));
        hero.setBorder(javax.swing.BorderFactory.createEmptyBorder(42, 32, 42, 32));

        JLabel brand = UiFactory.titleLabel("TA Recruit");
        brand.setForeground(Theme.SURFACE);
        JLabel subtitle = UiFactory.sectionLabel("Career workspace");
        subtitle.setForeground(new java.awt.Color(220, 232, 255));
        JLabel detail = UiFactory.bodyLabel("<html>Apply, review, generate resumes and manage your account in one polished desktop workspace.</html>");
        detail.setForeground(new java.awt.Color(220, 232, 255));

        hero.add(brand);
        hero.add(Box.createVerticalStrut(16));
        hero.add(subtitle);
        hero.add(Box.createVerticalStrut(20));
        hero.add(detail);
        hero.add(Box.createVerticalStrut(30));
        hero.add(accountHint("Applicant demo", "newta@school.edu / password123"));
        hero.add(Box.createVerticalStrut(8));
        hero.add(accountHint("Existing applicants", "amy@school.edu / password123"));
        hero.add(Box.createVerticalStrut(8));
        hero.add(accountHint("Module organiser", "mo@school.edu / password123"));
        hero.add(Box.createVerticalStrut(8));
        hero.add(accountHint("Admin", "admin@school.edu / password123"));
        hero.add(Box.createVerticalGlue());
        return hero;
    }

    private JPanel accountHint(String title, String description) {
        JPanel panel = UiFactory.card();
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 92));
        panel.setBackground(new java.awt.Color(245, 248, 255));
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        JLabel titleLabel = UiFactory.bodyLabel(title);
        titleLabel.setFont(Theme.BUTTON_FONT);
        JLabel descriptionLabel = UiFactory.mutedLabel(description);
        content.add(titleLabel);
        content.add(Box.createVerticalStrut(6));
        content.add(descriptionLabel);
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildFormPanel() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBorder(javax.swing.BorderFactory.createEmptyBorder(28, 32, 28, 40));

        JPanel card = UiFactory.card();
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(UiFactory.titleLabel("Sign in"));
        content.add(Box.createVerticalStrut(10));
        content.add(UiFactory.mutedLabel("Use a demo account, or create a new applicant account in a few seconds."));
        content.add(Box.createVerticalStrut(24));
        content.add(labeledField("Email", emailField));
        content.add(Box.createVerticalStrut(16));
        content.add(labeledField("Password", passwordField));
        content.add(Box.createVerticalStrut(24));

        JPanel buttonRow = new JPanel(new GridLayout(1, 2, 12, 0));
        buttonRow.setOpaque(false);
        JButton loginButton = UiFactory.primaryButton("Login");
        loginButton.addActionListener(event -> submitLogin());
        JButton clearButton = UiFactory.lightButton("Clear");
        clearButton.addActionListener(event -> {
            ((javax.swing.JTextField) emailField).setText("");
            passwordField.setText("");
        });
        buttonRow.add(loginButton);
        buttonRow.add(clearButton);
        content.add(buttonRow);
        content.add(Box.createVerticalStrut(24));

        JButton registerButton = UiFactory.secondaryButton("Create Applicant Account");
        registerButton.addActionListener(event -> showRegisterDialog());
        content.add(registerButton);
        content.add(Box.createVerticalStrut(16));

        JPanel quickLoginRow = new JPanel(new GridLayout(1, 3, 12, 0));
        quickLoginRow.setOpaque(false);
        JButton applicantDemo = UiFactory.secondaryButton("Use Applicant Demo");
        applicantDemo.addActionListener(event -> fillCredentials("newta@school.edu", "password123"));
        JButton organiserDemo = UiFactory.secondaryButton("Use MO Demo");
        organiserDemo.addActionListener(event -> fillCredentials("mo@school.edu", "password123"));
        JButton adminDemo = UiFactory.secondaryButton("Use Admin Demo");
        adminDemo.addActionListener(event -> fillCredentials("admin@school.edu", "password123"));
        quickLoginRow.add(applicantDemo);
        quickLoginRow.add(organiserDemo);
        quickLoginRow.add(adminDemo);
        content.add(quickLoginRow);

        card.add(content, BorderLayout.CENTER);
        wrapper.add(card, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel labeledField(String labelText, JComponent field) {
        return labeledField(labelText, field, null);
    }

    private JPanel labeledField(String labelText, JComponent field, JLabel validationLabel) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(LEFT_ALIGNMENT);
        JLabel label = UiFactory.bodyLabel(labelText);
        label.setFont(Theme.BUTTON_FONT);
        label.setAlignmentX(LEFT_ALIGNMENT);
        field.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(label);
        panel.add(Box.createVerticalStrut(8));
        UiFactory.fixedHeight(field, 36);
        panel.add(field);
        if (validationLabel != null) {
            panel.add(Box.createVerticalStrut(4));
            panel.add(validationLabel);
        }
        return panel;
    }

    private void fillCredentials(String email, String password) {
        ((javax.swing.JTextField) emailField).setText(email);
        passwordField.setText(password);
    }

    private void submitLogin() {
        String email = ((javax.swing.JTextField) emailField).getText().trim();
        String password = new String(passwordField.getPassword());
        if (email.isBlank() || password.isBlank()) {
            JOptionPane.showMessageDialog(this, "Please enter both email and password.");
            return;
        }
        loginHandler.accept(new LoginRequest(email, password));
    }

    public void loginFailed() {
        JOptionPane.showMessageDialog(this, "Invalid email or password. Please try one of the seeded accounts.");
    }

    private void showRegisterDialog() {
        JTextField nameField = UiFactory.textField();
        JTextField emailField = UiFactory.textField();
        JPasswordField passwordField = UiFactory.passwordField();
        JPasswordField confirmField = UiFactory.passwordField();
        JLabel nameValidationLabel = UiFactory.validationLabel();
        JLabel emailValidationLabel = UiFactory.validationLabel();
        JLabel passwordValidationLabel = UiFactory.validationLabel();
        JLabel confirmValidationLabel = UiFactory.validationLabel();
        DocumentListener validationListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                updateRegistrationValidation(nameField, emailField, passwordField, confirmField,
                        nameValidationLabel, emailValidationLabel, passwordValidationLabel, confirmValidationLabel, false);
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                updateRegistrationValidation(nameField, emailField, passwordField, confirmField,
                        nameValidationLabel, emailValidationLabel, passwordValidationLabel, confirmValidationLabel, false);
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                updateRegistrationValidation(nameField, emailField, passwordField, confirmField,
                        nameValidationLabel, emailValidationLabel, passwordValidationLabel, confirmValidationLabel, false);
            }
        };
        nameField.getDocument().addDocumentListener(validationListener);
        emailField.getDocument().addDocumentListener(validationListener);
        passwordField.getDocument().addDocumentListener(validationListener);
        confirmField.getDocument().addDocumentListener(validationListener);
        updateRegistrationValidation(nameField, emailField, passwordField, confirmField,
                nameValidationLabel, emailValidationLabel, passwordValidationLabel, confirmValidationLabel, false);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setOpaque(false);
        form.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        form.add(UiFactory.mutedLabel("Create an applicant account with your email and a secure password."));
        form.add(Box.createVerticalStrut(12));
        form.add(labeledField("Display name", nameField, nameValidationLabel));
        form.add(Box.createVerticalStrut(10));
        form.add(labeledField("Email", emailField, emailValidationLabel));
        form.add(Box.createVerticalStrut(10));
        form.add(labeledField("Password", UiFactory.passwordFieldWithToggle(passwordField, "Show"), passwordValidationLabel));
        form.add(Box.createVerticalStrut(8));
        form.add(UiFactory.passwordStrengthMeter(passwordField));
        form.add(Box.createVerticalStrut(4));
        form.add(UiFactory.mutedLabel("Password must contain 6 to 20 characters."));
        form.add(Box.createVerticalStrut(10));
        form.add(labeledField("Confirm password", UiFactory.passwordFieldWithToggle(confirmField, "Show"), confirmValidationLabel));

        int result = JOptionPane.showOptionDialog(
                this,
                form,
                "Create Applicant Account",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                new Object[]{"Create account", "Cancel"},
                "Create account"
        );
        if (result != JOptionPane.OK_OPTION) {
            return;
        }
        updateRegistrationValidation(nameField, emailField, passwordField, confirmField,
                nameValidationLabel, emailValidationLabel, passwordValidationLabel, confirmValidationLabel, true);

        registrationHandler.accept(new RegistrationRequest(
                nameField.getText(),
                emailField.getText(),
                new String(passwordField.getPassword()),
                new String(confirmField.getPassword())
        ));
    }

    private void updateRegistrationValidation(
            JTextField nameField,
            JTextField emailField,
            JPasswordField passwordField,
            JPasswordField confirmField,
            JLabel nameValidationLabel,
            JLabel emailValidationLabel,
            JLabel passwordValidationLabel,
            JLabel confirmValidationLabel,
            boolean showRequiredErrors
    ) {
        String name = nameField.getText().trim();
        String email = emailField.getText().trim();
        String password = new String(passwordField.getPassword());
        String confirmPassword = new String(confirmField.getPassword());
        UiFactory.setValidationMessage(nameValidationLabel, name.isBlank() ? (showRequiredErrors ? "Display name is required." : "") : "", name.isBlank() && showRequiredErrors);
        boolean invalidEmail = !email.isBlank() && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
        UiFactory.setValidationMessage(emailValidationLabel, email.isBlank() ? (showRequiredErrors ? "Email is required." : "Example: name@school.edu") : (invalidEmail ? "Enter a valid email address." : ""), invalidEmail || (email.isBlank() && showRequiredErrors));
        boolean invalidPassword = password.length() < 6 || password.length() > 20;
        UiFactory.setValidationMessage(passwordValidationLabel, password.isBlank() ? (showRequiredErrors ? "Password is required." : "6 to 20 characters") : (invalidPassword ? "Password must be 6 to 20 characters." : ""), invalidPassword && (!password.isBlank() || showRequiredErrors));
        boolean mismatch = !confirmPassword.isBlank() && !password.equals(confirmPassword);
        UiFactory.setValidationMessage(confirmValidationLabel, confirmPassword.isBlank() ? (showRequiredErrors ? "Confirm your password." : "") : (mismatch ? "Passwords must match." : ""), mismatch || (confirmPassword.isBlank() && showRequiredErrors));
    }

    public record LoginRequest(String email, String password) {
    }

    public record RegistrationRequest(String displayName, String email, String password, String confirmPassword) {
    }
}
