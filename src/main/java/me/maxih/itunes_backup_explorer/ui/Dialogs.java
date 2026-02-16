package me.maxih.itunes_backup_explorer.ui;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import me.maxih.itunes_backup_explorer.ITunesBackupExplorer;

import java.util.Optional;

public class Dialogs {

    public static Optional<char[]> askPassword() {
        Dialog<char[]> dialog = new Dialog<>();
        dialog.setTitle("Enter the password");
        dialog.setHeaderText("This backup is encrypted with a password");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ((Stage) dialog.getDialogPane().getScene().getWindow()).getIcons().add(ITunesBackupExplorer.APP_ICON);

        PasswordField passwordField = new PasswordField();
        dialog.setOnShown(event -> Platform.runLater(passwordField::requestFocus));

        HBox content = new HBox();
        content.setAlignment(Pos.CENTER_LEFT);
        content.setSpacing(10);
        content.getChildren().addAll(new Label("Please type in your password here:"), passwordField);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(pressedButton ->
                pressedButton == ButtonType.OK ? passwordField.getText().toCharArray() : null);

        return dialog.showAndWait();
    }

    public static Alert getAlert(Alert.AlertType type, String message, ButtonType... buttonTypes) {
        Alert alert = new Alert(type, message, buttonTypes);
        ((Stage) alert.getDialogPane().getScene().getWindow()).getIcons().add(ITunesBackupExplorer.APP_ICON);
        return alert;
    }

    public static Optional<ButtonType> showAlert(Alert.AlertType type, String message, ButtonType... buttonTypes) {
        return getAlert(type, message, buttonTypes).showAndWait();
    }

    public static class ProgressAlert extends Stage {
        private Label messageLabel;

        public ProgressAlert(String title, Task<?> task, EventHandler<WindowEvent> cancelEventHandler) {
            this.initModality(Modality.APPLICATION_MODAL);
            this.setTitle(title);
            this.setResizable(false);
            this.setOnCloseRequest(cancelEventHandler);
            this.getIcons().add(ITunesBackupExplorer.APP_ICON);

            ProgressBar bar = new ProgressBar();
            bar.setPrefWidth(250);
            bar.setPadding(new Insets(10));
            bar.progressProperty().bind(task.progressProperty());

            messageLabel = new Label();
            messageLabel.setPadding(new Insets(5, 10, 5, 10));
            messageLabel.textProperty().bind(task.messageProperty());

            VBox container = new VBox(messageLabel, bar);
            container.setAlignment(Pos.CENTER);
            container.setPadding(new Insets(10));

            task.runningProperty().addListener((observable, oldValue, newValue) -> {
                if (oldValue && !newValue) this.close();
            });

            this.setScene(new Scene(container));
        }
        public ProgressAlert(String title, Task<?> task, boolean cancellable) {
            this(title, task, cancellable ? event -> task.cancel() : Event::consume);
        }

        public ProgressAlert(String title, Task<?> task, Runnable cancelAction) {
            this(title, task, event -> cancelAction.run());
        }

    }

    private Dialogs() {
    }

}
