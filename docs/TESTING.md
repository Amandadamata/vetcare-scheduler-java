# Testing

## Running the tests

```bash
./mvnw test      # compile and run all tests
./mvnw verify    # tests plus packaging, the same command CI runs
./mvnw test -Dtest=InvalidClinicDataTest   # one class
```

## Test classes

| Class | What it verifies |
|---|---|
| `TriageServiceTest` | Emergency, urgent and standard rules, and emergency precedence |
| `ScheduleConflictServiceTest` | Partial, contained and adjacent intervals; cancelled and self-ignored appointments |
| `AppointmentServiceTest` | Scheduling, conflicts, filters, ordering, triage queue, clinical updates, rescheduling, cancellation |
| `OwnerAndAnimalServiceTest` | Registration, updates, searches, owner references, failed-save behavior |
| `ReportServiceTest` | Daily totals and groupings |
| `ValidatorsTest` | Text, phone, email, birth date, future date, booking horizon, duration |
| `JsonClinicDataStoreTest` | JSON round trip, missing and blank files, invalid JSON left untouched |
| `InvalidClinicDataTest` | Valid JSON with impossible data: unknown enums, out-of-range values, broken references, duplicate IDs |
| `ClinicRepositoryTest` | Loading replaces memory; snapshots are independent copies |
| `VetCareCliTest` | Full menu sessions: appointment lifecycle, error messages, invalid input, aborted cancellation, failed saves, closed input |
| `MainTest` | Data-file path resolution from `VETCARE_DATA_FILE` |

## Techniques used

- **Real objects instead of mocks.** Services are small and cheap to create. Tests use a real
  `ClinicRepository` and a tiny in-memory `ClinicDataStore` that records or fails saves.
- **Fixed clock.** `Clock.fixed(...)` makes "now" the same on every machine and every day.
- **Temporary directories.** `@TempDir` gives persistence tests an isolated folder that JUnit
  deletes afterwards.
- **One change per invalid-data case.** `InvalidClinicDataTest` starts from a valid JSON fixture
  and changes exactly one value per case, so a failure points to one rule.
- **Checking that tests can fail.** The invalid-data tests were run once with the validation call
  removed; 11 of 12 failed, confirming they detect the bug they were written for.
- **Simulated console.** `VetCareCliTest` runs the real menu with a `StringReader` and captures a
  `StringWriter`.

## Coverage

Coverage is used to find untested behavior, not as a target. Generate a local report without
changing `pom.xml`:

```bash
./mvnw org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent test \
       org.jacoco:jacoco-maven-plugin:0.8.13:report
open target/site/jacoco/index.html
```

A coverage run during the October 2026 review showed that `updateClinicalDetails` (which
recalculates the triage priority) and the CLI had no tests; both are covered now. Remaining
uncovered lines are mostly CLI retry messages for invalid input and the `main` method that wires
the real console.

## Continuous integration

`.github/workflows/ci.yml` runs `./mvnw --batch-mode verify` on Java 21 and 25 for every push and pull
request to `main`.
