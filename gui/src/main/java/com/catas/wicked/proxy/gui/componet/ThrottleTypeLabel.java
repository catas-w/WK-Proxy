package com.catas.wicked.proxy.gui.componet;


import com.catas.wicked.common.constant.ThrottlePreset;
import javafx.scene.control.Label;

public abstract class ThrottleTypeLabel extends Label {

    public ThrottleTypeLabel(String name) {
        super(name);
    }

    public abstract ThrottlePreset getThrottlePreset();
}
