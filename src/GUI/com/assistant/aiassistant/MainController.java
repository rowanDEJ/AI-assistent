package com.assistant.aiassistant;

import com.assistant.aiassistant.chatItemCreatorClasses.ChatItemCreator;
import com.assistant.aiassistant.query_API.ExamplequeryResolutionStrategy;
import com.assistant.aiassistant.query_API.QueryResolutionForm;
import com.assistant.aiassistant.query_API.QueryResolutionResult;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.io.IOException;
import java.util.*;

public class MainController {
    public Button newChatButton;
    @FXML
    private Stage primaryStage;
    public VBox chatNavigationBar;
    public VBox chatBox;
    public Label chatTitle;
    public TextArea bericht;
    public ScrollPane convScrollPane;

    private static final String FILE_PATH = "files/";
    public ArrayList<Conversation> savedConversations;
    private ArrayList<String> createdConversations = new ArrayList<>();

    public AccountManager accountManager = AccountManager.getInstance();
    public Locale appLocale = new Locale(accountManager.getActiveUser().getPreferredLanguage());
    ResourceBundle bundle = ResourceBundle.getBundle("MessageBundle", appLocale);

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }
    @FXML
    private void handleNewChat() {
        showNewChatPopup();
    }

    public void initialize() {
        loadSavedConversations();
        createChatScrollListener();
        fileCreationListener();
        initializeMessagebox();
        loadConversations();

        bericht.setDisable(true);
        bericht.setPromptText(bundle.getString("messagePrompt")); // Bericht...
        chatTitle.setText(bundle.getString("noChat")); // Geen Chat
    }

    private void loadConversations() {
        LoadSavedConversationAction action = new LoadSavedConversationAction();
        action.execute();
        savedConversations = action.savedConversations;
    }

    private void fileCreationListener() {
        FileCreationMonitor monitor = new FileCreationMonitor(new File("files/conversations/" + accountManager.getActiveUser().getUsername()));
        monitor.addObserver(fileName -> {
            Platform.runLater(() -> {
                // fileName is the name of the file that was created
                String topic = fileName.replace(".txt", "");
                createChatNavigationButton(topic);
            });
        });
        monitor.start();
    }

    private void createChatScrollListener() {
        chatBox.heightProperty().addListener((observable, oldValue, newValue) -> {
            Platform.runLater(() -> convScrollPane.setVvalue(1.0));
        });
    }

    private void initializeMessagebox() {
        bericht.setOnKeyPressed(e -> {
            if (e.getCode().toString().equals("ENTER")) {
                String message = bericht.getText();
                message = message.replace("\n", "");
                bericht.clear();
                handleMessage(message);
            }
        });
    }

    private void loadSavedConversations() {
        File folder = new File(FILE_PATH + "conversations/" + accountManager.getActiveUser().getUsername());
        File[] listOfFiles = folder.listFiles();

        if (listOfFiles != null) {
            Arrays.sort(listOfFiles, Comparator.comparingLong(File::lastModified));

            for (File file : listOfFiles) {
                if (file.isFile()) {
                    String topic = file.getName().replace(".txt", "");
                    createChatNavigationButton(topic);
                }
            }
        }
    }

    public void createChatNavigationButton(String topic) {
        if (createdConversations.contains(topic)) {
            return;
        }

        HBox buttonHBox = ChatItemCreator.createChatNavButton(topic, (e -> showChat(topic)));
        chatNavigationBar.getChildren().add(buttonHBox);
        createdConversations.add(topic);
    }

    private void showChat(String topic) {
        chatBox.getChildren().clear();
        chatTitle.setText(topic);
        bericht.setDisable(false);
        for (Conversation conversation : savedConversations) {
            if (conversation.getTopic().equals(topic)) {
                for (String message : conversation.getMessages()) {
                    if (message.startsWith("AI-")) {
                        String contents = message.substring(3);
                        HBox answerBubble = ChatItemCreator.createAnswerBubble(contents);
                        chatBox.getChildren().add(answerBubble);
                    } else {
                        HBox questionBubble = ChatItemCreator.createQuestionBubble(message);
                        chatBox.getChildren().add(questionBubble);
                    }
                }
                break;
            }
        }
    }
    private void showNewChatPopup() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(primaryStage);

        VBox dialogVBox = new VBox(10);
        Label label = new Label(bundle.getString("newChatDialog")); // New chat dialog
        TextField textField = new TextField();
        Button button = new Button(bundle.getString("startChat")); // Start Chat
        button.setOnAction(e -> {
            String topic = textField.getText();

            new StartNewConversationAction(topic, "AI-Hello! How can I help you?").execute();
            loadConversations();
            showChat(topic);

            dialog.close();
        });

        dialogVBox.getChildren().addAll(label, textField, button);

        Scene dialogScene = new Scene(dialogVBox, 300, 200);
        dialog.setScene(dialogScene);
        dialog.setTitle(bundle.getString("newChat")); // New Chat
        dialog.show();
    }

    private Conversation getCurrentlyShowingConversation() {
        String topic = chatTitle.getText();
        for (Conversation conversation : savedConversations) {
            if (conversation.getTopic().equals(topic)) {
                return conversation;
            }
        }
        return null;
    }


    private void handleMessage(String message) {
        if (message.isBlank()) {
            return;
        }

        Conversation conversation = getCurrentlyShowingConversation();

        if(conversation == null) {
            return;
        }

        // save message to the appropriate conversation file
        FileIOManager.addMessageToConversation(message, conversation);

        // create text bubble in gui, with the question in it
        HBox questionBubble = ChatItemCreator.createQuestionBubble(message);
        chatBox.getChildren().add(questionBubble);

        // geef message door aan AI
        sendQueryToAI(message);
    }

    private void sendQueryToAI(String query) {

        Conversation conversation = getCurrentlyShowingConversation();

        if(conversation == null) {
            return;
        }

        QueryResolutionForm<String> queryForm = new QueryResolutionForm<>(query);

        ExamplequeryResolutionStrategy exampleStrategy = new ExamplequeryResolutionStrategy();
        QueryResolutionResult<String> result = exampleStrategy.resolve(queryForm);

        String resultData = result.getData();

        FileIOManager.addMessageToConversation("AI-" + resultData, conversation);

        HBox questionBubble = ChatItemCreator.createAnswerBubble(resultData);
        chatBox.getChildren().add(questionBubble);
    }

    public void showSettings() throws IOException {
        UserInterfaceManager uiManager = UserInterfaceManager.getInstance();
        uiManager.switchCurrentViewTo(uiManager.settingsViewFilename);
    }
}