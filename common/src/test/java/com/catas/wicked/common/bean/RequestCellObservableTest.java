package com.catas.wicked.common.bean;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class RequestCellObservableTest {

    @Test
    public void exposesObservableDisplayProperties() {
        RequestCell cell = new RequestCell("Initial", "GET");
        AtomicInteger countChanges = new AtomicInteger();
        cell.countProperty().addListener((observable, oldValue, newValue) -> countChanges.incrementAndGet());

        cell.setPath("Updated");
        cell.setSecondaryText("Process 42");
        cell.setCount(3);

        assertEquals("Updated", cell.getPath());
        assertEquals("Process 42", cell.getSecondaryText());
        assertEquals(3, cell.getCount());
        assertEquals(1, countChanges.get());
    }
}
