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

## 3. Interval overlap for conflicts, per schedule

- **Decision:** two appointments conflict when they use the **same schedule**, both still occupy it
  (`SCHEDULED` or `COMPLETED`), and `start1 < end2 && start2 < end1`.
- **Why:** this single condition covers partial overlap, containment and identical slots, while
  allowing back-to-back appointments (10:00–10:30 and 10:30–11:00). Filtering by schedule first lets
  two veterinarians work at the same time.
- **Alternative:** checking fixed time slots. Simpler, but forces all durations into the same grid.
- **Verified by:** `ScheduleConflictServiceTest`,
  `AppointmentServiceTest.sameTimeIsAllowedOnDifferentSchedulesButNotOnTheSameOne`,
  `VetCareCliTest.differentSchedulesCanBeBookedAtTheSameTime`.

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
  single-user CLI does not need yet. (The companion project `sistema-bancario-java` uses SQLite
  because a transfer must change two accounts atomically.)
- **Verified by:** `JsonClinicDataStoreTest`.

## 9. Validate data loaded from disk

- **Decision:** `ClinicSnapshotValidator` checks every loaded record before the repository
  receives it.
- **Why:** Gson creates objects without calling the services, so loaded data skips every business
  rule. An unknown enum name becomes `null` silently. Without this check, an edited file loaded
  "successfully" and the daily report later crashed with a `NullPointerException`.
- **Alternative:** validating inside model constructors. Gson bypasses constructors, so it would
  not help here.
- **Verified by:** `InvalidClinicDataTest`. With the validation call removed, 14 of its 15 tests
  fail; the one that still passes is the control case that loads an unchanged valid file.

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

## 12. Keep an update in memory when saving fails

- **Decision:** if saving an ordinary update fails (registering, editing, scheduling, changing a
  status), the in-memory change is kept, the user is warned, and the next successful save
  includes it.
- **Why:** an undo for every kind of update would add code to every operation. For a local
  single-user tool, clear warnings were judged sufficient for these changes. Deletion and
  anonymization are different; see decision 17.
- **Verified by:** `OwnerAndAnimalServiceTest.failedSaveIsReportedAndTheNextSuccessfulSaveIncludesTheChange`,
  `VetCareCliTest.failedSaveIsReportedAndRememberedAtExit`.

## 13. One generic schedule resource

- **Decision:** a `ScheduleResource` has only an ID and a name. It can represent a veterinarian, a
  room, or equipment. Each appointment references exactly one.
- **Why:** the conflict rule is the same for all of them ("one appointment at a time"). Separate
  `Veterinarian` and `Room` classes would duplicate it without adding behavior.
- **Alternative:** an appointment that needs a veterinarian **and** a room. More realistic, but each
  booking would need two conflict checks and a way to search for a time when both are free. Left
  as a documented limitation.
- **Other rules:** names are unique ignoring case; a schedule referenced by any appointment cannot
  be deleted; with only one schedule, the CLI selects it automatically.
- **Verified by:** `ScheduleResourceServiceTest`, `AppointmentServiceTest.schedulingRequiresAnExistingSchedule`,
  `rescheduleCanMoveToAnotherScheduleAndChecksThatSchedule`.

## 14. Status transitions defined in the enum

- **Decision:** `AppointmentStatus` declares, for each value, whether it occupies the schedule,
  whether it is final, and which transitions are allowed (`canTransitionTo`). Only `SCHEDULED` can
  change; `COMPLETED`, `NO_SHOW` and `CANCELLED` are final.
- **Why:** the rules sit next to the values they describe, so the conflict check, the services and
  the loaded-data validator cannot disagree. A final status protects history: a completed visit
  cannot be turned into a cancellation afterwards.
- **Why `COMPLETED` occupies the slot and `NO_SHOW` does not:** a completed appointment really used
  the time; a no-show left it free, so another patient may have been attended then.
- **Alternative:** a `switch` in `AppointmentService`. Works, but other classes would need their
  own copies of the same rules.
