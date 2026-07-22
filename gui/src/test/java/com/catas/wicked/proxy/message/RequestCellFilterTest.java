package com.catas.wicked.proxy.message;

import com.catas.wicked.common.bean.RequestCell;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RequestCellFilterTest {

    @Test
    public void filterMatchesApplicationProcessHostMethodAndUrlText() {
        RequestCell cell = new RequestCell("/api/items", "GET");
        cell.setSearchText("Google Chrome chrome cloud.google.com GET https://cloud.google.com/api/items");

        assertTrue(cell.matchesFilter("chrome"));
        assertTrue(cell.matchesFilter("CLOUD.GOOGLE.COM"));
        assertTrue(cell.matchesFilter("get"));
        assertFalse(cell.matchesFilter("firefox"));
    }
}
