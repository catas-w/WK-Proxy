package com.catas.wicked.proxy.gui.componet;

import javafx.scene.control.Label;

import java.util.function.Supplier;

public class EnumLabel<T extends Enum<T>> extends Label {

    private final T obj;

    public EnumLabel(T obj) {
        super(obj.name());
        this.obj = obj;
    }

    public EnumLabel(T obj, String name) {
        super(name);
        this.obj = obj;
    }

    public EnumLabel(T obj, Supplier<String> nameSupplier) {
        super(nameSupplier.get());
        this.obj = obj;
    }

    public T getEnum() {
        return this.obj;
    }
}
