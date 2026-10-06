package com.npcore.ems.desktop;

import javafx.application.Application;

/**
 * Điểm vào khi chạy từ classpath (java -jar, jpackage). Lớp main không kế thừa Application
 * để JavaFX chạy được khi nằm trên classpath thay vì module path.
 */
public final class Launcher {
    private Launcher() {}

    public static void main(String[] args) {
        Application.launch(EmsApp.class, args);
    }
}
