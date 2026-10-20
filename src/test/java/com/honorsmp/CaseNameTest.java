package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.honorsmp.logic.CaseName;
import org.junit.jupiter.api.Test;

class CaseNameTest {
    @Test
    void joinsLinesWithSpaceAndTrims() {
        assertEquals("Spawn kill dispute", CaseName.fromSign("  Spawn kill", "", "dispute ", null));
    }

    @Test
    void emptyClears() {
        assertEquals("", CaseName.fromSign("", " ", "", ""));
        assertEquals("", CaseName.fromSign((String[]) null));
    }

    @Test
    void stripsFormattingAndControlChars() {
        assertEquals("Red name", CaseName.sanitize("\u00a7cRed\u0007 name"));
        assertEquals("a b", CaseName.sanitize("a\tb"));
    }

    @Test
    void capsAt40() {
        assertEquals(40, CaseName.sanitize("x".repeat(100)).length());
    }

    @Test
    void titleFormat() {
        assertEquals("Case #7", CaseName.title(7, ""));
        assertEquals("Case #7", CaseName.title(7, null));
        assertEquals("Case #7 \"Dispute\"", CaseName.title(7, "Dispute"));
    }

    @Test
    void oldCaseJsonWithoutNameLoads() {
        String json = "{\"version\":1,\"nextCaseId\":3,\"cases\":[{\"id\":2,\"killerName\":\"A\",\"status\":\"OPEN\"}]}";
        HonorData d = HonorData.parse(json);
        assertEquals(1, d.cases.size());
        assertEquals("", d.cases.get(0).name);
    }
}
