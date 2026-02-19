package me.maxih.itunes_backup_explorer.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import me.maxih.itunes_backup_explorer.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class WhatsAppTabController {
    private static final Logger logger = LoggerFactory.getLogger(WhatsAppTabController.class);
    private static final int PAGE_SIZE = 100;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter SHORT_DATE_FMT = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final String WHATSAPP_DOMAIN = "%group.net.whatsapp%";
    private static final String CHAT_STORAGE_PATTERN = "%ChatStorage.sqlite";

    private static final String[] SENDER_COLORS = {
            "#e06c75", "#e5c07b", "#98c379", "#56b6c2",
            "#61afef", "#c678dd", "#d19a66", "#be5046"
    };

    private record DbLoadResult(WhatsAppDatabaseService service, String domain) {}

    private ITunesBackup selectedBackup;
    private WhatsAppDatabaseService databaseService;
    private File tempDbFile;
    private String whatsappDomain;
    private final Map<String, Image> thumbnailCache = new HashMap<>();
    private File thumbnailCacheDir;
    private List<WhatsAppChat> allChats = new ArrayList<>();
    private FilteredList<WhatsAppChat> filteredChats;
    private WhatsAppChat selectedChat;
    private int loadedMessageCount;
    private int totalMessageCount;
    private List<WhatsAppMessage> allLoadedMessages = new ArrayList<>();

    @FXML SplitPane splitPane;
    @FXML TextField chatSearchField;
    @FXML ToggleButton filterAll;
    @FXML ToggleButton filterPrivate;
    @FXML ToggleButton filterGroups;
    @FXML ToggleGroup chatFilterGroup;
    @FXML Label chatCountLabel;
    @FXML ListView<WhatsAppChat> chatListView;
    @FXML VBox conversationHeaderBox;
    @FXML Label conversationHeader;
    @FXML Label conversationInfo;
    @FXML ScrollPane messagesScrollPane;
    @FXML VBox messagesContainer;
    @FXML VBox emptyStatePane;
    @FXML Button loadOlderButton;
    @FXML Label messageCountLabel;
    @FXML TextField messageSearchField;
    @FXML Button exportButton;

    @FXML
    public void initialize() {
        chatListView.setCellFactory(listView -> new ChatListCell());

        chatListView.getSelectionModel().selectedItemProperty().addListener((obs, oldChat, newChat) -> {
            if (newChat != null) onChatSelected(newChat);
        });

        chatSearchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilter());

        chatFilterGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null) {
                filterAll.setSelected(true);
                return;
            }
            applyFilter();
        });

        messageSearchField.textProperty().addListener((obs, oldVal, newVal) -> filterMessages(newVal));
    }

    public void tabShown(ITunesBackup backup) {
        if (backup == this.selectedBackup && databaseService != null) return;

        cleanup();
        this.selectedBackup = backup;

        javafx.concurrent.Task<DbLoadResult> task = new javafx.concurrent.Task<>() {
            @Override
            protected DbLoadResult call() throws Exception {
                List<BackupFile> results = backup.searchFiles(WHATSAPP_DOMAIN, CHAT_STORAGE_PATTERN);

                BackupFile chatStorageFile = results.stream()
                        .filter(f -> f.getFileType() == BackupFile.FileType.FILE)
                        .filter(f -> f.relativePath.endsWith("ChatStorage.sqlite"))
                        .findFirst()
                        .orElse(null);

                if (chatStorageFile == null) return null;

                logger.info("Found ChatStorage.sqlite in domain '{}' at '{}'",
                        chatStorageFile.domain, chatStorageFile.relativePath);

                File temp = File.createTempFile("whatsapp_chat_", ".sqlite");
                temp.deleteOnExit();
                tempDbFile = temp;
                chatStorageFile.extract(temp);

                return new DbLoadResult(new WhatsAppDatabaseService(temp), chatStorageFile.domain);
            }
        };

        task.setOnSucceeded(event -> {
            DbLoadResult result = task.getValue();
            if (result == null) {
                showEmptyState("No WhatsApp data found in this backup");
                return;
            }
            databaseService = result.service();
            whatsappDomain = result.domain();
            try {
                thumbnailCacheDir = Files.createTempDirectory("whatsapp_thumbs_").toFile();
                thumbnailCacheDir.deleteOnExit();
            } catch (IOException e) {
                logger.warn("Failed to create thumbnail cache directory", e);
            }
            loadChats();
        });

        task.setOnFailed(event -> {
            logger.error("Failed to open WhatsApp database", task.getException());
            showEmptyState("Failed to load WhatsApp data");
        });

        Thread thread = new Thread(task, "whatsapp-db-extract");
        thread.setDaemon(true);
        thread.start();
    }

    private void loadChats() {
        javafx.concurrent.Task<List<WhatsAppChat>> task = new javafx.concurrent.Task<>() {
            @Override
            protected List<WhatsAppChat> call() throws Exception {
                return databaseService.queryChats();
            }
        };

        task.setOnSucceeded(event -> {
            allChats = task.getValue();
            filteredChats = new FilteredList<>(FXCollections.observableArrayList(allChats));
            chatListView.setItems(filteredChats);
            applyFilter();
        });

        task.setOnFailed(event -> {
            logger.error("Failed to load WhatsApp chats", task.getException());
            showEmptyState("Failed to load conversations");
        });

        Thread thread = new Thread(task, "whatsapp-load-chats");
        thread.setDaemon(true);
        thread.start();
    }

    private void applyFilter() {
        if (filteredChats == null) return;

        String searchText = chatSearchField.getText() == null ? "" : chatSearchField.getText().trim().toLowerCase(Locale.ROOT);
        Toggle selected = chatFilterGroup.getSelectedToggle();

        filteredChats.setPredicate(chat -> {
            if (selected == filterPrivate && chat.isGroup()) return false;
            if (selected == filterGroups && !chat.isGroup()) return false;

            if (!searchText.isEmpty()) {
                String name = chat.displayName().toLowerCase(Locale.ROOT);
                String jid = chat.contactJid() != null ? chat.contactJid().toLowerCase(Locale.ROOT) : "";
                return name.contains(searchText) || jid.contains(searchText);
            }
            return true;
        });

        chatCountLabel.setText(filteredChats.size() + " chats");
    }

    private void onChatSelected(WhatsAppChat chat) {
        this.selectedChat = chat;
        this.loadedMessageCount = 0;
        this.totalMessageCount = chat.messageCount();
        this.allLoadedMessages.clear();
        this.messagesContainer.getChildren().clear();
        this.messageSearchField.clear();

        conversationHeaderBox.setVisible(true);
        conversationHeaderBox.setManaged(true);
        conversationHeader.setText(chat.displayName());
        conversationInfo.setText(chat.isGroup()
                ? "Group \u00b7 " + chat.messageCount() + " messages"
                : chat.messageCount() + " messages");

        emptyStatePane.setVisible(false);
        exportButton.setDisable(false);

        loadMessages(false);
    }

    private void loadMessages(boolean loadOlder) {
        if (selectedChat == null || databaseService == null) return;

        javafx.concurrent.Task<List<WhatsAppMessage>> task = new javafx.concurrent.Task<>() {
            @Override
            protected List<WhatsAppMessage> call() throws Exception {
                int offset = loadOlder ? loadedMessageCount : 0;
                return databaseService.queryMessages(selectedChat.id(), PAGE_SIZE, offset);
            }
        };

        task.setOnSucceeded(event -> {
            List<WhatsAppMessage> newMessages = task.getValue();
            if (newMessages.isEmpty()) {
                loadOlderButton.setDisable(true);
                if (!loadOlder) {
                    updateMessageCountLabel();
                    logger.warn("No messages returned for chat '{}' (Z_PK={}), expected {}",
                            selectedChat.displayName(), selectedChat.id(), totalMessageCount);
                }
                return;
            }

            Collections.reverse(newMessages);

            boolean isGroup = selectedChat.isGroup();
            List<Node> bubbles = newMessages.stream()
                    .map(msg -> renderMessage(msg, isGroup))
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());

            if (loadOlder) {
                allLoadedMessages.addAll(0, newMessages);
                messagesContainer.getChildren().addAll(0, bubbles);
            } else {
                allLoadedMessages.addAll(newMessages);
                messagesContainer.getChildren().addAll(bubbles);
            }

            loadedMessageCount += newMessages.size();
            updateMessageCountLabel();
            loadOlderButton.setDisable(loadedMessageCount >= totalMessageCount);

            if (!loadOlder) {
                Platform.runLater(() -> messagesScrollPane.setVvalue(1.0));
            }
        });

        task.setOnFailed(event -> {
            logger.error("Failed to load messages", task.getException());
            Dialogs.showAlert(Alert.AlertType.ERROR,
                    "Failed to load messages: " + task.getException().getMessage());
        });

        Thread thread = new Thread(task, "whatsapp-load-messages");
        thread.setDaemon(true);
        thread.start();
    }

    private Node renderMessage(WhatsAppMessage msg, boolean isGroup) {
        boolean hasText = msg.text() != null && !msg.text().isBlank();
        boolean hasMedia = msg.media() != null;
        boolean hasThumbnail = hasMedia && msg.media().thumbnailData() != null && msg.media().thumbnailData().length > 0;
        String typeLabel = getMessageTypeLabel(msg.messageType());

        if (!hasText && !hasMedia && typeLabel == null) {
            return null;
        }

        VBox bubble = new VBox(2);
        bubble.setMaxWidth(420);
        bubble.getStyleClass().add("whatsapp-bubble");
        bubble.getStyleClass().add(msg.fromMe() ? "whatsapp-bubble-sent" : "whatsapp-bubble-received");

        if (isGroup && !msg.fromMe()) {
            String senderName = msg.pushName() != null && !msg.pushName().isBlank() ? msg.pushName() :
                    (msg.fromJid() != null ? "+" + msg.fromJid().split("@")[0] : "Unknown");
            Label senderLabel = new Label(senderName);
            senderLabel.getStyleClass().add("whatsapp-sender-name");
            senderLabel.setStyle("-fx-text-fill: " + getSenderColor(msg.fromJid()));
            bubble.getChildren().add(senderLabel);
        }

        if (hasThumbnail) {
            try {
                Image img = new Image(new ByteArrayInputStream(msg.media().thumbnailData()));
                if (!img.isError()) {
                    ImageView iv = new ImageView(img);
                    iv.setFitWidth(200);
                    iv.setPreserveRatio(true);
                    iv.setSmooth(true);
                    iv.getStyleClass().add("whatsapp-media-thumbnail");
                    bubble.getChildren().add(iv);
                } else {
                    addMediaPlaceholder(bubble, typeLabel);
                }
            } catch (Exception e) {
                addMediaPlaceholder(bubble, typeLabel);
            }
        } else if (hasMedia && msg.media().localPath() != null) {
            Image thumbImage = loadThumbnail(msg.media().localPath());
            if (thumbImage == null && isImageType(msg)) {
                thumbImage = loadMediaFile(msg.media().localPath());
            }
            if (thumbImage != null) {
                ImageView iv = new ImageView(thumbImage);
                iv.setFitWidth(200);
                iv.setPreserveRatio(true);
                iv.setSmooth(true);
                iv.getStyleClass().add("whatsapp-media-thumbnail");
                bubble.getChildren().add(iv);
            } else {
                addMediaPlaceholderWithDetails(bubble, typeLabel, msg);
            }
        } else if (hasMedia) {
            addMediaPlaceholderWithDetails(bubble, typeLabel, msg);
        }

        if (hasText) {
            Label textLabel = new Label(msg.text());
            textLabel.setWrapText(true);
            textLabel.getStyleClass().add("whatsapp-message-text");
            bubble.getChildren().add(textLabel);
        } else if (!hasMedia && typeLabel != null) {
            Label lbl = new Label(typeLabel);
            lbl.getStyleClass().add("whatsapp-media-placeholder");
            bubble.getChildren().add(lbl);
        }

        HBox metaRow = new HBox(4);
        metaRow.setAlignment(msg.fromMe() ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        if (msg.messageDate() != null && !msg.messageDate().equals(Instant.EPOCH)) {
            Label timeLabel = new Label(TIME_FMT.format(msg.messageDate()));
            timeLabel.getStyleClass().add("whatsapp-timestamp");
            metaRow.getChildren().add(timeLabel);
        }

        if (msg.starred()) {
            Label star = new Label("\u2605");
            star.getStyleClass().add("whatsapp-star");
            metaRow.getChildren().add(star);
        }

        bubble.getChildren().add(metaRow);

        HBox row = new HBox();
        row.setPadding(new Insets(2, 8, 2, 8));
        row.setAlignment(msg.fromMe() ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.getChildren().add(bubble);

        return row;
    }

    private void addMediaPlaceholder(VBox bubble, String typeLabel) {
        Label lbl = new Label(typeLabel != null ? typeLabel : "\uD83D\uDCCE Media");
        lbl.getStyleClass().add("whatsapp-media-placeholder");
        bubble.getChildren().add(lbl);
    }

    private void addMediaPlaceholderWithDetails(VBox bubble, String typeLabel, WhatsAppMessage msg) {
        VBox mediaBox = new VBox(2);
        mediaBox.getStyleClass().add("whatsapp-media-placeholder");
        mediaBox.setAlignment(Pos.CENTER);
        mediaBox.setMinHeight(60);

        String label = typeLabel != null ? typeLabel : "\uD83D\uDCCE Media";
        if (msg.media().caption() != null && !msg.media().caption().isBlank()) {
            label = label + "\n" + msg.media().caption();
        }
        Label mediaLabel = new Label(label);
        mediaLabel.setWrapText(true);
        mediaLabel.setAlignment(Pos.CENTER);
        mediaBox.getChildren().add(mediaLabel);

        if (msg.media().fileSize() > 0) {
            Label sizeLabel = new Label(formatFileSize(msg.media().fileSize()));
            sizeLabel.setStyle("-fx-font-size: 10px; -fx-opacity: 0.7;");
            mediaBox.getChildren().add(sizeLabel);
        }

        bubble.getChildren().add(mediaBox);
    }

    private Image loadThumbnail(String mediaLocalPath) {
        if (mediaLocalPath == null || selectedBackup == null || whatsappDomain == null || thumbnailCacheDir == null)
            return null;

        Image cached = thumbnailCache.get(mediaLocalPath);
        if (cached != null) return cached;

        try {
            String thumbRelativePath = "Message/" + mediaLocalPath.replaceAll("\\.[^.]+$", ".thumb");

            List<BackupFile> results = selectedBackup.searchFiles(whatsappDomain, thumbRelativePath);
            BackupFile thumbFile = results.stream()
                    .filter(f -> f.getFileType() == BackupFile.FileType.FILE)
                    .findFirst()
                    .orElse(null);

            if (thumbFile == null) {
                results = selectedBackup.searchFiles(whatsappDomain, mediaLocalPath.replaceAll("\\.[^.]+$", ".thumb"));
                thumbFile = results.stream()
                        .filter(f -> f.getFileType() == BackupFile.FileType.FILE)
                        .findFirst()
                        .orElse(null);
            }

            if (thumbFile == null) return null;

            File tempFile = new File(thumbnailCacheDir, thumbFile.fileID + ".thumb");
            if (!tempFile.exists()) {
                thumbFile.extract(tempFile);
            }

            Image image = new Image(tempFile.toURI().toString(), 200, 0, true, true);
            if (!image.isError()) {
                thumbnailCache.put(mediaLocalPath, image);
                return image;
            }
        } catch (Exception e) {
            logger.debug("Failed to load thumbnail for {}", mediaLocalPath, e);
        }
        return null;
    }

    private boolean isImageType(WhatsAppMessage msg) {
        return msg.messageType() == 1 ||
            (msg.media() != null && msg.media().mimeType() != null && msg.media().mimeType().startsWith("image/"));
    }

    private Image loadMediaFile(String localPath) {
        if (localPath == null || selectedBackup == null || whatsappDomain == null || thumbnailCacheDir == null)
            return null;

        String cacheKey = "media:" + localPath;
        Image cached = thumbnailCache.get(cacheKey);
        if (cached != null) return cached;

        String[] patterns = {
            "Message/" + localPath,
            localPath
        };

        for (String pattern : patterns) {
            try {
                List<BackupFile> results = selectedBackup.searchFiles(whatsappDomain, pattern);
                BackupFile mediaFile = results.stream()
                    .filter(f -> f.getFileType() == BackupFile.FileType.FILE)
                    .findFirst()
                    .orElse(null);

                if (mediaFile != null) {
                    File tempFile = new File(thumbnailCacheDir, "media_" + mediaFile.fileID);
                    if (!tempFile.exists()) {
                        mediaFile.extract(tempFile);
                    }
                    Image image = new Image(tempFile.toURI().toString(), 200, 0, true, true);
                    if (!image.isError()) {
                        thumbnailCache.put(cacheKey, image);
                        return image;
                    }
                }
            } catch (Exception e) {
                logger.debug("Failed to load media file {}", localPath, e);
            }
        }
        return null;
    }

    private String getMessageTypeLabel(int type) {
        return switch (type) {
            case 0 -> null;
            case 1 -> "\uD83D\uDCF7 Photo";
            case 2 -> "\uD83C\uDFA5 Video";
            case 3, 8 -> "\uD83C\uDFA4 Audio";
            case 4 -> "\uD83D\uDC64 Contact";
            case 5, 16 -> "\uD83D\uDCCD Location";
            case 6 -> null;
            case 7 -> "\uD83D\uDCC4 Document";
            case 9, 13 -> "\uD83C\uDFA5 GIF";
            case 10 -> "\uD83D\uDCDE Call";
            case 11 -> "\uD83D\uDD12 Encryption changed";
            case 14 -> "\u26D4 Deleted";
            case 15 -> "Sticker";
            case 46 -> "\uD83D\uDCCA Poll";
            default -> null;
        };
    }

    private void filterMessages(String query) {
        if (query == null || query.isBlank()) {
            messagesContainer.getChildren().forEach(node -> {
                node.setVisible(true);
                node.setManaged(true);
            });
            return;
        }

        String lowerQuery = query.toLowerCase(Locale.ROOT);
        for (int i = 0; i < messagesContainer.getChildren().size() && i < allLoadedMessages.size(); i++) {
            WhatsAppMessage msg = allLoadedMessages.get(i);
            Node node = messagesContainer.getChildren().get(i);
            boolean matches = (msg.text() != null && msg.text().toLowerCase(Locale.ROOT).contains(lowerQuery))
                    || (msg.pushName() != null && msg.pushName().toLowerCase(Locale.ROOT).contains(lowerQuery));
            node.setVisible(matches);
            node.setManaged(matches);
        }
    }

    private void updateMessageCountLabel() {
        messageCountLabel.setText(loadedMessageCount + " of " + totalMessageCount + " messages");
    }

    private void showEmptyState(String message) {
        emptyStatePane.setVisible(true);
        if (!emptyStatePane.getChildren().isEmpty() && emptyStatePane.getChildren().get(0) instanceof Label label) {
            label.setText(message);
        }
        conversationHeaderBox.setVisible(false);
        conversationHeaderBox.setManaged(false);
        chatListView.setItems(FXCollections.observableArrayList());
        chatCountLabel.setText("0 chats");
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private String getSenderColor(String jid) {
        if (jid == null) return SENDER_COLORS[0];
        int hash = Math.abs(jid.hashCode());
        return SENDER_COLORS[hash % SENDER_COLORS.length];
    }

    private void cleanup() {
        if (databaseService != null) {
            databaseService.close();
            databaseService = null;
        }
        if (tempDbFile != null) {
            tempDbFile.delete();
            tempDbFile = null;
        }
        thumbnailCache.clear();
        whatsappDomain = null;
        if (thumbnailCacheDir != null) {
            File[] files = thumbnailCacheDir.listFiles();
            if (files != null) for (File f : files) f.delete();
            thumbnailCacheDir.delete();
            thumbnailCacheDir = null;
        }
        allChats.clear();
        allLoadedMessages.clear();
        selectedChat = null;
        loadedMessageCount = 0;
        totalMessageCount = 0;
        messagesContainer.getChildren().clear();
        conversationHeaderBox.setVisible(false);
        conversationHeaderBox.setManaged(false);
        emptyStatePane.setVisible(true);
        exportButton.setDisable(true);
        loadOlderButton.setDisable(true);
        messageCountLabel.setText("");
        messageSearchField.clear();
    }

    @FXML
    private void onLoadOlder() {
        loadMessages(true);
    }

    @FXML
    private void onSearchMessages() {
        filterMessages(messageSearchField.getText());
    }

    @FXML
    private void onExportChat() {
        if (selectedChat == null || allLoadedMessages.isEmpty()) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Chat");
        chooser.setInitialFileName(selectedChat.displayName().replaceAll("[^a-zA-Z0-9._\\- ]", "_") + ".txt");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        File destination = chooser.showSaveDialog(splitPane.getScene().getWindow());
        if (destination == null) return;

        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() throws Exception {
                List<WhatsAppMessage> allMessages = databaseService.queryMessages(
                        selectedChat.id(), selectedChat.messageCount(), 0);
                Collections.reverse(allMessages);

                try (BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(destination), StandardCharsets.UTF_8))) {
                    for (WhatsAppMessage msg : allMessages) {
                        String date = msg.messageDate() != null && !msg.messageDate().equals(Instant.EPOCH)
                                ? DATETIME_FMT.format(msg.messageDate()) : "??";
                        String sender = msg.fromMe() ? "You" :
                                (msg.pushName() != null ? msg.pushName() : selectedChat.displayName());
                        String text = msg.text() != null ? msg.text() : "";
                        if (msg.media() != null) {
                            String mediaCaption = msg.media().caption() != null ? msg.media().caption() : "";
                            text = "<media> " + mediaCaption + (text.isEmpty() ? "" : " " + text);
                        }
                        if (text.isEmpty()) text = getMessageTypeLabel(msg.messageType());
                        writer.write("[" + date + "] " + sender + ": " + text);
                        writer.newLine();
                    }
                }
                return null;
            }
        };

        task.setOnFailed(event -> {
            logger.error("Failed to export chat", task.getException());
            Dialogs.showAlert(Alert.AlertType.ERROR, "Failed to export chat: " + task.getException().getMessage());
        });

        Thread thread = new Thread(task, "whatsapp-export");
        thread.setDaemon(true);
        thread.start();
    }

    private static class ChatListCell extends ListCell<WhatsAppChat> {
        private final VBox container = new VBox(2);
        private final HBox topRow = new HBox(8);
        private final Label nameLabel = new Label();
        private final Label timeLabel = new Label();
        private final HBox bottomRow = new HBox(8);
        private final Label previewLabel = new Label();
        private final Label badgeLabel = new Label();

        ChatListCell() {
            container.setPadding(new Insets(8, 10, 8, 10));
            container.getStyleClass().add("whatsapp-chat-cell");

            nameLabel.getStyleClass().add("whatsapp-chat-name");
            nameLabel.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(nameLabel, Priority.ALWAYS);
            timeLabel.getStyleClass().add("whatsapp-chat-time");
            timeLabel.setMinWidth(Region.USE_PREF_SIZE);

            topRow.setAlignment(Pos.CENTER_LEFT);
            topRow.getChildren().addAll(nameLabel, timeLabel);

            previewLabel.getStyleClass().add("whatsapp-chat-preview");
            previewLabel.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(previewLabel, Priority.ALWAYS);
            badgeLabel.getStyleClass().add("whatsapp-chat-badge");
            badgeLabel.setMinWidth(Region.USE_PREF_SIZE);

            bottomRow.setAlignment(Pos.CENTER_LEFT);
            bottomRow.getChildren().addAll(previewLabel, badgeLabel);

            container.getChildren().addAll(topRow, bottomRow);
        }

        @Override
        protected void updateItem(WhatsAppChat chat, boolean empty) {
            super.updateItem(chat, empty);
            if (empty || chat == null) {
                setText(null);
                setGraphic(null);
            } else {
                nameLabel.setText(chat.displayName());
                timeLabel.setText(formatChatTime(chat.lastMessageDate()));

                String preview = chat.lastMessagePreview();
                previewLabel.setText(preview != null ? truncate(preview, 60) : "");
                int count = chat.messageCount();
                if (count > 0) {
                    badgeLabel.setText(String.valueOf(count));
                    badgeLabel.setVisible(true);
                    badgeLabel.setManaged(true);
                } else {
                    badgeLabel.setText("");
                    badgeLabel.setVisible(false);
                    badgeLabel.setManaged(false);
                }

                setGraphic(container);
            }
        }

        private String formatChatTime(Instant instant) {
            if (instant == null || instant.equals(Instant.EPOCH)) return "";
            return SHORT_DATE_FMT.format(instant);
        }

        private String truncate(String text, int maxLen) {
            if (text == null) return "";
            String singleLine = text.replace('\n', ' ').replace('\r', ' ');
            return singleLine.length() <= maxLen ? singleLine : singleLine.substring(0, maxLen) + "...";
        }
    }
}
