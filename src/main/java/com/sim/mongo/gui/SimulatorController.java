package com.sim.mongo.gui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SimulatorController {

    @FXML private ListView<ShardConfig> shardsListView;
    @FXML private ListView<SourceConfig> sourcesListView;

    @FXML private TextField newShardNameField;
    @FXML private TextField newSourceNameField;
    @FXML private ChoiceBox<String> distributionChoiceBox;
    @FXML private TextField distributionParamField;

    @FXML private Button addShardButton;
    @FXML private Button addSourceButton;
    @FXML private Button assignShardButton;
    @FXML private Button saveButton;

    private final ObservableList<ShardConfig> shards = FXCollections.observableArrayList();
    private final ObservableList<SourceConfig> sources = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        shardsListView.setItems(shards);
        sourcesListView.setItems(sources);

        distributionChoiceBox.getItems().addAll("Constant","Uniform","Exponential");
        distributionChoiceBox.setValue("Constant");

        addShardButton.setOnAction(e -> addShard());
        addSourceButton.setOnAction(e -> addSource());
        assignShardButton.setOnAction(e -> assignShardToSelectedSource());
        saveButton.setOnAction(e -> saveConfigToFile());

        shardsListView.setCellFactory(lv -> new ListCell<>(){
            @Override
            protected void updateItem(ShardConfig item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });

        sourcesListView.setCellFactory(lv -> new ListCell<>(){
            @Override
            protected void updateItem(SourceConfig item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDisplay());
            }
        });
    }

    private void addShard() {
        String name = newShardNameField.getText();
        if (name == null || name.isBlank()) {
            showAlert("Shard name required");
            return;
        }
        shards.add(new ShardConfig(name.trim()));
        newShardNameField.clear();
    }

    private void addSource() {
        String name = newSourceNameField.getText();
        if (name == null || name.isBlank()) {
            showAlert("Source name required");
            return;
        }
        // attach default distribution from controls
        DistributionConfig dist = new DistributionConfig(distributionChoiceBox.getValue(), distributionParamField.getText());
        sources.add(new SourceConfig(name.trim(), dist));
        newSourceNameField.clear();
    }

    private void assignShardToSelectedSource() {
        SourceConfig src = sourcesListView.getSelectionModel().getSelectedItem();
        ShardConfig shard = shardsListView.getSelectionModel().getSelectedItem();
        if (src == null || shard == null) {
            showAlert("Select both a source and a shard to assign");
            return;
        }
        src.setAssignedShard(shard.getName());
        sourcesListView.refresh();
    }

    private void saveConfigToFile() {
        FileChooser chooser = new FileChooser();
        chooser.setInitialFileName("simulation-config.txt");
        Path path = chooser.showSaveDialog(saveButton.getScene().getWindow()) == null ? null : chooser.showSaveDialog(saveButton.getScene().getWindow()).toPath();
        if (path == null) return;

        List<String> lines = new ArrayList<>();
        lines.add("shards:");
        for (ShardConfig s : shards) lines.add("  - " + s.getName());
        lines.add("");
        lines.add("sources:");
        for (SourceConfig s : sources) {
            lines.add("  - name: " + s.getName());
            lines.add("    assignedShard: " + (s.getAssignedShard() == null ? "" : s.getAssignedShard()));
            lines.add("    distribution: " + s.getDistribution().getType() + "(" + s.getDistribution().getParam() + ")");
        }

        try {
            Files.write(path, lines, StandardCharsets.UTF_8);
            showAlert("Saved configuration to " + path.toString());
        } catch (IOException e) {
            showAlert("Failed to save file: " + e.getMessage());
        }
    }

    private void showAlert(String msg){
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.showAndWait();
    }

    // --- Simple configuration POJOs used only by the GUI ---
    public static class ShardConfig {
        private final String name;
        public ShardConfig(String name){ this.name = name; }
        public String getName(){ return name; }
        @Override public String toString(){ return name; }
    }

    public static class SourceConfig {
        private final String name;
        private final DistributionConfig distribution;
        private String assignedShard;
        public SourceConfig(String name, DistributionConfig distribution){ this.name = name; this.distribution = distribution; }
        public String getName(){ return name; }
        public DistributionConfig getDistribution(){ return distribution; }
        public void setAssignedShard(String s){ this.assignedShard = s; }
        public String getAssignedShard(){ return assignedShard; }
        public String getDisplay(){ return name + (assignedShard == null ? "" : " -> " + assignedShard); }
    }

    public static class DistributionConfig {
        private final String type;
        private final String param;
        public DistributionConfig(String type, String param){ this.type = type; this.param = param; }
        public String getType(){ return type; }
        public String getParam(){ return param; }
    }
}
