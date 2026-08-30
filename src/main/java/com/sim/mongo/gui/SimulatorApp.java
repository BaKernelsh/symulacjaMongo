package com.sim.mongo.gui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class SimulatorApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/sim/mongo/gui/layout.fxml"));
        Parent root = loader.load();
        stage.setTitle("Mongo Simulation Configurator");
        stage.setScene(new Scene(root, 900, 600));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
