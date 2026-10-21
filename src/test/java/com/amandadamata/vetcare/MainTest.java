package com.amandadamata.vetcare;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MainTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void missingEnvironmentVariableUsesTheDefaultDataFile(String configuredPath) {
        assertEquals(Path.of("data", "vetcare-data.json"), Main.resolveDataFile(configuredPath));
    }

    @Test
    void configuredPathIsTrimmed() {
        assertEquals(Path.of("/tmp/demo.json"), Main.resolveDataFile("  /tmp/demo.json  "));
    }
}