- **Verified by:** `AppointmentStatusTest` (every pair of statuses),
  `AppointmentServiceTest.finalStatusesCannotChangeOrBeRescheduled`,
  `ScheduleConflictServiceTest.completedKeepsTheSlotButNoShowFreesIt`.

## 15. Attendance is recorded by a person, never automatically

- **Decision:** `COMPLETED` and `NO_SHOW` are set only through menu option 20, and only once the
  appointment start time has passed.
- **Why:** time passing does not prove the patient was attended. Marking past appointments as
  completed automatically would create false history and wrong reports. Recording an outcome
  before the start is impossible to know, so it is rejected.
- **Verified by:** `AppointmentServiceTest.attendanceCannotBeRecordedBeforeTheAppointmentStarts`,
  `attendanceCanBeRecordedOnceTheAppointmentHasStarted`,
  `VetCareCliTest.attendanceCannotBeRecordedForAFutureAppointment`.

## 16. Delete or anonymize, never edit references by hand

- **Decision:** three operations, all in `PrivacyService`:
  - delete an owner with all their animals and those animals' appointments;
  - delete one animal with its appointments;
  - anonymize an owner: replace name, phone and email, rename their animals to
    `Anonymized animal`, and clear all notes, while keeping dates, types, clinical data and
    outcomes.
- **Why:** deleting only the owner would leave animals pointing to a missing owner, and the next
  load would fail validation. Removing children together with the parent keeps every reference
  valid. Anonymization exists because a clinic may want the statistics without the person.
- **Safeguards:**
  - refused while any related appointment is `SCHEDULED`, so a future visit is not lost silently;
    the CLI checks this before asking for confirmation, and the operation checks it again;
  - the CLI shows the number of affected records and requires typing `DELETE` or `ANONYMIZE`; a
    stray `y` or Enter cancels;
  - anonymized owners cannot be edited or receive new animals.
- **What is kept after anonymization:** species, birth date, appointment type, condition, signs,
  priority and status. Together with dates, these could still identify an animal in a small
  community; this is documented as a limitation.
- **Not claimed:** compliance with the LGPD or any other law. There are no retention rules, no
  access control, and older backup files are not changed.
- **Verified by:** `PrivacyServiceTest`, `VetCareCliTest.deletingAnOwnerRequiresTheExactConfirmationWord`,
  `ownerWithScheduledAppointmentsIsRefusedBeforeAskingForConfirmation`, `anonymizingAnOwnerKeepsTheRecordWithoutPersonalData`.

## 17. Undo deletion and anonymization when saving fails

- **Decision:** these operations take a snapshot before changing anything and call
  `PersistenceCoordinator.saveOrRestore(before)`. If saving fails, the repository is reloaded from
  the snapshot and a `ChangeUndoneException` reaches the CLI, which says that nothing was
  modified.
- **Why:** for personal data, "deleted on screen but still on disk" would mislead the person who
  asked for the deletion. Unlike decision 12, the undo is cheap here because the snapshot already
  exists.
- **Verified by:** `PrivacyServiceTest.failedSaveRestoresEverythingThatWasDeletedOrAnonymized`,
  `VetCareCliTest.failedDeletionIsUndoneAndNotReportedAsAChangeInMemory`. The CLI test was written
  first and failed against the previous message ("The change may exist only in memory").

## 18. Versioned file format with migration and backup

- **Decision:** the JSON file carries `schemaVersion` (currently 2). Files without it are version 1.
  Loading a version 1 file converts it in memory (one `Main schedule` for every existing
  appointment), validates the result, copies the original file to a backup, and only then saves the
  new format. Newer versions are refused without changes.
- **Why:**
  - converting first and copying second means an invalid old file causes no writes at all;
  - the backup is never overwritten (`-2`, `-3`... are added), so running the migration twice on
    different copies cannot destroy an earlier backup;
  - refusing newer files prevents an old version from discarding fields it does not know.
- **Alternative:** keep reading version 1 forever without rewriting it. Simpler, but every future
  change would need to support all old formats at once.
- **Verified by:** `ClinicDataMigrationTest`, which loads a file produced by the previous release
  (commit `02c2e8d`) rather than a hand-written imitation.
