package ui;

import javax.swing.*;
import static ui.Constants.*;

public class HomePage extends JFrame {
    public HomePage() {
        setTitle("");
        setSize(400, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Main Container
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(Color.WHITE);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Color.decode("#3C3E45"));
        header.setPreferredSize(new Dimension(400, 70));

        JLabel title = new JLabel("My App");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Arial", Font.BOLD, 24));
        title.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));

        header.add(title, BorderLayout.WEST);

        // Main content
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(Color.WHITE);
        content.setBorder(BorderFactory.createEmptyBorder(30, 20, 30, 20));

        JLabel welcome = new JLabel("Welcome!");
        welcome.setFont(new Font("Arial", Font.BOLD, 28));
        welcome.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton button = new JButton("Get Started");
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setMaximumSize(new Dimension(250, 50));

        content.add(welcome);
        content.add(Box.createVerticalStrut(30));
        content.add(button);

        // Bottom navigation
        JPanel navigation = new JPanel(new GridLayout(1, 3));
        navigation.setPreferredSize(new Dimension(400, 60));

        JButton homeButton = new JButton("Home");
        JButton searchButton = new JButton("Search");
        JButton profileButton = new JButton("Profile");

        navigation.add(homeButton);
        navigation.add(searchButton);
        navigation.add(profileButton);

        // Put everything together
        mainPanel.add(header, BorderLayout.NORTH);
        mainPanel.add(content, BorderLayout.CENTER);
        mainPanel.add(navigation, BorderLayout.SOUTH);

        add(mainPanel);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            PhoneApp app = new PhoneApp();
            app.setVisible(true);
        });
    }
}