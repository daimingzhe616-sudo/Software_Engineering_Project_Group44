package com.group44.tarecruit.ui.components;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;

public final class UiFactory {
    private UiFactory() {
    }

    public static JLabel titleLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.TITLE_FONT);
        label.setForeground(Theme.TEXT);
        return label;
    }

    public static JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.SECTION_FONT);
        label.setForeground(Theme.TEXT);
        return label;
    }

    public static JLabel bodyLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.BODY_FONT);
        label.setForeground(Theme.TEXT);
        return label;
    }

    public static JLabel mutedLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.SMALL_FONT);
        label.setForeground(Theme.SUBTLE_TEXT);
        return label;
    }

    public static JLabel validationLabel() {
        JLabel label = mutedLabel("");
        label.setPreferredSize(new Dimension(220, 18));
        return label;
    }

    public static void setValidationMessage(JLabel label, String message, boolean error) {
        label.setText(message == null ? "" : message);
        label.setForeground(error ? new Color(174, 45, 45) : Theme.SUBTLE_TEXT);
    }

    public static JButton primaryButton(String text) {
        return button(text, Theme.PRIMARY, Color.WHITE);
    }

    public static JButton secondaryButton(String text) {
        return button(text, Theme.ACCENT, Color.WHITE);
    }

    public static JButton lightButton(String text) {
        return button(text, Theme.SURFACE_MUTED, Theme.TEXT);
    }

    public static JButton navButton(String text) {
        JButton button = button(text, Theme.SURFACE_MUTED, Theme.TEXT);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setPreferredSize(new Dimension(148, 34));
        return button;
    }

    private static JButton button(String text, Color background, Color foreground) {
        JButton button = new JButton(text);
        button.setFont(Theme.BUTTON_FONT);
        button.setFocusPainted(false);
        button.setBackground(background);
        button.setForeground(foreground);
        button.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setOpaque(true);
        return button;
    }

    public static JTextField textField() {
        JTextField field = new JTextField();
        field.setFont(Theme.BODY_FONT);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1, true),
                BorderFactory.createEmptyBorder(7, 9, 7, 9)
        ));
        return field;
    }

    public static JPasswordField passwordField() {
        JPasswordField field = new JPasswordField();
        field.setFont(Theme.BODY_FONT);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1, true),
                BorderFactory.createEmptyBorder(7, 9, 7, 9)
        ));
        return field;
    }

    public static JPanel passwordFieldWithToggle(JPasswordField field, String toggleText) {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setOpaque(false);
        JButton toggleButton = lightButton(toggleText);
        toggleButton.setPreferredSize(new Dimension(64, 34));
        char echoChar = field.getEchoChar();
        toggleButton.addActionListener(event -> {
            boolean showing = field.getEchoChar() == 0;
            field.setEchoChar(showing ? echoChar : (char) 0);
            toggleButton.setText(showing ? toggleText : "Hide");
        });
        panel.add(field, BorderLayout.CENTER);
        panel.add(toggleButton, BorderLayout.EAST);
        return panel;
    }

    public static JLabel passwordStrengthLabel(JPasswordField field) {
        JLabel label = mutedLabel("Password strength: empty");
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                update();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                update();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                update();
            }

            private void update() {
                String password = new String(field.getPassword());
                label.setText("Password strength: " + passwordStrength(password).label());
            }
        });
        return label;
    }

    public static JPanel passwordStrengthMeter(JPasswordField field) {
        JPanel meter = new JPanel(new BorderLayout(10, 0));
        meter.setOpaque(false);
        JPanel bars = new JPanel(new GridLayout(1, 6, 3, 0));
        bars.setOpaque(false);
        JLabel[] segments = new JLabel[6];
        for (int index = 0; index < segments.length; index++) {
            JLabel segment = new JLabel();
            segment.setOpaque(true);
            segment.setBackground(Theme.BORDER);
            segment.setPreferredSize(new Dimension(28, 10));
            segments[index] = segment;
            bars.add(segment);
        }
        JLabel label = mutedLabel("Password strength: empty");
        meter.add(bars, BorderLayout.WEST);
        meter.add(label, BorderLayout.CENTER);
        meter.add(Box.createHorizontalGlue(), BorderLayout.EAST);

        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                update();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                update();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                update();
            }

            private void update() {
                PasswordStrength strength = passwordStrength(new String(field.getPassword()));
                label.setText("Password strength: " + strength.label());
                for (int index = 0; index < segments.length; index++) {
                    segments[index].setBackground(index < strength.activeSegments() ? strength.color() : Theme.BORDER);
                }
            }
        });
        return meter;
    }

    private static PasswordStrength passwordStrength(String password) {
        if (password == null || password.isBlank()) {
            return new PasswordStrength("empty", 0, Theme.BORDER);
        }
        int score = 0;
        if (password.length() >= 6) {
            score++;
        }
        if (password.length() >= 10) {
            score++;
        }
        if (password.matches(".*[A-Z].*") && password.matches(".*[a-z].*")) {
            score++;
        }
        if (password.matches(".*\\d.*")) {
            score++;
        }
        if (password.matches(".*[^A-Za-z0-9].*")) {
            score++;
        }
        if (password.length() < 6) {
            return new PasswordStrength("too short", 1, new Color(224, 61, 61));
        }
        if (score <= 2) {
            return new PasswordStrength("weak", 2, new Color(224, 61, 61));
        }
        if (score <= 4) {
            return new PasswordStrength("medium", 4, Theme.WARNING);
        }
        return new PasswordStrength("strong", 6, Theme.SUCCESS);
    }

    private record PasswordStrength(String label, int activeSegments, Color color) {
    }

    public static JTextField numericTextField(int maxLength) {
        JTextField field = textField();
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
                replace(fb, offset, 0, string, attr);
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                if (text == null) {
                    return;
                }
                String candidate = new StringBuilder(fb.getDocument().getText(0, fb.getDocument().getLength()))
                        .replace(offset, offset + length, text)
                        .toString();
                if (candidate.length() <= maxLength && candidate.matches("\\d*")) {
                    super.replace(fb, offset, length, text, attrs);
                }
            }
        });
        return field;
    }

    public static JTextArea textArea(int rows) {
        JTextArea area = new JTextArea(rows, 20);
        area.setFont(Theme.BODY_FONT);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(BorderFactory.createEmptyBorder(7, 9, 7, 9));
        return area;
    }

    public static JPanel card() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.SURFACE);
        panel.setBorder(Theme.cardBorder());
        return panel;
    }

    public static JPanel flowPanel(int align, int hgap, int vgap) {
        JPanel panel = new JPanel(new FlowLayout(align, hgap, vgap));
        panel.setOpaque(false);
        return panel;
    }

    public static JScrollPane scrollPane(Component component) {
        Component view = component instanceof Scrollable ? component : new ViewportWidthPanel(component);
        JScrollPane scrollPane = new JScrollPane(view);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getViewport().setBackground(Theme.APP_BACKGROUND);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        return scrollPane;
    }

    public static void fixedHeight(JComponent component, int height) {
        Dimension preferred = component.getPreferredSize();
        component.setPreferredSize(new Dimension(preferred.width, height));
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
    }

    private static final class ViewportWidthPanel extends JPanel implements Scrollable {
        private ViewportWidthPanel(Component component) {
            super(new BorderLayout());
            setOpaque(false);
            add(component, BorderLayout.CENTER);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(64, visibleRect.height - 32);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
