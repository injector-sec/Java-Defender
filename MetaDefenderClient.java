import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Base64;
import org.json.JSONObject;

public class MetaDefenderClient extends JFrame {
    private JComboBox<String> operationCombo;
    private JTextField contentField;
    private JButton fileSelectBtn, submitBtn, saveBtn;
    private JTextArea resultArea;
    private JLabel fileLabel;
    private File selectedFile;

    public MetaDefenderClient() {
        setTitle("MetaDefender Client");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        createUI();
    }

    private void createUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Operation selection
        JPanel topPanel = new JPanel(new GridLayout(3, 2, 10, 10));
        topPanel.add(new JLabel("Operation:"));
        
        operationCombo = new JComboBox<>(new String[]{"trim", "sort", "trim_file", "sort_file", "scan_file", "scan_url"});
        operationCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, 
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                String[] descriptions = {
                    "Trim spaces (text)", "Sort values (text)", 
                    "Trim spaces (file)", "Sort lines (file)",
                    "Scan file", "Scan URL"
                };
                if (index >= 0 && index < descriptions.length) {
                    setText(descriptions[index]);
                }
                return this;
            }
        });
        topPanel.add(operationCombo);

        // Content input
        topPanel.add(new JLabel("Content/URL:"));
        contentField = new JTextField();
        topPanel.add(contentField);

        // File selection
        topPanel.add(new JLabel("File:"));
        JPanel filePanel = new JPanel(new BorderLayout());
        fileLabel = new JLabel("No file selected");
        fileSelectBtn = new JButton("Select File");
        fileSelectBtn.addActionListener(e -> selectFile());
        filePanel.add(fileLabel, BorderLayout.CENTER);
        filePanel.add(fileSelectBtn, BorderLayout.EAST);
        topPanel.add(filePanel);

        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Result area
        resultArea = new JTextArea();
        resultArea.setEditable(false);
        resultArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(resultArea);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        saveBtn = new JButton("Save Result");
        saveBtn.addActionListener(e -> saveResult());
        saveBtn.setEnabled(false);
        
        submitBtn = new JButton("Submit");
        submitBtn.addActionListener(e -> submitRequest());
        
        buttonPanel.add(saveBtn);
        buttonPanel.add(submitBtn);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Operation combo listener
        operationCombo.addActionListener(e -> {
            String selected = (String) operationCombo.getSelectedItem();
            contentField.setEnabled(!selected.endsWith("_file"));
            fileSelectBtn.setEnabled(selected.endsWith("_file"));
        });

        add(mainPanel);
    }

    private void selectFile() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedFile = fileChooser.getSelectedFile();
            fileLabel.setText(selectedFile.getName());
        }
    }

    private void submitRequest() {
        String operation = (String) operationCombo.getSelectedItem();
        
        if (operation.endsWith("_file")) {
            submitFileOperation(operation);
        } else {
            submitTextOperation(operation);
        }
    }

    private void submitFileOperation(String operation) {
        if (selectedFile == null) {
            JOptionPane.showMessageDialog(this, "Please select a file first", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        new Thread(() -> {
            try {
                setLoading(true);
                resultArea.setText("Reading file...");
                
                byte[] fileContent = Files.readAllBytes(selectedFile.toPath());
                String encodedContent = Base64.getEncoder().encodeToString(fileContent);
                
                JSONObject request = new JSONObject();
                request.put("operation", operation);
                request.put("content", encodedContent);
                if (operation.equals("scan_file")) {
                    request.put("filename", selectedFile.getName());
                }
                
                resultArea.setText("Sending to server...");
                String response = sendRequestToServer(request.toString());
                
                displayResponse(response);
                saveBtn.setEnabled(true);
            } catch (Exception ex) {
                resultArea.setText("Error: " + ex.getMessage());
                ex.printStackTrace();
            } finally {
                setLoading(false);
            }
        }).start();
    }

    private void submitTextOperation(String operation) {
        String content = contentField.getText().trim();
        if (content.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter content", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        new Thread(() -> {
            try {
                setLoading(true);
                JSONObject request = new JSONObject();
                request.put("operation", operation);
                request.put("content", content);
                
                String response = sendRequestToServer(request.toString());
                displayResponse(response);
                saveBtn.setEnabled(true);
            } catch (Exception ex) {
                resultArea.setText("Error: " + ex.getMessage());
                ex.printStackTrace();
            } finally {
                setLoading(false);
            }
        }).start();
    }

    private void displayResponse(String response) {
        try {
            JSONObject jsonResponse = new JSONObject(response);
            
            if (jsonResponse.has("scan_results") || jsonResponse.has("file_info")) {
                resultArea.setText(formatScanResults(jsonResponse));
            } 
            else if (jsonResponse.has("result")) {
                resultArea.setText(jsonResponse.getString("result"));
            }
            else {
                resultArea.setText("Full Response:\n" + jsonResponse.toString(2));
            }
        } catch (Exception e) {
            resultArea.setText("Error displaying results:\n" + response);
        }
    }

    private String formatScanResults(JSONObject scanResults) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Scan Results ===\n\n");
        
        // File/URL Info
        if (scanResults.has("file_info")) {
            JSONObject fileInfo = scanResults.getJSONObject("file_info");
            sb.append("=== File Information ===\n");
            appendFormattedInfo(sb, "File Name", fileInfo.optString("display_name"));
            appendFormattedInfo(sb, "File Size", fileInfo.optString("size") + " bytes");
            appendFormattedInfo(sb, "File Type", fileInfo.optString("file_type"));
            sb.append("\n");
        }
        
        // Scan Results
        if (scanResults.has("scan_results")) {
            JSONObject scanData = scanResults.getJSONObject("scan_results");
            sb.append("=== Scan Summary ===\n");
            appendFormattedInfo(sb, "Overall Result", scanData.optString("scan_all_result_a"));
            appendFormattedInfo(sb, "Detection Ratio", 
                scanData.optInt("total_detected_avs", 0) + "/" + 
                scanData.optInt("total_avs", 0));
            sb.append("\n");

            // Engine Results
            if (scanData.has("scan_details")) {
                sb.append("=== Engine Results ===\n");
                JSONObject details = scanData.getJSONObject("scan_details");
                
                List<String> engines = new ArrayList<String>(details.keySet());
                Collections.sort(engines);
                
                sb.append(String.format("%-20s %-15s %-30s\n", 
                    "Engine", "Result", "Threat Found"));
                sb.append(String.format("%-20s %-15s %-30s\n",
                    "------", "------", "------------"));
                
                for (String engine : engines) {
                    JSONObject engineResult = details.getJSONObject(engine);
                    sb.append(String.format("%-20s %-15s %-30s\n",
                        engine,
                        engineResult.optInt("scan_result_i", 0) == 0 ? "Clean" : "Malicious",
                        engineResult.optString("threat_found", "None")));
                }
            }
        }
        
        return sb.toString();
    }

    private void appendFormattedInfo(StringBuilder sb, String label, String value) {
        sb.append(String.format("%-15s: %s\n", label, 
            value == null || value.isEmpty() ? "N/A" : value));
    }

    private String sendRequestToServer(String requestBody) throws IOException {
        URL url = new URL("http://localhost:8080/api/process");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = requestBody.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "utf-8"))) {
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            return response.toString();
        }
    }

    private void saveResult() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Result");
        fileChooser.setSelectedFile(new File("result.txt"));
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (FileWriter writer = new FileWriter(fileChooser.getSelectedFile())) {
                writer.write(resultArea.getText());
                JOptionPane.showMessageDialog(this, "Result saved!", "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Error saving file", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void setLoading(boolean isLoading) {
        submitBtn.setEnabled(!isLoading);
        operationCombo.setEnabled(!isLoading);
        contentField.setEnabled(!isLoading && !((String)operationCombo.getSelectedItem()).endsWith("_file"));
        fileSelectBtn.setEnabled(!isLoading && ((String)operationCombo.getSelectedItem()).endsWith("_file"));
        setCursor(isLoading ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                new MetaDefenderClient().setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}