package com.catas.wicked.common.provider;

import javafx.stage.Stage;

public interface StageProvider {

    void initStage(Stage primaryStage);

    default void setAppIcon(Stage stage) {}
}
