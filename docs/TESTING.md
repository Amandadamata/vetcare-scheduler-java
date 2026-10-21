# Testing

## Running the tests

```bash
./mvnw test      # compile and run all tests
./mvnw verify    # tests plus packaging, the same command CI runs
./mvnw test -Dtest=InvalidClinicDataTest   # one class
```

## Test classes

The suite has 123 tests. Parameterized tests count once per case.

| Class | Tests | What it verifies |
|---|---:|---|
| `AppointmentServiceTest` | 19 | Scheduling, conflicts per schedule, filters (including by schedule), ordering, triage queue, clinical updates, rescheduling to another schedule, cancellation, attendance before and after the start time, final statuses |
| `AppointmentStatusTest` | 18 | All 16 (from, to) status pairs; which statuses occupy the schedule; which are final |
| `InvalidClinicDataTest` | 15 | Valid JSON with impossible data: unknown enums, out-of-range values, broken references (owner, animal, schedule), duplicate IDs, anonymized owners with contact data |
| `VetCareCliTest` | 13 | Full menu sessions: appointment life cycle, errors, invalid input, aborted cancellation, failed saves, closed input, two schedules at the same time, attendance, typed confirmation, blocked and undone deletions, anonymization |
| `OwnerAndAnimalServiceTest` | 10 | Registration, updates, searches, owner references, failed-save behavior |
| `PrivacyServiceTest` | 9 | Impact counts, deleting an owner or an animal without leaving broken references, refusal while appointments are scheduled, anonymization, editing anonymized records, undo after a failed save, saving and reloading anonymized data |
| `ValidatorsTest` | 8 | Text, phone, email, birth date, future date, booking horizon, duration |
| `ClinicDataMigrationTest` | 6 | Migrating a file written by the previous release, backup creation, no second migration, never overwriting a backup, invalid old data, newer versions refused |
| `ScheduleConflictServiceTest` | 5 | Partial, contained and adjacent intervals; cancelled and self-ignored appointments; other schedules; completed versus no-show |
| `MainTest` | 4 | Data-file path resolution from `VETCARE_DATA_FILE` |
| `JsonClinicDataStoreTest` | 4 | JSON round trip, missing and blank files, invalid JSON left untouched |
| `TriageServiceTest` | 4 | Emergency, urgent and standard rules, and emergency precedence |
| `ReportServiceTest` | 3 | Daily totals and groupings by status, priority and type |
| `ScheduleResourceServiceTest` | 3 | Alphabetical listing, unique names ignoring case, deleting only unused schedules |
| `ClinicRepositoryTest` | 2 | Loading replaces memory; snapshots are independent copies |

## Techniques used

- **Real objects instead of mocks.** Services are small and cheap to create. Tests use a real
  `ClinicRepository` and a tiny in-memory `ClinicDataStore` that records or fails saves.
- **Fixed clock.** `Clock.fixed(...)` makes "now" the same on every machine and every day. The
  attendance tests place appointments just before and just after that fixed instant.
- **Temporary directories.** `@TempDir` gives persistence and migration tests an isolated folder
  that JUnit deletes afterwards.
- **One change per invalid-data case.** `InvalidClinicDataTest` starts from a valid JSON fixture
  and changes exactly one value per case, so a failure points to one rule.
- **A real old file for migration.** `src/test/resources/migration/v1-written-by-release-02c2e8d.json`
  was produced by running the previous release (commit `02c2e8d`) with synthetic data, so the test
  reads exactly what users of that version have on disk.
- **Checking that tests can fail.**
  - With the validation call in `JsonClinicDataStore.load` removed, 14 of the 15
    `InvalidClinicDataTest` cases fail; the one that passes is the control case that loads the
    unchanged valid fixture.
  - `VetCareCliTest.failedDeletionIsUndoneAndNotReportedAsAChangeInMemory` was written before the
    fix and failed on three assertions until `ChangeUndoneException` was added.
- **Simulated console.** `VetCareCliTest` runs the real menu with a `StringReader` and captures a
  `StringWriter`. A test can run two sessions on the same data store to simulate closing and
  reopening the program.
- **Synthetic data only.** Names, phone numbers (`+1 202-555-01xx`, a range reserved for fiction)
  and emails in tests are invented.

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

## End-to-end check of the migration

Besides the automated tests, the migration was checked by hand with the packaged applications:
the release `02c2e8d` JAR created a data file, the new JAR opened it, printed the backup location,
kept every record, and accepted a second appointment at the same time on another schedule. The
backup was byte-for-byte identical to the original file, and opening the file again did not
migrate it a second time.

## Continuous integration

`.github/workflows/ci.yml` runs `./mvnw --batch-mode verify` on Java 21 and 25 for every push and pull
request to `main`.
