# Design decisions

Each entry records what was decided, why, what else was considered, and how the decision is
verified.

## 1. Layered packages with constructor injection

- **Decision:** separate `cli`, `service`, `repository`, `persistence` and `model`; `Main` creates
  every object and passes dependencies through constructors.
- **Why:** business rules can be tested without a terminal or a file, and each layer can change
  without rewriting the others (for example, replacing JSON with a database touches only
  `persistence`).
- **Alternative:** a dependency-injection framework such as Spring. Not needed for a dozen
  objects; plain constructors keep the wiring visible in one method.
- **Verified by:** service tests build services with an in-memory `ClinicDataStore`; CLI tests
  run the real menu with a `StringReader`.

## 2. Triage priority is calculated, not chosen

- **Decision:** `TriageService` derives `EMERGENCY`, `URGENT` or `STANDARD` from the patient
  condition and clinical signs. Emergency rules are checked first.
- **Why:** a person under time pressure should not have to remember the rules, and checking the
  strongest rule first means a weaker rule can never lower the result.
- **Note:** the rules are educational examples, not a veterinary protocol.
- **Verified by:** `TriageServiceTest`,
  `AppointmentServiceTest.updateClinicalDetailsRecalculatesPriorityAndPersists`.

## 3. Interval overlap for conflicts

- **Decision:** two active appointments conflict when
  `start1 < end2 && start2 < end1`.
- **Why:** this single condition covers partial overlap, containment and identical slots, while
  allowing back-to-back appointments (10:00–10:30 and 10:30–11:00).
- **Alternative:** checking fixed time slots. Simpler, but forces all durations into the same grid.
- **Verified by:** `ScheduleConflictServiceTest`.

## 4. Two-year booking horizon

- **Decision:** appointments must start in the future and no more than two years ahead.
- **Why:** a start time close to `LocalDateTime.MAX` made the end-time calculation throw an
  uncaught `DateTimeException`, and year typos (2206 instead of 2026) were accepted. A booking
  horizon is also a normal clinic rule.
- **Verified by:** `ValidatorsTest.appointmentMustBeWithinTheBookingHorizon`,
  `VetCareCliTest.businessRuleErrorsAreShownAndTheMenuContinues`.

## 5. `PriorityQueue` for the triage view

- **Decision:** the daily queue is built in a `PriorityQueue` and emptied with `poll()`.
- **Why:** it models "who is next?" directly. Iterating a `PriorityQueue` is not ordered, so
  `poll()` is required.
- **Honest trade-off:** building the full list once costs O(n log n), the same as sorting. A
  `PriorityQueue` would be faster than re-sorting only if the queue were kept and consumed
  incrementally.
- **Tie-break:** within one priority, the appointment registered first comes first (arrival order),
  then start time, then ID for a stable result.
- **Verified by:** `AppointmentServiceTest.triageQueueOrdersByPriorityThenCreationTimeAndExcludesCancelledOrOtherDates`.

## 6. Collections

| Use | Collection | Why |
|---|---|---|
| Repository storage | `LinkedHashMap<UUID, T>` | O(1) lookup by ID and predictable iteration order |
| Clinical signs | `Set`, `EnumSet` in the CLI | No duplicates; `EnumSet` is compact and ordered by declaration |
| Report counts | `EnumMap<TriagePriority, Long>` | Every enum key initialized to zero; ordered output |
| Results returned to callers | `List.copyOf`, `Set.copyOf` | Callers cannot modify internal state |

## 7. UUID identifiers

- **Decision:** records use random `UUID`s.
- **Why:** IDs can be created without a counter or a database, never collide in practice, and stay
  valid when records are saved and loaded.
- **Trade-off:** long to type, so the CLI also accepts the list number shown on screen.

## 8. JSON file with atomic replace

- **Decision:** the whole repository is saved as one JSON snapshot with Gson after each change,
  writing a temporary file first and then moving it into place.
- **Why:** human-readable, no database to install, and an interrupted write cannot corrupt the
  existing file.
- **Alternative:** SQLite or another database. Adds transactions and concurrent access, which the
  single-user CLI does not need yet. Listed as a possible next step.
- **Verified by:** `JsonClinicDataStoreTest`.

## 9. Validate data loaded from disk

- **Decision:** `ClinicSnapshotValidator` checks every loaded record before the repository
  receives it.
- **Why:** Gson creates objects without calling the services, so loaded data skips every business
  rule. An unknown enum name becomes `null` silently. Without this check, an edited file loaded
  "successfully" and the daily report later crashed with a `NullPointerException`.
- **Alternative:** validating inside model constructors. Gson bypasses constructors, so it would
  not help here.
- **Verified by:** `InvalidClinicDataTest` (11 of its 12 tests fail if the validation call is
  removed).

## 10. Unchecked domain exceptions

- **Decision:** `ValidationException`, `NotFoundException`, `ScheduleConflictException` and
  `DataPersistenceException` extend `RuntimeException`.
- **Why:** they are reported to the user at one place (the CLI); intermediate methods do not need
  to declare them. Separate types let the CLI react differently, for example marking persistence
  as unhealthy only for `DataPersistenceException`.
- **Verified by:** `VetCareCliTest.failedSaveIsReportedAndRememberedAtExit`.

## 11. Injected `Clock`

- **Decision:** "now" comes from a `Clock` passed to services and the CLI.
- **Why:** future-date, birth-date and booking-horizon rules depend on the current time; a fixed
  clock makes those tests repeatable on any day.

## 12. Keep a change in memory when saving fails

- **Decision:** if saving fails, the in-memory change is kept, the user is warned, and the next
  successful save includes it.
- **Why:** rolling back would need an undo for every operation. For a local single-user tool,
  clear warnings were judged sufficient.
- **Verified by:** `OwnerAndAnimalServiceTest.failedSaveIsReportedAndTheNextSuccessfulSaveIncludesTheChange`.
