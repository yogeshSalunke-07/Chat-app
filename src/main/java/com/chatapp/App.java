import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.stage.FileChooser;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import java.io.*;
import java.net.*;

/**
 * Opens a window for two-way network chat.
 */
public class App extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    private enum ConnectionState {
        LISTENING, CONNECTING, CONNECTED, CLOSED
    }

    private static final String DEFAULT_PORT = "1501";
    private static final String DEFAULT_HOST = "localhost";

    private volatile ConnectionHandler connection;

    private Button listenButton, connectButton, closeButton, clearButton, quitButton, saveButton, sendButton;
    private TextField listeningPortInput, remotePortInput, remoteHostInput, messageInput;
    private TextArea transcript;
    private Stage window;

    @Override
    public void start(Stage stage) {
        window = stage;

        // Buttons and Inputs
        listenButton = new Button("Listen on port:");
        connectButton = new Button("Connect to:");
        closeButton = new Button("Disconnect");
        clearButton = new Button("Clear Transcript");
        quitButton = new Button("Quit");
        saveButton = new Button("Save Transcript");
        sendButton = new Button("Send");

        listeningPortInput = new TextField(DEFAULT_PORT);
        remotePortInput = new TextField(DEFAULT_PORT);
        remoteHostInput = new TextField(DEFAULT_HOST);

        messageInput = new TextField();
        transcript = new TextArea();

        setupUI(stage);
    }

    private void setupUI(Stage stage) {
        listenButton.setOnAction(this::doAction);
        connectButton.setOnAction(this::doAction);
        closeButton.setOnAction(this::doAction);
        clearButton.setOnAction(this::doAction);
        saveButton.setOnAction(this::doAction);
        quitButton.setOnAction(this::doAction);
        sendButton.setOnAction(this::doAction);
        messageInput.setOnAction(this::doAction);

        closeButton.setDisable(true);
        sendButton.setDisable(true);
        messageInput.setEditable(false);

        transcript.setPrefRowCount(20);
        transcript.setPrefColumnCount(60);
        transcript.setWrapText(true);
        transcript.setEditable(false);

        HBox connectBar = new HBox(5, listenButton, listeningPortInput, connectButton, remoteHostInput, new Label("port:"), remotePortInput);
        connectBar.setAlignment(Pos.CENTER);

        HBox buttonBar = new HBox(5, quitButton, saveButton, clearButton, closeButton);
        buttonBar.setAlignment(Pos.CENTER);

        VBox topPane = new VBox(8, connectBar, buttonBar);

        BorderPane inputBar = new BorderPane(messageInput);
        inputBar.setLeft(new Label("Your Message:"));
        inputBar.setRight(sendButton);

        BorderPane.setMargin(messageInput, new Insets(0, 5, 0, 5));

        BorderPane root = new BorderPane(transcript);
        root.setTop(topPane);
        root.setBottom(inputBar);

        root.setStyle("-fx-border-color: #444; -fx-border-width: 3px");
        inputBar.setStyle("-fx-padding:5px; -fx-border-color: #444; -fx-border-width: 3px 0 0 0");
        topPane.setStyle("-fx-padding:5px; -fx-border-color: #444; -fx-border-width: 0 0 3px 0");

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.setTitle("Two-user Networked Chat");
        stage.setOnHidden(e -> {
            if (connection != null) connection.close();
        });
        stage.show();
    }

    private void doAction(ActionEvent evt) {
        Object source = evt.getSource();

        if (source == listenButton) {
            handleListen();
        } else if (source == connectButton) {
            handleConnect();
        } else if (source == closeButton) {
            if (connection != null) connection.close();
        } else if (source == clearButton) {
            transcript.setText("");
        } else if (source == quitButton) {
            Platform.exit();
        } else if (source == saveButton) {
            doSave();
        } else if (source == sendButton || source == messageInput) {
            if (connection != null && connection.getConnectionState() == ConnectionState.CONNECTED) {
                connection.send(messageInput.getText());
                messageInput.selectAll();
                messageInput.requestFocus();
            }
        }
    }

    private void handleListen() {
        String portString = listeningPortInput.getText();
        try {
            int port = Integer.parseInt(portString);
            if (port < 0 || port > 65535) throw new NumberFormatException();
            connection = new ConnectionHandler(port);
            listenButton.setDisable(true);
            connectButton.setDisable(true);
            closeButton.setDisable(false);
        } catch (NumberFormatException e) {
            errorMessage(portString + " is not a valid port number.");
        }
    }

    private void handleConnect() {
        String portString = remotePortInput.getText();
        String host = remoteHostInput.getText();
        try {
            int port = Integer.parseInt(portString);
            if (port < 0 || port > 65535) throw new NumberFormatException();
            connection = new ConnectionHandler(host, port);
            listenButton.setDisable(true);
            connectButton.setDisable(true);
        } catch (NumberFormatException e) {
            errorMessage(portString + " is not a valid port number.");
        }
    }

    private void errorMessage(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.showAndWait();
    }

    private void doSave() {
        FileChooser fileDialog = new FileChooser();
        fileDialog.setInitialFileName("transcript.txt");
        fileDialog.setInitialDirectory(new File(System.getProperty("user.home")));
        fileDialog.setTitle("Save Transcript");

        File selectedFile = fileDialog.showSaveDialog(window);
        if (selectedFile != null) {
            try (PrintWriter out = new PrintWriter(new FileWriter(selectedFile))) {
                out.print(transcript.getText());
            } catch (IOException e) {
                errorMessage("Error saving file: " + e.getMessage());
            }
        }
    }

    private class ConnectionHandler extends Thread {
        private volatile ConnectionState state;
        private String remoteHost;
        private int port;
        private ServerSocket listener;
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;

        ConnectionHandler(int port) {
            this.port = port;
            state = ConnectionState.LISTENING;
            start();
        }

        ConnectionHandler(String remoteHost, int port) {
            this.remoteHost = remoteHost;
            this.port = port;
            state = ConnectionState.CONNECTING;
            start();
        }

        synchronized ConnectionState getConnectionState() {
            return state;
        }

        void send(String message) {
            if (out != null) out.println(message);
        }

        void close() {
            try {
                if (socket != null) socket.close();
                if (listener != null) listener.close();
            } catch (IOException ignored) {}
        }
    }
}
