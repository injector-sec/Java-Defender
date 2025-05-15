import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import org.json.JSONObject;

public class MetaDefenderServer {
    private static final String API_KEY = "xxxxxxxxxxxxxxxx"; // Replace with your actual API key
    private static final String API_URL = "https://api.metadefender.com/v4/";
    private static final ConcurrentLinkedQueue<String> operationLogs = new ConcurrentLinkedQueue<>();
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/api/process", new ProcessHandler());
        server.createContext("/api/logs", new LogsHandler());
        server.setExecutor(null);
        server.start();
        
        startLogConsole();
        System.out.println("Server started on port 8080");
    }

    static class ProcessHandler implements com.sun.net.httpserver.HttpHandler {
        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            try {
                String clientAddress = exchange.getRemoteAddress().getAddress().getHostAddress();
                logOperation("Request from " + clientAddress);
                
                if (!"POST".equals(exchange.getRequestMethod())) {
                    sendResponse(exchange, 405, "Method Not Allowed");
                    return;
                }

                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                JSONObject request = new JSONObject(requestBody);
                String operation = request.getString("operation");
                JSONObject response = new JSONObject();

                logOperation("Operation: " + operation + " requested");
                
                switch (operation) {
                    case "trim":
                        response.put("result", request.getString("content").trim());
                        break;
                    case "sort":
                        String[] items = request.getString("content").split(",");
                        Arrays.sort(items);
                        response.put("result", String.join(",", items));
                        break;
                    case "trim_file":
                    case "sort_file":
                        response = processFileOperation(
                            Base64.getDecoder().decode(request.getString("content")), 
                            operation
                        );
                        break;
                    case "scan_file":
                        response = scanFile(
                            Base64.getDecoder().decode(request.getString("content")),
                            request.getString("filename")
                        );
                        break;
                    case "scan_url":
                        response = scanUrl(request.getString("content"));
                        break;
                    default:
                        sendResponse(exchange, 400, "Invalid operation");
                        return;
                }

                sendResponse(exchange, 200, response.toString());
            } catch (Exception e) {
                logOperation("Error: " + e.getMessage());
                sendResponse(exchange, 500, "Error: " + e.getMessage());
            }
        }

        private JSONObject processFileOperation(byte[] fileBytes, String operation) throws Exception {
            String content = new String(fileBytes, StandardCharsets.UTF_8);
            JSONObject response = new JSONObject();
            
            if (operation.equals("trim_file")) {
                response.put("result", content.trim());
            } else {
                String[] lines = content.split("\n");
                Arrays.sort(lines);
                response.put("result", String.join("\n", lines));
            }
            return response;
        }

        private JSONObject scanFile(byte[] fileBytes, String fileName) throws Exception {
            File tempFile = File.createTempFile("scan_", fileName);
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(fileBytes);
            }

            String scanResponse = submitFileForScan(tempFile);
            JSONObject json = new JSONObject(scanResponse);
            
            if (json.has("data_id")) {
                String dataId = json.getString("data_id");
                String results = pollScanResults(dataId, "file");
                return new JSONObject(results);
            }
            return json;
        }

        private JSONObject scanUrl(String urlToScan) throws Exception {
            String scanResponse = submitUrlForScan(urlToScan);
            JSONObject json = new JSONObject(scanResponse);
            
            if (json.has("data_id")) {
                String dataId = json.getString("data_id");
                String results = pollScanResults(dataId, "url");
                return new JSONObject(results);
            }
            return json;
        }

        private String submitFileForScan(File file) throws IOException {
            URL url = new URL(API_URL + "file");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("apikey", API_KEY);
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            conn.setRequestProperty("filename", file.getName());
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream(); 
                 FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }

            return getResponse(conn);
        }

        private String submitUrlForScan(String urlToScan) throws IOException {
            URL url = new URL(API_URL + "url");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("apikey", API_KEY);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            String jsonInputString = "{\"url\": [\"" + urlToScan + "\"]}";

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            return getResponse(conn);
        }

        private String pollScanResults(String dataId, String type) throws IOException, InterruptedException {
            String endpoint = type.equals("file") ? "file" : "url";
            URL url = new URL(API_URL + endpoint + "/" + dataId);

            while (true) {
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("apikey", API_KEY);

                String response = getResponse(conn);
                JSONObject json = new JSONObject(response);

                int progress = json.has("scan_results") ? 
                    json.getJSONObject("scan_results").getInt("progress_percentage") :
                    json.getInt("progress_percentage");
                
                if (progress == 100) {
                    return response;
                }
                Thread.sleep(2000);
            }
        }

        private String getResponse(HttpURLConnection conn) throws IOException {
            int responseCode = conn.getResponseCode();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(
                        responseCode == 200 ? conn.getInputStream() : conn.getErrorStream(), 
                        "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                if (responseCode != 200) {
                    throw new IOException("HTTP " + responseCode + " - " + response.toString());
                }
                return response.toString();
            }
        }
    }

    static class LogsHandler implements com.sun.net.httpserver.HttpHandler {
        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            try {
                if (!"GET".equals(exchange.getRequestMethod())) {
                    sendResponse(exchange, 405, "Method Not Allowed");
                    return;
                }

                StringBuilder logs = new StringBuilder();
                for (String log : operationLogs) {
                    logs.append(log).append("\n");
                }

                JSONObject response = new JSONObject();
                response.put("logs", logs.toString());
                sendResponse(exchange, 200, response.toString());
            } catch (Exception e) {
                sendResponse(exchange, 500, "Error: " + e.getMessage());
            }
        }
    }

    private static void logOperation(String message) {
        String timestamp = dateFormat.format(new Date());
        String logEntry = "[" + timestamp + "] " + message;
        operationLogs.add(logEntry);
        System.out.println(logEntry);
    }

    private static void sendResponse(com.sun.net.httpserver.HttpExchange exchange, 
                                   int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

    private static void startLogConsole() {
        JFrame logFrame = new JFrame("Server Logs");
        logFrame.setSize(600, 400);
        logFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        

        JTextArea logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        
        JButton saveBtn = new JButton("Save Logs");
        saveBtn.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            if (fileChooser.showSaveDialog(logFrame) == JFileChooser.APPROVE_OPTION) {
                try (FileWriter writer = new FileWriter(fileChooser.getSelectedFile())) {
                    for (String log : operationLogs) {
                        writer.write(log + "\n");
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });
        
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(saveBtn, BorderLayout.SOUTH);
        
        logFrame.add(panel);
        logFrame.setTitle("Server");
        logFrame.setVisible(true);
        
        javax.swing.Timer timer = new javax.swing.Timer(1000, e -> {
            StringBuilder logs = new StringBuilder();
            for (String log : operationLogs) {
                logs.append(log).append("\n");
            }
            logArea.setText(logs.toString());
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
        timer.start();
    }
}