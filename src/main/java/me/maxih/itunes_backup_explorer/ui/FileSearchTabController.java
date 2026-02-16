package me.maxih.itunes_backup_explorer.ui;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.DirectoryChooser;
import me.maxih.itunes_backup_explorer.api.*;
import me.maxih.itunes_backup_explorer.util.FileSize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.util.*;
import java.util.stream.Collectors;

public class FileSearchTabController {
    private static final Logger logger = LoggerFactory.getLogger(FileSearchTabController.class);

    ITunesBackup selectedBackup;

    @FXML ComboBox<String> domainComboBox;
    @FXML ComboBox<String> fileTypeComboBox;
    @FXML TextField relativePathQueryField;
    @FXML TableView<BackupFileEntry> filesTable;
    @FXML Label searchResultsCount;

    @FXML
    public void initialize() {
        setupFileTypeComboBox();

        TableColumn<BackupFileEntry, String> domainColumn = new TableColumn<>("Domain");
        TableColumn<BackupFileEntry, String> nameColumn = new TableColumn<>("Name");
        TableColumn<BackupFileEntry, String> typeColumn = new TableColumn<>("Type");
        TableColumn<BackupFileEntry, String> pathColumn = new TableColumn<>("Path");
        TableColumn<BackupFileEntry, String> sizeColumn = new TableColumn<>("Size");

        domainColumn.setCellValueFactory(new PropertyValueFactory<>("domain"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        pathColumn.setCellValueFactory(new PropertyValueFactory<>("parentPath"));

        typeColumn.setCellValueFactory(cellData -> {
            String fileName = cellData.getValue().getFileName();
            int dotIndex = fileName.lastIndexOf('.');
            String extension = dotIndex > 0 ? fileName.substring(dotIndex + 1).toUpperCase() : "";
            return new javafx.beans.property.SimpleStringProperty(extension);
        });

        sizeColumn.setCellValueFactory(cellData -> {
            long size = cellData.getValue().getSize();
            return new javafx.beans.property.SimpleStringProperty(FileSize.format(size));
        });

        domainColumn.prefWidthProperty().bind(this.filesTable.widthProperty().multiply(0.2));
        nameColumn.prefWidthProperty().bind(this.filesTable.widthProperty().multiply(0.25));
        typeColumn.prefWidthProperty().bind(this.filesTable.widthProperty().multiply(0.08));
        pathColumn.prefWidthProperty().bind(this.filesTable.widthProperty().multiply(0.35));
        sizeColumn.prefWidthProperty().bind(this.filesTable.widthProperty().multiply(0.12));

        this.filesTable.getColumns().addAll(Arrays.asList(domainColumn, nameColumn, typeColumn, pathColumn, sizeColumn));

        this.filesTable.setRowFactory(tableView -> {
            TableRow<BackupFileEntry> row = new TableRow<>();

            row.itemProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue == null || newValue.getFile().isEmpty()) return;
                row.setContextMenu(FileActions.getContextMenu(
                        newValue.getFile().get(),
                        tableView.getScene().getWindow(),
                        removedIDs -> filesTable.getItems().removeIf(entry ->
                                entry.getFile().map(f -> removedIDs.contains(f.fileID)).orElse(false)
                        ))
                );
            });

            return row;
        });

