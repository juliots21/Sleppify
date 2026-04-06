package com.example.sleppify;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GeminiIntelligenceServiceTest {

    @Test
    public void normalizeAiTaskCategoryValue_returnsEmpty_forNullOrBlank() {
        assertEquals("", GeminiIntelligenceService.normalizeAiTaskCategoryValue(null));
        assertEquals("", GeminiIntelligenceService.normalizeAiTaskCategoryValue("   \n  \r  "));
    }

    @Test
    public void normalizeAiTaskCategoryValue_cleansQuotesAndKeepsSingleWord() {
        String value = GeminiIntelligenceService.normalizeAiTaskCategoryValue(" \" Preparacion escolar... \" ");
        assertEquals("Preparacion", value);
    }

    @Test
    public void normalizeAiTaskCategoryValue_returnsFirstWordWhenPhraseProvided() {
        String value = GeminiIntelligenceService.normalizeAiTaskCategoryValue("Plan   de\ntrabajo\r semestral");
        assertEquals("Plan", value);
    }

    @Test
    public void normalizeAiTaskCategoryValue_truncatesTo24Characters() {
        String raw = "Categoria extremadamente larga para validar truncado controlado";
        String value = GeminiIntelligenceService.normalizeAiTaskCategoryValue(raw);
        assertTrue(value.length() <= 24);
    }
}
