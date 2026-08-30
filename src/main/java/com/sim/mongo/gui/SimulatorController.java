package com.sim.mongo.gui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SimulatorController {

    // Shards tab
    @FXML private ListView<ShardConfig> shardsListView;
    @FXML private Button addShardButton;
    @FXML private ListView<CollectionConfig> collectionsListView;
    @FXML private TextField newCollectionNameField;
    @FXML private TextField newCollectionSizeField;
    @FXML private Button addCollectionButton;

    // Sources tab
    @FXML private ListView<SourceConfig> sourcesListView;
    @FXML private Button addSourceButton;
    @FXML private ChoiceBox<String> assignShardChoiceBox;
    @FXML private TextField clientToNodeTravelTimeField;
    @FXML private Button applySourceSettingsButton;
    @FXML private ListView<OperationConfig> operationsListView;
    @FXML private Button addOperationButton;
    @FXML private Button removeOperationButton;

    private final ObservableList<ShardConfig> shards = FXCollections.observableArrayList();
    private final ObservableList<SourceConfig> sources = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // shards
        shardsListView.setItems(shards);
        shardsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> onShardSelected(newV));
        addShardButton.setOnAction(e -> onAddShard());

        // collections
        collectionsListView.setItems(FXCollections.observableArrayList());
        addCollectionButton.setOnAction(e -> onAddCollection());

        // sources
        sourcesListView.setItems(sources);
        sourcesListView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> onSourceSelected(newV));
        addSourceButton.setOnAction(e -> onAddSource());

        // source controls
        assignShardChoiceBox.setItems(FXCollections.observableArrayList());
        applySourceSettingsButton.setOnAction(e -> onApplySourceSettings());

        // operations
        operationsListView.setItems(FXCollections.observableArrayList());
        addOperationButton.setOnAction(e -> onAddOperation());
        removeOperationButton.setOnAction(e -> onRemoveOperation());

        // sample: populate with one shard/source for convenience
        // (can be removed)
        //shards.add(new ShardConfig("shard-0"));
        //sources.add(new SourceConfig("client-0"));

        refreshAssignedShardChoices();
    }

    // --- Shards logic ---
    private void onAddShard(){
        TextInputDialog d = new TextInputDialog();
        d.setTitle("Add Shard");
        d.setHeaderText("Enter shard name");
        d.setContentText("Name:");
        Optional<String> r = d.showAndWait();
        r.ifPresent(name -> {
            String n = name.trim();
            if (!n.isEmpty()){
                ShardConfig s = new ShardConfig(n);
                shards.add(s);
                refreshAssignedShardChoices();
            }
        });
    }

    private void onShardSelected(ShardConfig shard){
        if (shard == null){
            collectionsListView.setItems(FXCollections.observableArrayList());
            return;
        }
        collectionsListView.setItems(FXCollections.observableArrayList(shard.getCollections()));
    }

    private void onAddCollection(){
        ShardConfig shard = shardsListView.getSelectionModel().getSelectedItem();
        if (shard == null){ showAlert("Select a shard first"); return; }
        String name = newCollectionNameField.getText();
        String sizeText = newCollectionSizeField.getText();
        if (name == null || name.isBlank()){ showAlert("Collection name required"); return; }
        int size = 0;
        try{ size = Integer.parseInt(sizeText); }catch(Exception ex){ showAlert("Invalid size (integer required)"); return; }
        CollectionConfig c = new CollectionConfig(name.trim(), size);
        shard.getCollections().add(c);
        collectionsListView.setItems(FXCollections.observableArrayList(shard.getCollections()));
        newCollectionNameField.clear(); newCollectionSizeField.clear();
    }

    // --- Sources logic ---
    private void onAddSource(){
        TextInputDialog d = new TextInputDialog();
        d.setTitle("Add Source");
        d.setHeaderText("Enter source (client) name");
        d.setContentText("Name:");
        Optional<String> r = d.showAndWait();
        r.ifPresent(name -> {
            String n = name.trim();
            if (!n.isEmpty()){
                SourceConfig s = new SourceConfig(n);
                sources.add(s);
            }
        });
    }

    private void onSourceSelected(SourceConfig src){
        if (src == null){
            assignShardChoiceBox.getSelectionModel().clearSelection();
            clientToNodeTravelTimeField.clear();
            operationsListView.setItems(FXCollections.observableArrayList());
            return;
        }
        // populate assigned shard choice box
        refreshAssignedShardChoices();
        if (src.getAssignedShard() != null) assignShardChoiceBox.setValue(src.getAssignedShard());
        clientToNodeTravelTimeField.setText(src.getClientToNodeTravelTime() == null ? "" : String.valueOf(src.getClientToNodeTravelTime()));
        operationsListView.setItems(FXCollections.observableArrayList(src.getOperations()));
    }

    private void onApplySourceSettings(){
        SourceConfig src = sourcesListView.getSelectionModel().getSelectedItem();
        if (src == null){ showAlert("Select a source first"); return; }
        String shardName = assignShardChoiceBox.getValue();
        src.setAssignedShard(shardName);
        // clientToNodeTravelTime
        String t = clientToNodeTravelTimeField.getText();
        if (t != null && !t.isBlank()){
            try{
                double val = Double.parseDouble(t);
                src.setClientToNodeTravelTime(val);
            }catch(NumberFormatException ex){ showAlert("Invalid travel time (number required)"); return; }
        }
        // refresh lists
        sourcesListView.refresh();
    }

    private void onAddOperation(){
        SourceConfig src = sourcesListView.getSelectionModel().getSelectedItem();
        if (src == null){ showAlert("Select a source first"); return; }
        // Prompt sequence for operation fields
        TextInputDialog idDialog = new TextInputDialog();
        idDialog.setTitle("Add Operation");
        idDialog.setHeaderText("Operation id/name");
        idDialog.setContentText("id:");
        Optional<String> idOpt = idDialog.showAndWait();
        if (idOpt.isEmpty()) return;
        String id = idOpt.get().trim();
        if (id.isEmpty()){ showAlert("Operation id required"); return; }

        ChoiceDialog<String> typeDialog = new ChoiceDialog<>("READ", "READ", "WRITE");
        typeDialog.setTitle("Operation Type");
        typeDialog.setHeaderText("Select operation type");
        Optional<String> typeOpt = typeDialog.showAndWait();
        if (typeOpt.isEmpty()) return;
        String type = typeOpt.get();

        TextInputDialog opsPerSecDialog = new TextInputDialog("1");
        opsPerSecDialog.setTitle("Operations per second");
        opsPerSecDialog.setHeaderText("Mean operations per second");
        opsPerSecDialog.setContentText("ops/sec:");
        Optional<String> opsOpt = opsPerSecDialog.showAndWait();
        if (opsOpt.isEmpty()) return;
        double ops = 1.0; try{ ops = Double.parseDouble(opsOpt.get()); }catch(Exception ex){ showAlert("Invalid ops/sec"); return; }

        TextInputDialog affectedDocsDialog = new TextInputDialog("1");
        affectedDocsDialog.setTitle("Affected docs");
        affectedDocsDialog.setHeaderText("Affected document count (mean)");
        affectedDocsDialog.setContentText("docs:");
        Optional<String> docsOpt = affectedDocsDialog.showAndWait();
        if (docsOpt.isEmpty()) return;
        int docs = 1; try{ docs = Integer.parseInt(docsOpt.get()); }catch(Exception ex){ showAlert("Invalid docs number"); return; }

        TextInputDialog baseExecDialog = new TextInputDialog("1");
        baseExecDialog.setTitle("Base execution time (ms)");
        baseExecDialog.setHeaderText("Base execution time in ms (mean)");
        baseExecDialog.setContentText("ms:");
        Optional<String> baseOpt = baseExecDialog.showAndWait();
        if (baseOpt.isEmpty()) return;
        double baseMs = 1.0; try{ baseMs = Double.parseDouble(baseOpt.get()); }catch(Exception ex){ showAlert("Invalid base exec time"); return; }

        OperationConfig oc = new OperationConfig(id, type, ops, docs, baseMs);
        src.getOperations().add(oc);
        operationsListView.setItems(FXCollections.observableArrayList(src.getOperations()));
    }

    private void onRemoveOperation(){
        SourceConfig src = sourcesListView.getSelectionModel().getSelectedItem();
        OperationConfig op = operationsListView.getSelectionModel().getSelectedItem();
        if (src == null || op == null){ showAlert("Select source and operation"); return; }
        src.getOperations().remove(op);
        operationsListView.setItems(FXCollections.observableArrayList(src.getOperations()));
    }

    private void refreshAssignedShardChoices(){
        List<String> names = new ArrayList<>();
        for (ShardConfig s : shards) names.add(s.getName());
        Platform.runLater(() -> {
            assignShardChoiceBox.setItems(FXCollections.observableArrayList(names));
        });
    }

    private void showAlert(String msg){
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.showAndWait();
    }

    // --- Simple configuration POJOs used only by the GUI ---
    public static class ShardConfig {
        private final String name;
        private final List<CollectionConfig> collections = new ArrayList<>();
        public ShardConfig(String name){ this.name = name; }
        public String getName(){ return name; }
        public List<CollectionConfig> getCollections(){ return collections; }
        @Override public String toString(){ return name; }
    }

    public static class CollectionConfig {
        private final String name;
        private final int size;
        public CollectionConfig(String name, int size){ this.name = name; this.size = size; }
        public String getName(){ return name; }
        public int getSize(){ return size; }
        @Override public String toString(){ return name + " (" + size + " docs)"; }
    }

    public static class SourceConfig {
        private final String name;
        private String assignedShard;
        private Double clientToNodeTravelTime;
        private final List<OperationConfig> operations = new ArrayList<>();
        public SourceConfig(String name){ this.name = name; }
        public String getName(){ return name; }
        public String getAssignedShard(){ return assignedShard; }
        public void setAssignedShard(String s){ this.assignedShard = s; }
        public Double getClientToNodeTravelTime(){ return clientToNodeTravelTime; }
        public void setClientToNodeTravelTime(Double v){ this.clientToNodeTravelTime = v; }
        public List<OperationConfig> getOperations(){ return operations; }
        @Override public String toString(){ return name + (assignedShard == null ? "" : " -> " + assignedShard); }
    }

    public static class OperationConfig {
        private final String id;
        private final String type;
        private final double opsPerSec;
        private final int affectedDocs;
        private final double baseExecMs;
        public OperationConfig(String id, String type, double opsPerSec, int affectedDocs, double baseExecMs){
            this.id = id; this.type = type; this.opsPerSec = opsPerSec; this.affectedDocs = affectedDocs; this.baseExecMs = baseExecMs;
        }
        @Override public String toString(){ return id + " [" + type + "] ops/s=" + opsPerSec + " docs=" + affectedDocs + " baseMs=" + baseExecMs; }
    }
}
