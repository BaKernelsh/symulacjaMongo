package com.sim.mongo.gui;

import com.sim.mongo.Simulation;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.ja.OperationTypeEnum;
import org.ja.Shard;
import org.ja.statistics.Statistics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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

    //run tab
    @FXML private TextField simulationTimeField;
    @FXML private Button startSimulationButton;
    @FXML private Button cancelSimulationButton;
    @FXML private TextArea simulationLogArea;

    // Statistics tab
    @FXML private ListView<String> operationTypesListView;
    @FXML private TextArea statisticsTextArea;
    @FXML private Button refreshStatsButton;

    // background thread for simulation
    private Thread simulationThread;
    
    // Statistics reference
    private final Statistics statistics = Statistics.instance();

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

        startSimulationButton.setOnAction(e -> startSimulation());
        cancelSimulationButton.setOnAction(e -> cancelSimulation());

        // Statistics tab initialization
        operationTypesListView.setItems(FXCollections.observableArrayList());
        operationTypesListView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> onOperationIDSelected(newV));
        refreshStatsButton.setOnAction(e -> refreshStatistics());

        refreshAssignedShardChoices();
    }

    private void appendLog(String line) {
        Platform.runLater(() -> {
            simulationLogArea.appendText(line + "\n");
        });
    }

    private void startSimulation() {
        // Prevent multiple concurrent runs
        if (simulationThread != null && simulationThread.isAlive()) {
            showAlert("Simulation already running");
            return;
        }

        // Parse simulation time
        long simMs = 60_000L;
        String t = simulationTimeField.getText();
        if (t != null && !t.isBlank()) {
            try {
                simMs = Long.parseLong(t);
            } catch (NumberFormatException ex) {
                showAlert("Invalid simulation time (ms)");
                return;
            }
        }

        // Build sources map for Simulation
        // NOTE: this requires OperationDefinition setters/constructors (see below)
        java.util.Map<String, com.sim.mongo.model.Source> sourcesForSim = new java.util.HashMap<>();
        int sourceId = 0;
        for (SourceConfig sconf : sources) {
            // find assigned shard (by name) and map to an org.ja.Shard instance
            ShardConfig shardConf =  findShardByName(sconf.getAssignedShard());
            Shard targetShard = new org.ja.Shard(); // you may want a constructor or setup nodes
            for(CollectionConfig collConf : shardConf.getCollections()){
                targetShard.addCollection(collConf.getName(), collConf.getSize());
            }
            // Build lists of OperationDefinition
            java.util.List<org.ja.OperationDefinition> readDefs = new java.util.ArrayList<>();
            java.util.List<org.ja.OperationDefinition> writeDefs = new java.util.ArrayList<>();
            for (OperationConfig opc : sconf.getOperations()) {
                // convert DistributionConfig -> com.sim.mongo.distributions.Distribution
                com.sim.mongo.distributions.Distribution opsDist = convertToDistribution(opc.opsPerSecDist);
                com.sim.mongo.distributions.Distribution docsDist = convertToDistribution(opc.affectedDocsDist);
                com.sim.mongo.distributions.Distribution baseDist = convertToDistribution(opc.baseExecDist);

                org.ja.OperationDefinition def = new org.ja.OperationDefinition();
                // These setter methods must be added to OperationDefinition (see suggestion below)
                def.setId(opc.id);
                def.setAffectedCollectionName(opc.affectedCollection);
                def.setTypeFromString(opc.type);
                def.setOperationsPerSecondDistribution(opsDist);
                def.setAffectedDocNumberDistribution(docsDist);
                def.setBaseExecutionTimeDistribution(baseDist);

                readDefs.add(def); // or writeDefs depending on type
            }

            com.sim.mongo.model.Source srcModel = new com.sim.mongo.model.Source(sourceId++, targetShard, readDefs, writeDefs);
            if (sconf.getClientToNodeTravelTime() != null) {
                srcModel.setClientToNodeTravelTime(new com.sim.mongo.distributions.ConstantDistribution(sconf.getClientToNodeTravelTime()));
            }
            sourcesForSim.put(sconf.getName(), srcModel);
        }

        // Create and run Simulation in a background thread
        Simulation sim = new Simulation(sourcesForSim);
        sim.setSimulationTimeMs(simMs);
        sim.setup();

        appendLog("Simulation starting (time ms=" + simMs + ")");

        simulationThread = new Thread(() -> {
            try {
                statistics.clear();
                sim.run();
                appendLog("Simulation finished");
                // Store statistics reference for UI display
                //statistics = sim.getStatistics();
                //refreshStatistics();
            } catch (Exception ex) {
                System.out.println("Simulation error:");
                ex.printStackTrace(System.out);

                // GUI shows only a short, non-sensitive notice
                appendLog("Simulation error occurred (see stdout)");
            }
        }, "simulation-thread");
        simulationThread.start();
    }

    private void cancelSimulation() {
        if (simulationThread == null || !simulationThread.isAlive()) {
            appendLog("No running simulation to cancel");
            return;
        }
        appendLog("Cancelling simulation...");
        // Clear scheduler and interrupt the thread so run() will exit
        com.sim.mongo.GlobalScheduler.instance().clear(); // see suggested method below
        simulationThread.interrupt();
    }

    private com.sim.mongo.distributions.Distribution convertToDistribution(DistributionConfig d) {
        switch (d.getType()) {
            case "Constant": return new com.sim.mongo.distributions.ConstantDistribution(d.getParams()[0]);
            case "Uniform": return new com.sim.mongo.distributions.UniformDistribution(d.getParams()[0], d.getParams()[1]);
            case "Exponential": return new com.sim.mongo.distributions.ExponentialDistribution(d.getParams()[0]);
            default: throw new IllegalArgumentException("Unknown distribution: " + d.getType());
        }
    }

    // --- Statistics tab logic ---
    private void refreshStatistics() {
        if (statistics == null) {
            Platform.runLater(() -> {
                operationTypesListView.setItems(FXCollections.observableArrayList());
                statisticsTextArea.clear();
                statisticsTextArea.setText("No statistics available. Run simulation first.");
            });
            return;
        }

        Platform.runLater(() -> {
            // Create list of operation types + "ALL OPERATIONS"
            java.util.Set<String> opIDs = statistics.getRecordedOperationIDs();
            List<String> displayList = new ArrayList<>();
            displayList.add("ALL OPERATIONS");
            displayList.addAll(opIDs.stream().sorted().collect(Collectors.toList()));
            
            operationTypesListView.setItems(FXCollections.observableArrayList(displayList));
            
            // Auto-select "ALL OPERATIONS" if nothing selected
            if (operationTypesListView.getSelectionModel().getSelectedItem() == null) {
                operationTypesListView.getSelectionModel().selectFirst();
            }
        });
    }

    private void onOperationIDSelected(String opID) {
        if (opID == null) {
            statisticsTextArea.clear();
            return;
        }

        Platform.runLater(() -> {
            StringBuilder sb = new StringBuilder();
            
            if ("ALL OPERATIONS".equals(opID)) {
                sb.append("=== STATISTICS FOR ALL OPERATIONS ===\n\n");
                
                // Response time stats
                sb.append("--- RESPONSE TIME STATS ---\n");
                Statistics.ResponseTimeStats rtStats = statistics.getResponseTimeStatsAllOps();
                sb.append(rtStats.toString()).append("\n\n");
                
                // Percentiles
                double[] percentiles = {50, 95, 99, 99.9};
                java.util.Map<Double, Long> percentileMap = statistics.getResponseTimePercentilesAllOps(percentiles);
                sb.append("Response Time Percentiles (ms):\n");
                for (double p : percentiles) {
                    sb.append(String.format("  p%.1f: %d ms\n", p, percentileMap.get(p)));
                }
                sb.append("\n");
                
                // Lock wait time stats
                sb.append("--- LOCK WAIT TIME STATS ---\n");
                long totalLockWait = statistics.getTotalLockWaitTimeAllOps();
                //double avgLockWait = statistics.getAverageLockWaitTimeAllOps();
                double percentWaited = statistics.getPercentageOfOpsWaitedForLocksAllOps();
                sb.append(String.format("Total Lock Wait Time: %d ms\n", totalLockWait));
                //sb.append(String.format("Average Lock Wait Time: %.2f ms\n", avgLockWait));
                sb.append(String.format("Percentage of Ops That Waited for Locks: %.2f%%\n", percentWaited));
                sb.append("\n");
                
                // Throughput stats
                sb.append("--- THROUGHPUT STATS ---\n");
                double avgThroughput = statistics.getAverageThroughputAllOps();
                sb.append(String.format("Average Throughput: %.2f ops/sec\n", avgThroughput));
                sb.append(String.format("Total Operations Completed: %d\n", statistics.getTotalOperationsCompleted()));
                sb.append("\n");
                
                // Throughput by second
                sb.append("Throughput by Second (ops/sec):\n");
                java.util.Map<Long, Integer> throughput = statistics.getThroughputAllOps();
                for (java.util.Map.Entry<Long, Integer> entry : throughput.entrySet()) {
                    long secondMs = entry.getKey();
                    int count = entry.getValue();
                    sb.append(String.format("  Second %d: %d ops\n", secondMs / 1000, count));
                }
                
            } else {
                // Single operation type stats
                sb.append("=== STATISTICS FOR OPERATION: ").append(opID).append(" ===\n\n");
                
                // Response time stats
                sb.append("--- RESPONSE TIME STATS ---\n");
                Statistics.ResponseTimeStats rtStats = statistics.getResponseTimeStatsByOperation(opID);
                sb.append(rtStats.toString()).append("\n\n");
                
                // Percentiles
                double[] percentiles = {50, 95, 99, 99.9};
                java.util.Map<Double, Long> percentileMap = statistics.getResponseTimePercentilesByOperation(opID, percentiles);
                sb.append("Response Time Percentiles (ms):\n");
                for (double p : percentiles) {
                    sb.append(String.format("  p%.1f: %d ms\n", p, percentileMap.get(p)));
                }
                sb.append("\n");
                
                // Lock wait time stats
                sb.append("--- LOCK WAIT TIME STATS ---\n");
                //long totalLockWait = statistics.getTotalLockWaitTimeByOperation(operationType);
                //double avgLockWait = statistics.getAverageLockWaitTimeByOperation(operationType);
                //double percentWaited = statistics.getPercentageOfOpsWaitedForLocksByOperation(operationType);
                //sb.append(String.format("Total Lock Wait Time: %d ms\n", totalLockWait));
                //sb.append(String.format("Average Lock Wait Time: %.2f ms\n", avgLockWait));
                //sb.append(String.format("Percentage of Ops That Waited for Locks: %.2f%%\n", percentWaited));
                sb.append("\n");
                
                // Throughput stats
                sb.append("--- THROUGHPUT STATS ---\n");
                double avgThroughput = statistics.getAverageThroughputByOperation(opID);
                sb.append(String.format("Average Throughput: %.2f ops/sec\n", avgThroughput));
                sb.append("\n");
                
                // Throughput by second
                sb.append("Throughput by Second (ops/sec):\n");
                java.util.Map<Long, Integer> throughput = statistics.getThroughputByOperation(opID);
                for (java.util.Map.Entry<Long, Integer> entry : throughput.entrySet()) {
                    long secondMs = entry.getKey();
                    int count = entry.getValue();
                    sb.append(String.format("  Second %d: %d ops\n", secondMs / 1000, count));
                }
            }
            
            statisticsTextArea.setText(sb.toString());
        });
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

        // Build dialog for operation
        Dialog<OperationConfig> dialog = new Dialog<>();
        dialog.setTitle("Add Operation");
        dialog.setHeaderText("Define operation to be sent by this source");
        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);

        TextField idField = new TextField();

        idField.setPromptText("operation id");

        ChoiceBox<String> collectionChoice = new ChoiceBox<>();
        // populate with collections from assigned shard (if any)
        List<String> colNames = new ArrayList<>();
        String assignedShardName = src.getAssignedShard();
        if (assignedShardName != null) {
            ShardConfig shard = findShardByName(assignedShardName);
            if (shard != null) {
                for (CollectionConfig c : shard.getCollections()) colNames.add(c.getName());
            }
        }
        collectionChoice.setItems(FXCollections.observableArrayList(colNames));

        // type choice populated from OperationTypeEnum
        ChoiceBox<String> typeChoice = new ChoiceBox<>(FXCollections.observableArrayList(
                Arrays.stream(OperationTypeEnum.values()).map(Enum::name).collect(Collectors.toList())
        ));
        typeChoice.setValue(OperationTypeEnum.values()[0].name());


        // distribution buttons and summaries
        Button opsPerSecBtn = new Button("Set ops/sec distribution");
        Label opsPerSecSummary = new Label("(not set)");
        Button affectedDocsBtn = new Button("Set affectedDocs distribution");
        Label affectedDocsSummary = new Label("(not set)");
        Button baseExecBtn = new Button("Set baseExecTime distribution");
        Label baseExecSummary = new Label("(not set)");

        final DistributionConfig[] opsDist = new DistributionConfig[1];
        final DistributionConfig[] docsDist = new DistributionConfig[1];
        final DistributionConfig[] baseDist = new DistributionConfig[1];

        opsPerSecBtn.setOnAction(e -> {
            DistributionConfig d = showDistributionDialog();
            if (d != null) {
                opsDist[0] = d;
                opsPerSecSummary.setText(d.summary());
            }
        });
        affectedDocsBtn.setOnAction(e -> {
            DistributionConfig d = showDistributionDialog();
            if (d != null) {
                docsDist[0] = d;
                affectedDocsSummary.setText(d.summary());
            }
        });
        baseExecBtn.setOnAction(e -> {
            DistributionConfig d = showDistributionDialog();
            if (d != null) {
                baseDist[0] = d;
                baseExecSummary.setText(d.summary());
            }
        });

        grid.add(new Label("Operation id:"), 0, 0);
        grid.add(idField, 1, 0);
        grid.add(new Label("Affected collection:"), 0, 1);
        grid.add(collectionChoice, 1, 1);

        grid.add(new Label("Type:"), 0, 2);
        grid.add(typeChoice, 1, 2);

        grid.add(opsPerSecBtn, 0, 3);
        grid.add(opsPerSecSummary, 1, 3);
        grid.add(affectedDocsBtn, 0, 4);
        grid.add(affectedDocsSummary, 1, 4);
        grid.add(baseExecBtn, 0, 5);
        grid.add(baseExecSummary, 1, 5);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                String id = idField.getText();
                String coll = collectionChoice.getValue();
                String type = typeChoice.getValue();
                if (id == null || id.isBlank()) { showAlert("Operation id required"); return null; }
                if (opsDist[0] == null) { showAlert("ops/sec distribution required"); return null; }
                if (docsDist[0] == null) { showAlert("affectedDocs distribution required"); return null; }
                if (baseDist[0] == null) { showAlert("baseExecTime distribution required"); return null; }

                return new OperationConfig(id.trim(), type, coll, opsDist[0], docsDist[0], baseDist[0]);
            }
            return null;
        });

        Optional<OperationConfig> res = dialog.showAndWait();
        res.ifPresent(op -> {
            src.getOperations().add(op);
            operationsListView.setItems(FXCollections.observableArrayList(src.getOperations()));
        });
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

    private ShardConfig findShardByName(String name){
        for (ShardConfig s : shards) if (s.getName().equals(name)) return s;
        return null;
    }

    private DistributionConfig showDistributionDialog(){
        Dialog<DistributionConfig> dialog = new Dialog<>();
        dialog.setTitle("Distribution");
        dialog.setHeaderText("Select distribution type and parameters");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);

        ChoiceBox<String> typeChoice = new ChoiceBox<>(FXCollections.observableArrayList("Constant", "Uniform", "Exponential"));
        typeChoice.setValue("Constant");

        TextField param1 = new TextField();
        param1.setPromptText("value (or min)");
        TextField param2 = new TextField();
        param2.setPromptText("max (for Uniform)");

        grid.add(new Label("Type:"), 0, 0);
        grid.add(typeChoice, 1, 0);
        grid.add(new Label("Param 1:"), 0, 1);
        grid.add(param1, 1, 1);
        grid.add(new Label("Param 2:"), 0, 2);
        grid.add(param2, 1, 2);

        // dynamically enable/disable param2 depending on type
        typeChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if ("Uniform".equals(newV)) param2.setDisable(false); else param2.setDisable(true);
            if ("Exponential".equals(newV)) param1.setPromptText("mean"); else param1.setPromptText("value (or min)");
        });
        param2.setDisable(true);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                String type = typeChoice.getValue();
                try {
                    if ("Constant".equals(type)) {
                        double v = Double.parseDouble(param1.getText());
                        return new DistributionConfig("Constant", new double[]{v});
                    } else if ("Uniform".equals(type)) {
                        double min = Double.parseDouble(param1.getText());
                        double max = Double.parseDouble(param2.getText());
                        if (max < min) { showAlert("Uniform max must be >= min"); return null; }
                        return new DistributionConfig("Uniform", new double[]{min, max});
                    } else if ("Exponential".equals(type)) {
                        double mean = Double.parseDouble(param1.getText());
                        return new DistributionConfig("Exponential", new double[]{mean});
                    }
                } catch (NumberFormatException ex){ showAlert("Invalid numeric parameter"); return null; }
            }
            return null;
        });

        Optional<DistributionConfig> r = dialog.showAndWait();
        return r.orElse(null);
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
        private final String affectedCollection; // collection name
        private final DistributionConfig opsPerSecDist;
        private final DistributionConfig affectedDocsDist;
        private final DistributionConfig baseExecDist;
        public OperationConfig(String id, String type, String affectedCollection, DistributionConfig opsPerSecDist, DistributionConfig affectedDocsDist, DistributionConfig baseExecDist){
            this.id = id; this.type = type; this.affectedCollection = affectedCollection; this.opsPerSecDist = opsPerSecDist; this.affectedDocsDist = affectedDocsDist; this.baseExecDist = baseExecDist;
        }
        @Override public String toString(){
            return id + " [" + type + "] coll=" + (affectedCollection==null?"(any)":affectedCollection) + " ops=" + opsPerSecDist.summary();
        }
    }

    public static class DistributionConfig {
        private final String type;
        private final double[] params;
        public DistributionConfig(String type, double[] params){ this.type = type; this.params = params; }
        public String getType(){ return type; }
        public double[] getParams(){ return params; }
        public String summary(){
            if ("Constant".equals(type)) return "Const("+params[0]+")";
            if ("Uniform".equals(type)) return "Unif("+params[0]+","+params[1]+")";
            if ("Exponential".equals(type)) return "Exp(mean="+params[0]+")";
            return type;
        }
    }
}
