package me.maxih.itunes_backup_explorer.ui;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import me.maxih.itunes_backup_explorer.ITunesBackupExplorer;
import me.maxih.itunes_backup_explorer.api.BackupReadException;
import me.maxih.itunes_backup_explorer.api.ITunesBackup;
import me.maxih.itunes_backup_explorer.api.NotUnlockedException;
import me.maxih.itunes_backup_explorer.api.UnsupportedCryptoException;
import me.maxih.itunes_backup_explorer.util.FileSize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class WindowController {
    private static final Logger logger = LoggerFactory.getLogger(WindowController.class);
    static final DateTimeFormatter BACKUP_DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    List<ITunesBackup> backups = new ArrayList<>();
    ITunesBackup selectedBackup;
    final Map<ITunesBackup, ToggleButton> sidebarButtons = new HashMap<>();

    List<Node> lockedTabPages = new ArrayList<>();

    @FXML
    VBox backupSidebarBox;

    @FXML
    VBox welcomePane;

    @FXML
    TabPane tabPane;

    @FXML
    AnchorPane infoTabPage;
    @FXML
    InfoTabController infoTabPageController;

    @FXML
    AnchorPane mediaTabPage;
    @FXML
    MediaTabController mediaTabPageController;

    @FXML
    AnchorPane filesTabPage;
    @FXML
    FilesTabController filesTabPageController;

    @FXML
    AnchorPane appsTabPage;
    @FXML
    AppsTabController appsTabPageController;

    @FXML
    Label statusTotalFiles;
    @FXML
    Label statusBackupSize;
    @FXML
    Label statusEncryption;
    @FXML
    Label statusBackupPath;

    @FXML
    public void initialize() {
        this.lockedTabPages = Arrays.asList(this.filesTabPage, this.mediaTabPage);

        this.tabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) -> {
            if (newTab == null || this.selectedBackup == null) return;

            Node tabPage = newTab.getContent();
            if (this.lockedTabPages.contains(tabPage) && !this.tryUnlock()) {
                if (oldTab != null) this.tabPane.getSelectionModel().select(oldTab);
            } else if (tabPage == this.filesTabPage) {
                this.filesTabPageController.tabShown(this.selectedBackup);
            } else if (tabPage == this.mediaTabPage) {
                this.mediaTabPageController.tabShown(this.selectedBackup);
            } else if (tabPage == this.appsTabPage) {
                this.appsTabPageController.tabShown(this.selectedBackup);
            }
        });

        this.tabPane.setVisible(false);
        this.tabPane.setManaged(false);
        this.welcomePane.setVisible(true);
        this.welcomePane.setManaged(true);
        this.welcomePane.setMouseTransparent(false);

        this.loadBackups();
        this.setupKeyboardShortcuts();
        this.setupDragAndDrop();

        Platform.runLater(this::applyTheme);
    }

    private void setupKeyboardShortcuts() {
        Platform.runLater(() -> {
            Scene scene = tabPane.getScene();
            if (scene != null) {
                scene.getAccelerators().put(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN), this::fileOpenBackup);
                scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN), () -> tabPane.getSelectionModel().select(2));
                scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Q, KeyCombination.CONTROL_DOWN), this::quit);
                scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F5), this::loadBackups);
            }
        });
    }

    private void setupDragAndDrop() {
        backupSidebarBox.setOnDragOver(event -> {
            if (event.getGestureSource() != backupSidebarBox && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        backupSidebarBox.setOnDragEntered(event -> {
            if (event.getGestureSource() != backupSidebarBox && event.getDragboard().hasFiles()) {
                backupSidebarBox.getStyleClass().add("drag-over");
            }
            event.consume();
        });

        backupSidebarBox.setOnDragExited(event -> {
            backupSidebarBox.getStyleClass().remove("drag-over");
            event.consume();
        });

        backupSidebarBox.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                for (File file : db.getFiles()) {
                    if (file.isDirectory()) {
                        openBackup(file);
                        success = true;
                    } else if (file.getName().equals("Manifest.plist") || file.getName().equals("Manifest.db")) {
                        openBackup(file.getParentFile());
                        success = true;
                    }
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void openBackup(File backupDirectory) {
        try {
            ITunesBackup backup = new ITunesBackup(backupDirectory);
            this.loadBackup(backup);
            this.selectBackup(backup);
        } catch (FileNotFoundException e) {
            Dialogs.showAlert(Alert.AlertType.ERROR, "O arquivo não foi encontrado: " + e.getMessage());
        } catch (BackupReadException e) {
            Dialogs.showAlert(Alert.AlertType.ERROR, e.getMessage());
        }
    }

    public void cleanUp() {
        this.backups.forEach(ITunesBackup::cleanUp);
        ThumbnailService.getInstance().shutdown();
    }

    public void loadBackup(ITunesBackup backup) {
        if (this.backups.stream().anyMatch(existing -> existing.directory.equals(backup.directory))) {
            return;
        }

        ToggleButton backupEntry = new ToggleButton(backup.manifest.deviceName + "\n" + BACKUP_DATE_FMT.format(
                backup.getBackupInfo().map(info -> info.lastBackupDate).orElse(backup.manifest.date).toInstant()));
        backupEntry.getStyleClass().add("sidebar-button");
        backupEntry.setOnAction(this::backupSelected);
        backupEntry.setMaxWidth(Integer.MAX_VALUE);
        backupEntry.setPrefHeight(60);
        backupEntry.setAlignment(Pos.BASELINE_LEFT);
        backupEntry.setPadding(new Insets(0, 24, 0, 24));
        backupEntry.setId(backup.directory.getName());

        MenuItem openBackupDirectory = new MenuItem("Open backup directory");
        openBackupDirectory.setOnAction(event -> {
            try {
                Desktop.getDesktop().browse(backup.directory.toURI());
            } catch (IOException e) {
                logger.error("Falha ao abrir diretório do backup", e);
            }
        });

        MenuItem closeBackup = new MenuItem("Close backup");
        closeBackup.setOnAction(event -> {
            backup.cleanUp();
            if (this.selectedBackup == backup) {
                this.selectedBackup = null;
            }
            this.backups.remove(backup);
            this.backupSidebarBox.getChildren().remove(backupEntry);
            this.sidebarButtons.remove(backup);

            if (this.backups.isEmpty()) {
                showWelcome();
            } else if (this.selectedBackup == null) {
                selectBackup(this.backups.get(0));
            }
            updateStatusBar();
        });

        ContextMenu backupContextMenu = new ContextMenu(openBackupDirectory, closeBackup);
        backupEntry.setContextMenu(backupContextMenu);

        this.backups.add(backup);
        this.backupSidebarBox.getChildren().add(backupEntry);
        this.sidebarButtons.put(backup, backupEntry);
        this.backups.sort(Comparator.comparing((ITunesBackup b) -> b.manifest.date).reversed());
        this.backupSidebarBox.getChildren().sort(Comparator.comparing(node -> {
            ToggleButton button = (ToggleButton) node;
            return this.backups.stream().filter(backup_ -> sidebarButtons.get(backup_) == button)
                    .map(backup_ -> backup_.manifest.date).findFirst().orElse(new Date(0));
        }, Comparator.reverseOrder()));
    }

    public void loadBackups() {
        ITunesBackup previousSelected = this.selectedBackup;

        this.backupSidebarBox.getChildren().clear();
        this.sidebarButtons.clear();
        this.backups.forEach(ITunesBackup::cleanUp);
        this.backups.clear();

        for (String root : PreferencesController.getBackupRoots()) {
            ITunesBackup.getBackups(new File(root)).forEach(this::loadBackup);
        }

        this.backups.sort(Comparator.comparing((ITunesBackup b) -> b.manifest.date).reversed());

        if (this.backups.isEmpty()) {
            this.selectedBackup = null;
            showWelcome();
            updateStatusBar();
            return;
        }

        Optional<ITunesBackup> byPrevious = previousSelected == null ? Optional.empty() :
                this.backups.stream().filter(backup -> backup.directory.equals(previousSelected.directory)).findFirst();

        if (byPrevious.isPresent()) {
            selectBackup(byPrevious.get());
        } else if (PreferencesController.getAutoSelectNewestBackup()) {
            selectBackup(this.backups.get(0));
        } else {
            this.selectedBackup = null;
            showWelcome();
            updateStatusBar();
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean tryUnlock() {
        if (this.selectedBackup == null) return false;
        if (!this.selectedBackup.isLocked()) return true;
        if (this.selectedBackup.manifest.getKeyBag().isEmpty()) return false;

        Optional<char[]> response = Dialogs.askPassword();
        if (response.isEmpty()) return false;
        char[] password = response.get();

        try {
            selectedBackup.manifest.getKeyBag().get().unlock(password);
            selectedBackup.decryptDatabase();
            return true;
        } catch (InvalidKeyException e) {
            Dialogs.showAlert(Alert.AlertType.ERROR, "The given password is not valid");
        } catch (BackupReadException e) {
            Dialogs.showAlert(Alert.AlertType.ERROR, "The backup could not be read");
        } catch (UnsupportedCryptoException e) {
            Dialogs.showAlert(Alert.AlertType.ERROR, "Your system doesn't support the necessary cryptography");
        } catch (NotUnlockedException e) {
            logger.error("Backup não desbloqueado", e);
        } catch (IOException e) {
            Dialogs.showAlert(Alert.AlertType.ERROR, e.getMessage());
        }
        return false;
    }

    public void selectBackup(ITunesBackup backup) {
        ToggleButton selectedButton = this.sidebarButtons.get(backup);
        if (selectedButton == null) return;
        selectedButton.setSelected(true);
        if (backup == this.selectedBackup) return;

        if (selectedBackup != null && sidebarButtons.get(selectedBackup) != null) {
            sidebarButtons.get(selectedBackup).setSelected(false);
        }
        this.selectedBackup = backup;

        showTabs();

        this.infoTabPageController.updateInformation(backup.manifest, backup.getBackupInfo().orElse(null));
        this.updateStatusBar();

        Node selectedTabPage = this.tabPane.getSelectionModel().getSelectedItem().getContent();
        if (this.lockedTabPages.contains(selectedTabPage) && !this.tryUnlock()) {
            this.tabPane.getSelectionModel().select(0);
        } else if (selectedTabPage == this.filesTabPage) {
            this.filesTabPageController.tabShown(backup);
        } else if (selectedTabPage == this.mediaTabPage) {
            this.mediaTabPageController.tabShown(backup);
        } else if (selectedTabPage == this.appsTabPage) {
            this.appsTabPageController.tabShown(backup);
        }
    }

    private void showWelcome() {
        this.tabPane.setVisible(false);
        this.tabPane.setManaged(false);
        this.welcomePane.setVisible(true);
        this.welcomePane.setManaged(true);
        this.welcomePane.setMouseTransparent(false);
    }

    private void showTabs() {
        this.tabPane.setVisible(true);
        this.tabPane.setManaged(true);
        this.welcomePane.setVisible(false);
        this.welcomePane.setManaged(false);
        this.welcomePane.setMouseTransparent(true);
    }

    private void applyTheme() {
        Scene scene = tabPane.getScene();
        if (scene == null) return;

        Parent root = scene.getRoot();
        root.getStyleClass().removeAll("theme-dark", "theme-light");
        String theme = "Light".equalsIgnoreCase(PreferencesController.getTheme()) ? "theme-light" : "theme-dark";
        root.getStyleClass().add(theme);
    }

    @FXML
    public void backupSelected(ActionEvent event) {
        ToggleButton button = (ToggleButton) event.getSource();

        this.sidebarButtons.entrySet().stream()
                .filter(entry -> entry.getValue() == button)
                .findFirst()
                .map(Map.Entry::getKey)
                .ifPresent(this::selectBackup);
    }

    @FXML
    public void openPreferences() {
        FXMLLoader fxmlLoader = new FXMLLoader(ITunesBackupExplorer.class.getResource("preferences.fxml"));
        try {
            Parent root = fxmlLoader.load();
            PreferencesController controller = fxmlLoader.getController();
            controller.reloadCallback = this::loadBackups;
            controller.preferencesChangedCallback = this::applyTheme;

            Stage prefsWindow = new Stage();
            prefsWindow.initModality(Modality.APPLICATION_MODAL);
            prefsWindow.initOwner(tabPane.getScene().getWindow());

            Scene prefsScene = new Scene(root, 760, 620);
            prefsWindow.setScene(prefsScene);
            prefsWindow.setTitle("Preferences");
            prefsWindow.getIcons().add(ITunesBackupExplorer.APP_ICON);
            prefsWindow.show();
        } catch (IOException e) {
            logger.error("Falha ao carregar preferências", e);
            Dialogs.showAlert(Alert.AlertType.ERROR, e.getMessage());
        }
    }

    @FXML
    public void fileOpenBackup() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("iTunes Backup", "Manifest.plist", "Manifest.db"));
        File source = chooser.showOpenDialog(tabPane.getScene().getWindow());
        if (source == null) return;

        try {
            ITunesBackup backup = new ITunesBackup(source.getParentFile());
            this.loadBackup(backup);
            this.selectBackup(backup);
        } catch (FileNotFoundException e) {
            Dialogs.showAlert(Alert.AlertType.ERROR, "The following file was not found: " + e.getMessage());
        } catch (BackupReadException e) {
            Dialogs.showAlert(Alert.AlertType.ERROR, e.getMessage());
        }
    }

    @FXML
    public void reloadBackupsAction() {
        this.loadBackups();
    }

    @FXML
    public void quit() {
        this.cleanUp();
        Platform.exit();
    }

    @FXML
    public void helpAbout() {
        Alert about = new Alert(Alert.AlertType.INFORMATION);
        about.setTitle("About iTunes Backup Explorer");
        about.setHeaderText("iTunes Backup Explorer v2.1");
        about.setContentText(
                "Originally developed by MaxiHuHe04\n" +
                        "Enhanced by fredac100\n\n" +
                        "License: MIT\n\n" +
                        "https://github.com/MaxiHuHe04/iTunes-Backup-Explorer\n" +
                        "https://github.com/fredac100"
        );

        DialogPane dialogPane = about.getDialogPane();
        dialogPane.setMinWidth(480);
        dialogPane.getStylesheets().add(
                ITunesBackupExplorer.class.getResource("stylesheet.css").toExternalForm()
        );
        String theme = "Light".equalsIgnoreCase(PreferencesController.getTheme()) ? "theme-light" : "theme-dark";
        dialogPane.getStyleClass().add(theme);

        ((Stage) dialogPane.getScene().getWindow()).getIcons().add(ITunesBackupExplorer.APP_ICON);
        about.showAndWait();
    }

    private void updateStatusBar() {
        if (selectedBackup == null) {
            statusTotalFiles.setText("Total files: --");
            statusBackupSize.setText("Size: --");
            statusEncryption.setText("Encryption: --");
            statusBackupPath.setText("Backup: --");
            return;
        }

        String encryptionStatus = selectedBackup.manifest.encrypted ?
                (selectedBackup.isLocked() ? "Encrypted (locked)" : "Encrypted (unlocked)") : "Not encrypted";
        statusEncryption.setText("Encryption: " + encryptionStatus);
        statusBackupPath.setText("Backup: " + selectedBackup.directory.getAbsolutePath());

        if (selectedBackup.isLocked()) {
            statusTotalFiles.setText("Total files: --");
            statusBackupSize.setText("Size: --");
            return;
        }

        ITunesBackup backup = selectedBackup;
        javafx.concurrent.Task<long[]> task = new javafx.concurrent.Task<>() {
            @Override
            protected long[] call() throws Exception {
                return backup.queryFileStats();
            }
        };

        task.setOnSucceeded(event -> {
            long[] result = task.getValue();
            Platform.runLater(() -> {
                statusTotalFiles.setText("Total files: " + result[0]);
                if (result[1] >= 0) {
                    statusBackupSize.setText("Size: " + FileSize.format(result[1]));
                } else {
                    statusBackupSize.setText("Size: --");
                }
            });
        });

        task.setOnFailed(event -> Platform.runLater(() -> {
            statusTotalFiles.setText("Total files: --");
            statusBackupSize.setText("Size: --");
        }));

        new Thread(task).start();
    }
}
