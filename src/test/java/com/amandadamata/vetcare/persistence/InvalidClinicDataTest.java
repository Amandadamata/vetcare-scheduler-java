package com.amandadamata.vetcare.persistence;

import com.amandadamata.vetcare.exception.DataPersistenceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Files that are valid JSON but contain data the application could never have created. Each case
 * changes one value of an otherwise valid file, so a failure points to exactly one rule.
 */
class InvalidClinicDataTest {
    private static final String OWNER_ID = "10000000-0000-0000-0000-000000000001";
    private static final String ANIMAL_ID = "20000000-0000-0000-0000-000000000001";
    private static final String APPOINTMENT_ID = "30000000-0000-0000-0000-000000000001";
    private static final String RESOURCE_ID = "40000000-0000-0000-0000-000000000001";

    private static final String VALID_JSON = """
            {
              "schemaVersion": 2,
              "resources": [
                {"id": "%s", "name": "Dr. Ana"}
              ],
              "owners": [
                {"id": "%s", "name": "Test Owner", "phone": "+1 202-555-0100", "email": "owner@example.com"}
              ],
              "animals": [
                {"id": "%s", "ownerId": "%s", "name": "Luna", "species": "DOG",
                 "birthDate": "2021-05-10", "notes": ""}
              ],
              "appointments": [
                {"id": "%s", "animalId": "%s", "resourceId": "%s", "type": "EXAM", "startTime": "2026-09-25T14:30:00",
                 "durationMinutes": 45, "condition": "CONCERNING", "clinicalSigns": ["SEVERE_PAIN"],
                 "notes": "", "priority": "URGENT", "status": "SCHEDULED",
                 "createdAt": "2026-09-24T13:00:00Z"}
              ]
            }
            """.formatted(RESOURCE_ID, OWNER_ID, ANIMAL_ID, OWNER_ID, APPOINTMENT_ID, ANIMAL_ID, RESOURCE_ID);

    @TempDir
    Path tempDir;

    @Test
    void unchangedFixtureLoadsSuccessfully() throws IOException {
        ClinicSnapshot snapshot = new JsonClinicDataStore(write(VALID_JSON)).load();

        assertAll(
                () -> assertEquals(1, snapshot.owners().size()),
                () -> assertEquals(1, snapshot.animals().size()),
                () -> assertEquals(1, snapshot.appointments().size()));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "unknown appointment type     | \"type\": \"EXAM\"                  | \"type\": \"SURGERY\"                 | missing or unknown type",
            "unknown status               | \"status\": \"SCHEDULED\"           | \"status\": \"DONE\"                  | missing or unknown status",
            "unknown clinical sign        | [\"SEVERE_PAIN\"]                   | [\"COUGH\"]                           | unknown clinical sign",
            "duration out of range        | \"durationMinutes\": 45             | \"durationMinutes\": 5000             | Duration must be between 15 and 180 minutes",
            "missing start time           | \"startTime\": \"2026-09-25T14:30:00\", | ''                                | missing or unknown start time",
            "appointment for no animal    | \"animalId\": \"" + ANIMAL_ID + "\" | \"animalId\": \"" + OWNER_ID + "\"    | refers to a missing animal",
            "invalid owner phone          | \"+1 202-555-0100\"                 | \"123\"                               | Phone must contain between 8 and 15 digits",
            "blank animal name            | \"name\": \"Luna\"                  | \"name\": \"  \"                      | Animal name is required",
            "null owner email             | \"email\": \"owner@example.com\"    | \"email\": null                       | missing or unknown email",
            "appointment for no schedule  | \"resourceId\": \"" + RESOURCE_ID + "\" | \"resourceId\": \"" + OWNER_ID + "\" | refers to a missing schedule",
            "unknown new status           | \"status\": \"SCHEDULED\"           | \"status\": \"POSTPONED\"             | missing or unknown status",
            "anonymized owner with contact | \"owner@example.com\"}             | \"owner@example.com\", \"anonymized\": true} | An anonymized owner cannot keep a phone or email"
    })
    void semanticallyInvalidDataIsRejectedWithAClearMessage(
            String scenario, String original, String replacement, String expectedMessage) throws IOException {
        String invalidJson = replaceOnce(VALID_JSON, original, replacement);
        Path dataFile = write(invalidJson);

        DataPersistenceException exception = assertThrows(
                DataPersistenceException.class,
                () -> new JsonClinicDataStore(dataFile).load());

        assertAll(
                () -> assertTrue(exception.getMessage().contains(expectedMessage), exception.getMessage()),
                () -> assertTrue(exception.getMessage().contains("The file was not changed."), exception.getMessage()),
                () -> assertEquals(invalidJson, Files.readString(dataFile, StandardCharsets.UTF_8)));
    }

    @Test
    void animalOwnedByMissingOwnerIsRejected() throws IOException {
        String invalidJson = replaceOnce(VALID_JSON,
                "\"ownerId\": \"" + OWNER_ID + "\"",
                "\"ownerId\": \"" + APPOINTMENT_ID + "\"");

        DataPersistenceException exception = assertThrows(
                DataPersistenceException.class,
                () -> new JsonClinicDataStore(write(invalidJson)).load());

        assertTrue(exception.getMessage().contains("Animal " + ANIMAL_ID + " refers to a missing owner"),
                exception.getMessage());
    }

    @Test
    void duplicateIdsAreRejected() throws IOException {
        String duplicatedOwner = "{\"id\": \"" + OWNER_ID
                + "\", \"name\": \"Other\", \"phone\": \"+1 202-555-0101\", \"email\": \"\"}";
        String invalidJson = replaceOnce(VALID_JSON, "\"owners\": [", "\"owners\": [" + duplicatedOwner + ",");

        DataPersistenceException exception = assertThrows(
                DataPersistenceException.class,
                () -> new JsonClinicDataStore(write(invalidJson)).load());

        assertTrue(exception.getMessage().contains("Owner ID " + OWNER_ID + " appears more than once"),
                exception.getMessage());
    }

    private Path write(String json) throws IOException {
        Path dataFile = tempDir.resolve("clinic.json");
        Files.writeString(dataFile, json, StandardCharsets.UTF_8);
        return dataFile;
    }

    private static String replaceOnce(String text, String original, String replacement) {
        int index = text.indexOf(original);
        if (index < 0 || text.indexOf(original, index + 1) >= 0) {
            throw new IllegalArgumentException("Fixture text must appear exactly once: " + original);
        }
        return text.substring(0, index) + replacement + text.substring(index + original.length());
    }
}