        domainComboBox.setOnAction(event -> searchFiles());
        relativePathQueryField.setOnAction(event -> searchFiles());
        fileTypeComboBox.setOnAction(event -> searchFiles());
    }

    private void setupFileTypeComboBox() {
        fileTypeComboBox.setItems(FXCollections.observableArrayList(
            "All Types",
            "Images (jpg, png, heic)",
            "Videos (mov, mp4)",
            "Databases (db, sqlite)",
            "Plists (plist)",
            "Text (txt, csv, json)"
        ));
        fileTypeComboBox.setValue("All Types");
    }

    @FXML
    public void searchFiles() {
        filesTable.getItems().clear();

        try {
            String domainQuery = getDomainQuery();
            String pathQuery = relativePathQueryField.getText();

            if (pathQuery.isEmpty()) pathQuery = "%";

            List<BackupFile> searchResult = selectedBackup.searchFiles(domainQuery, pathQuery);

            String selectedFileType = fileTypeComboBox.getValue();
            if (selectedFileType != null && !selectedFileType.equals("All Types")) {
                searchResult = filterByFileType(searchResult, selectedFileType);
            }

            if (!PreferencesController.getSearchIncludeNonFiles()) {
                searchResult = searchResult.stream()
                        .filter(file -> file.getFileType() == BackupFile.FileType.FILE)
                        .collect(Collectors.toList());
            }

            int totalResults = searchResult.size();
            int resultLimit = PreferencesController.getSearchResultLimit();
            boolean limited = resultLimit > 0 && totalResults > resultLimit;
            if (limited) {
                searchResult = searchResult.subList(0, resultLimit);
            }

            this.filesTable.setItems(FXCollections.observableList(searchResult.stream().map(BackupFileEntry::new).collect(Collectors.toList())));

            updateResultsCount(searchResult.size(), totalResults);
        } catch (DatabaseConnectionException e) {
            logger.error("Falha ao buscar arquivos", e);
            Dialogs.showAlert(Alert.AlertType.ERROR, e.getMessage());
        }
    }

    private String getDomainQuery() {
        String domain = domainComboBox.getEditor().getText();
        if (domain == null || domain.isEmpty() || domain.equals("All Domains")) {
            return "%";
        }
        return domain;
    }

    private List<BackupFile> filterByFileType(List<BackupFile> files, String fileType) {
        Set<String> extensions = new HashSet<>();

        switch (fileType) {
            case "Images (jpg, png, heic)":
                extensions.addAll(Arrays.asList("jpg", "jpeg", "png", "heic", "heif"));
                break;
            case "Videos (mov, mp4)":
                extensions.addAll(Arrays.asList("mov", "mp4", "m4v"));
                break;
            case "Databases (db, sqlite)":
                extensions.addAll(Arrays.asList("db", "sqlite", "sqlite3"));
                break;
            case "Plists (plist)":
                extensions.add("plist");
                break;
            case "Text (txt, csv, json)":
                extensions.addAll(Arrays.asList("txt", "csv", "json", "log"));
                break;
        }

        return files.stream()
            .filter(file -> {
                String fileName = file.getFileName().toLowerCase();
                int dotIndex = fileName.lastIndexOf('.');
                if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
                    String ext = fileName.substring(dotIndex + 1);
                    return extensions.contains(ext);
                }
                return false;
            })
            .collect(Collectors.toList());
    }

    private void updateResultsCount(int shownCount, int totalCount) {
        if (searchResultsCount != null) {
            if (shownCount != totalCount) {
                searchResultsCount.setText(shownCount + "/" + totalCount + " results (limited)");
            } else {
                searchResultsCount.setText(shownCount + " result" + (shownCount != 1 ? "s" : ""));
            }
        }
    }

    @FXML
    public void exportMatching() {
        if (this.filesTable.getItems().size() == 0) return;

        DirectoryChooser chooser = new DirectoryChooser();
        File lastDirectory = PreferencesController.getLastExportDirectory();
        if (lastDirectory != null) chooser.setInitialDirectory(lastDirectory);
        File destination = chooser.showDialog(this.filesTable.getScene().getWindow());

        if (destination == null || !destination.exists()) return;
        PreferencesController.setLastExportDirectory(destination);

        boolean withRelativePath = PreferencesController.getCreateDirectoryStructure();
        boolean preserveTimestamps = PreferencesController.getPreserveTimestamps();
        boolean skipExisting = PreferencesController.getSkipExistingFiles();
        this.filesTable.getItems().forEach(backupFile -> {
            if (backupFile.getFile().isEmpty()) return;
            try {
                backupFile.getFile().get().extractToFolder(destination, withRelativePath, preserveTimestamps);
            } catch (FileAlreadyExistsException e) {
                if (!skipExisting) Dialogs.showAlert(Alert.AlertType.ERROR, e.getMessage(), ButtonType.OK);
            } catch (IOException | BackupReadException | NotUnlockedException | UnsupportedCryptoException e) {
                logger.error("Falha ao exportar arquivo correspondente", e);
                Dialogs.showAlert(Alert.AlertType.ERROR, e.getMessage(), ButtonType.OK);
            }
        });
    }

    @FXML
    public void quickFilterAllFiles() {
        setQuickFilter("%", "%");
    }

    @FXML
    public void quickFilterPhotos() {
        performMultiPatternSearch("CameraRollDomain", Arrays.asList("%.HEIC", "%.heic", "%.JPG", "%.jpg", "%.JPEG", "%.jpeg", "%.PNG", "%.png"));
    }

    @FXML
    public void quickFilterVideos() {
        performMultiPatternSearch("CameraRollDomain", Arrays.asList("%.MOV", "%.mov", "%.MP4", "%.mp4", "%.M4V", "%.m4v"));
    }

    @FXML
    public void quickFilterWhatsApp() {
        setQuickFilter("AppDomainGroup-group.net.whatsapp.WhatsApp.shared", "%");
    }

    @FXML
    public void quickFilterContacts() {
        setQuickFilter("HomeDomain", "%AddressBook%");
    }

    @FXML
    public void quickFilterMessages() {
        setQuickFilter("HomeDomain", "%sms%");
    }

    @FXML
    public void quickFilterNotes() {
        setQuickFilter("AppDomainGroup-group.com.apple.notes", "%");
    }

    @FXML
    public void quickFilterVoiceMemos() {
        setQuickFilter("MediaDomain", "%Recording%");
    }

    private void setQuickFilter(String domain, String path) {
        domainComboBox.setValue(domain);
        relativePathQueryField.setText(path.equals("%") ? "" : path);
        fileTypeComboBox.setValue("All Types");
        searchFiles();
    }

    private void performMultiPatternSearch(String domain, List<String> pathPatterns) {
        filesTable.getItems().clear();

        try {
            List<BackupFile> allResults = new ArrayList<>();

            for (String pattern : pathPatterns) {
                List<BackupFile> results = selectedBackup.searchFiles(domain, pattern);
                allResults.addAll(results);
            }

            Set<String> seenIds = new HashSet<>();
            List<BackupFile> uniqueResults = allResults.stream()
                .filter(file -> seenIds.add(file.fileID))
                .collect(Collectors.toList());

            if (!PreferencesController.getSearchIncludeNonFiles()) {
                uniqueResults = uniqueResults.stream()
                        .filter(file -> file.getFileType() == BackupFile.FileType.FILE)
                        .collect(Collectors.toList());
            }

            int totalResults = uniqueResults.size();
            int resultLimit = PreferencesController.getSearchResultLimit();
            if (resultLimit > 0 && uniqueResults.size() > resultLimit) {
                uniqueResults = uniqueResults.subList(0, resultLimit);
            }

            this.filesTable.setItems(FXCollections.observableList(uniqueResults.stream().map(BackupFileEntry::new).collect(Collectors.toList())));

            updateResultsCount(uniqueResults.size(), totalResults);

            domainComboBox.setValue(domain);
            relativePathQueryField.setText("");
            fileTypeComboBox.setValue("All Types");
        } catch (DatabaseConnectionException e) {
            logger.error("Falha ao buscar arquivos", e);
            Dialogs.showAlert(Alert.AlertType.ERROR, e.getMessage());
        }
    }

    public void tabShown(ITunesBackup backup) {
        if (backup == this.selectedBackup && this.filesTable.getItems() != null) return;

        this.filesTable.setItems(null);
        this.selectedBackup = backup;
        updateResultsCount(0, 0);

        populateDomainComboBox();
    }

    private void populateDomainComboBox() {
        try {
            List<BackupFile> domainRoots = selectedBackup.queryDomainRoots();

            List<String> domains = domainRoots.stream()
                .map(file -> file.domain)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

            domains.add(0, "All Domains");

            domainComboBox.setItems(FXCollections.observableArrayList(domains));
            domainComboBox.setValue("All Domains");
        } catch (DatabaseConnectionException e) {
            logger.error("Falha ao carregar domínios", e);
            domainComboBox.setItems(FXCollections.observableArrayList("All Domains"));
            domainComboBox.setValue("All Domains");
        }
    }
}
