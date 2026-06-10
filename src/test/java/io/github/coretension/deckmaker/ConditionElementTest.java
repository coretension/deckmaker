package io.github.coretension.deckmaker;

import io.github.coretension.deckmaker.config.*;
import io.github.coretension.deckmaker.model.*;
import io.github.coretension.deckmaker.persistence.*;
import io.github.coretension.deckmaker.service.*;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ConditionElementTest {

    @Test
    public void testConditionEvaluation() {
        DataMerger dataMerger = new DataMerger();
        ConditionElement ce = new ConditionElement();
        ce.setCondition("{{Type}} == Spell");
        
        Map<String, String> recordMatch = new HashMap<>();
        recordMatch.put("Type", "Spell");
        
        Map<String, String> recordNoMatch = new HashMap<>();
        recordNoMatch.put("Type", "Creature");
        
        assertTrue(dataMerger.evaluateCondition(ce.getCondition(), recordMatch));
        assertFalse(dataMerger.evaluateCondition(ce.getCondition(), recordNoMatch));
    }

    @Test
    public void testDefaultValues() {
        ConditionElement ce = new ConditionElement();
        assertEquals("", ce.getCondition());
        assertEquals("Condition", ce.getName());
        assertTrue(ce.getChildren().isEmpty());
    }
}

