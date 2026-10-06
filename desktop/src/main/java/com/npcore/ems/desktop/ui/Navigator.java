package com.npcore.ems.desktop.ui;

import java.util.function.Supplier;
import javafx.scene.Node;

/** Mở một màn hình trong tab của cửa sổ chính (mở lại tab cũ nếu cùng key). */
public interface Navigator {
    void open(String key, String title, Supplier<Node> view);
}
