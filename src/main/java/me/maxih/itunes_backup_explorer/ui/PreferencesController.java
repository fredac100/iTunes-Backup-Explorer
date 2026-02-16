package me.maxih.itunes_backup_explorer.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import me.maxih.itunes_backup_explorer.ITunesBackupExplorer;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PreferencesController {
    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(ITunesBackupExplorer.class);

    private static final String DEFAULT_ROOTS = Stream.of(
            Paths.get(System.getProperty("user.home"), "AppData\\Roaming\\Apple Computer\\MobileSync\\Backup"),
            Paths.get(System.getProperty("user.home"), "Apple\\MobileSync\\Backup"),
            Paths.get(System.getProperty("user.home"), "Library/Application Support/MobileSync/Backup")
    ).filter(Files::exists).map(Path::toString).collect(Collectors.joining("\n"));

    private static final String APP_VERSION = "v2.0";

    public static String[] getBackupRoots() {
        String roots = PREFERENCES.get("BackupRoots", DEFAULT_ROOTS);
        return roots.isEmpty() ? new String[0] : roots.split("\\n");
    }

    public static boolean getPreserveTimestamps() {
        return PREFERENCES.getBoolean("PreserveTimestamps", true);
    }

    public static boolean getCreateDirectoryStructure() {
        return PREFERENCES.getBoolean("CreateDirectoryStructure", true);
    }

    public static boolean getSkipExistingFiles() {
        return PREFERENCES.getBoolean("SkipExistingFiles", false);
    }

    public Runnable reloadCallback;

    @FXML
    public ListView<String> backupRootsListView;

    @FXML
    public ComboBox<String> themeComboBox;

    @FXML
    public CheckBox preserveTimestampsCheckBox;

    @FXML
    public CheckBox createDirectoryStructureCheckBox;

    @FXML
    public CheckBox skipExistingFilesCheckBox;

    @FXML
    public Label versionLabel;

    @FXML
    public Label javaVersionLabel;

    @FXML
    public Label platformLabel;

    private ObservableList<String> backupRootsList;

    @FXML
    public void initialize() {
        backupRootsList = FXCollections.observableArrayList();
        String roots = PREFERENCES.get("BackupRoots", DEFAULT_ROOTS);
        if (!roots.isEmpty()) {
            String[] rootsArray = roots.split("\\n");
            for (String root : rootsArray) {
                if (!root.trim().isEmpty()) {
                    backupRootsList.add(root.trim());
                }
            }
        }
        backupRootsListView.setItems(backupRootsList);

        themeComboBox.setItems(FXCollections.observableArrayList("Dark", "Light"));
        themeComboBox.getSelectionModel().select("Dark");
        themeComboBox.setDisable(true);

        preserveTimestampsCheckBox.setSelected(PREFERENCES.getBoolean("PreserveTimestamps", true));
        createDirectoryStructureCheckBox.setSelected(PREFERENCES.getBoolean("CreateDirectoryStructure", true));
        skipExistingFilesCheckBox.setSelected(PREFERENCES.getBoolean("SkipExistingFiles", false));

        versionLabel.setText(APP_VERSION);
        javaVersionLabel.setText(System.getProperty("java.version"));
        platformLabel.setText(System.getProperty("os.name") + " " + System.getProperty("os.arch"));
    }

    @FXML
    public void addDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Backup Directory");
        File selected = chooser.showDialog(backupRootsListView.getScene().getWindow());
        if (selected != null && selected.isDirectory()) {
            String path = selected.getAbsolutePath();
            if (!backupRootsList.contains(path)) {
                backupRootsList.add(path);
            }
        }
    }

    @FXML
    public void removeDirectory() {
        String selected = backupRootsListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            backupRootsList.remove(selected);
        }
    }

    @FXML
    public void save() {
        String currentRoots = PREFERENCES.get("BackupRoots", DEFAULT_ROOTS);
        String newRoots = String.join("\n", backupRootsList);

        if (!newRoots.equals(currentRoots)) {
            PREFERENCES.put("BackupRoots", newRoots);
            if (this.reloadCallback != null) this.reloadCallback.run();
        }

        PREFERENCES.putBoolean("PreserveTimestamps", preserveTimestampsCheckBox.isSelected());
        PREFERENCES.putBoolean("CreateDirectoryStructure", createDirectoryStructureCheckBox.isSelected());
        PREFERENCES.putBoolean("SkipExistingFiles", skipExistingFilesCheckBox.isSelected());

        ((Stage) backupRootsListView.getScene().getWindow()).close();
    }

    @FXML
    public void cancel() {
        ((Stage) backupRootsListView.getScene().getWindow()).close();
    }

    @FXML
    public void resetToDefaults() {
        String currentRoots = PREFERENCES.get("BackupRoots", DEFAULT_ROOTS);
        boolean currentPreserveTimestamps = PREFERENCES.getBoolean("PreserveTimestamps", true);
        boolean currentCreateDirectoryStructure = PREFERENCES.getBoolean("CreateDirectoryStructure", true);
        boolean currentSkipExistingFiles = PREFERENCES.getBoolean("SkipExistingFiles", false);

        boolean needsReload = false;

        if (!currentRoots.equals(DEFAULT_ROOTS)) {
            PREFERENCES.remove("BackupRoots");
            needsReload = true;
        }

        if (!currentPreserveTimestamps) {
            PREFERENCES.remove("PreserveTimestamps");
        }

        if (!currentCreateDirectoryStructure) {
            PREFERENCES.remove("CreateDirectoryStructure");
        }

        if (currentSkipExistingFiles) {
            PREFERENCES.remove("SkipExistingFiles");
        }

        if (needsReload && this.reloadCallback != null) {
            this.reloadCallback.run();
        }

        ((Stage) backupRootsListView.getScene().getWindow()).close();
    }
}
