# VetCare Scheduler: Project Walkthrough

This guide explains the project for someone at the beginning of a Systems Analysis and Development degree. It focuses on what the code does, why the classes exist, and what Amanda should be able to explain in an interview.

## 1. Application flow

When the application starts, `Main` performs six steps:

1. Chooses the JSON data file.
2. Creates the repository and data store.
3. Loads previously saved data into memory, migrating a file from the previous version if needed.
4. Tells the user where the backup is, if a migration happened.
5. Creates the services that contain the application rules.
6. Starts the command-line menu.

The normal flow of an action is:

```text
CLI input → service validation/rule → repository update → JSON save → CLI result
```

The CLI never calculates triage priority or checks time overlap itself. This matters because presentation code changes more often than a business rule and is harder to test through console input.

## 2. Main classes and responsibilities

### Model

- `ScheduleResource`: a veterinarian, room, or anything else that attends one appointment at a time.
- `Owner`: identifies the person responsible for one or more animals; can be anonymized.
- `Animal`: stores the owner relationship, name, species, birth date, and notes.
- `Appointment`: stores the schedule, scheduling and clinical inputs, the calculated priority, and the status.
- Enums such as `Species`, `AppointmentType`, and `TriagePriority`: limit values to a known set instead of accepting inconsistent strings.
- `AppointmentStatus`: besides limiting values, it knows which status changes are allowed and which statuses occupy the schedule.

Fields are private and changed through methods such as `update`, `reschedule`, and `changeStatus`. This is encapsulation: an object controls the operations that can change its state. Anonymization does not edit fields one by one; it creates a complete anonymized copy (`anonymizedCopy()`), which is harder to get half-right.

### Repository

`ClinicRepository` keeps schedules, owners, animals, and appointments in `Map<UUID, ...>` collections while the application is running.

A `Map` is useful because an entity can be retrieved by ID without scanning a complete list. `LinkedHashMap` also gives predictable insertion order when a snapshot is saved.

### Services

- `OwnerService`: validates, registers, updates, and lists owners.
- `AnimalService`: validates owner relationships and animal data, lists animals, and performs partial-name search.
- `AppointmentService`: schedules, updates, filters, reschedules, cancels, records attendance, and builds the triage queue.
- `ScheduleResourceService`: registers, lists, and deletes unused schedules.
- `PrivacyService`: deletes or anonymizes an owner, or deletes an animal, keeping references valid.
- `TriageService`: contains only priority rules.
- `ScheduleConflictService`: contains only interval-overlap logic.
- `ReportService`: calculates daily totals and grouped counts.

The small rule services are easy to test directly. The larger `AppointmentService` coordinates those rules rather than duplicating them.

### Persistence

- `ClinicSnapshot`: one serializable view of all four collections, plus `schemaVersion`.
- `ClinicDataStore`: an interface for loading and saving a snapshot.
- `JsonClinicDataStore`: the JSON implementation of that interface, including the migration of older files.
- `ClinicDataMigration`: converts a version 1 snapshot (no schedules) into the current version.
- `ClinicSnapshotValidator`: re-checks every rule on data loaded from disk.
- `PersistenceCoordinator`: connects the repository snapshot to the data store; `saveOrRestore` undoes a change if saving fails.

Depending on an interface makes the storage boundary explicit. A future version could add another implementation without moving scheduling rules into database code.

## 3. Triage logic

The user provides two kinds of information:

- the general condition: `STABLE`, `CONCERNING`, or `CRITICAL`;
- zero or more observed clinical signs.

`TriageService.calculate` checks the strongest rule first:

```text
critical condition or emergency sign → EMERGENCY
otherwise, concerning condition or urgent sign → URGENT
otherwise → STANDARD
```

Checking emergency first is important. An appointment with both severe pain and breathing difficulty must remain `EMERGENCY`; a later urgent rule must not lower it.

These are simplified software rules, not a real clinical protocol.

## 4. Triage queue and `PriorityQueue`

`AppointmentService.triageQueue` selects the scheduled appointments for one date and inserts them into a `PriorityQueue` with this comparator:

1. priority rank;
2. registration time;
3. scheduled time;
4. ID as a final stable tie-breaker.

`EMERGENCY` has rank `0`, so it comes before `URGENT` (`1`) and `STANDARD` (`2`).

One subtle Java detail: iterating a `PriorityQueue` does **not** guarantee priority order. The code repeatedly calls `poll()` to produce the ordered result.

Cost: inserting *n* appointments and polling all of them is O(n log n), the same as sorting a list with the comparator. The `PriorityQueue` is a modeling choice ("who is next?"), not a performance gain for this one-off listing. It would become more efficient than re-sorting if the queue were kept alive and appointments were added and taken one at a time.

## 5. Schedule-conflict logic

An appointment is a half-open interval: its start belongs to the interval, but its end can be the next appointment's start.

Conflicts are checked only among appointments **on the same schedule** that still occupy it (`SCHEDULED` or `COMPLETED`). Among those, two intervals overlap when both statements are true:

```java
firstStart.isBefore(secondEnd)
secondStart.isBefore(firstEnd)
```

Examples:

| Existing | Candidate | Result |
|---|---|---|
| 10:00–10:30 | 10:30–11:00 | allowed |
| 10:00–10:30 | 10:15–10:45 | conflict |
| 10:00–11:00 | 10:15–10:30 | conflict |
| cancelled or no-show 10:00–10:30 | 10:00–10:30 | allowed |
| completed 10:00–10:30 | 10:00–10:30 | conflict |
| Dr. Ana 10:00–10:30 | Dr. Bruno 10:00–10:30 | allowed |

During rescheduling, the service ignores the appointment being moved. Otherwise it would always conflict with its old interval.

## 6. Validation and error handling

`Validators` centralizes reusable checks:

- required and maximum-length text;
- email shape;
- reasonable phone digit count;
- birth date not in the future;
- appointment strictly in the future and at most two years ahead;
- duration from 15 to 180 minutes.

The services also validate relationships: an animal needs an existing owner, and an appointment needs an existing animal and an existing schedule.

Data loaded from disk is validated too. Gson builds objects through their private constructors without calling the services, so `ClinicSnapshotValidator` re-checks every loaded record with the same rules.

Custom exceptions describe categories of expected failure:

- `ValidationException`: input violates a rule, including a forbidden status change;
- `NotFoundException`: an ID or relationship does not exist;
- `ScheduleConflictException`: an active time interval overlaps;
- `DataPersistenceException`: JSON could not be safely read or written, or it contains invalid data;
- `ChangeUndoneException`: a kind of `DataPersistenceException` meaning the save failed and the change was already undone in memory.

The CLI catches these errors and prints a short message rather than exposing a stack trace to the user.

## 7. How data is saved

After a successful change, the service asks `PersistenceCoordinator` to save a snapshot. Gson converts the snapshot to readable JSON.

`JsonClinicDataStore` writes to a `.tmp` file first. Only after the write succeeds does it replace the real data file. If the existing file is not valid JSON, or is valid JSON with impossible data (for example an unknown appointment type or an appointment for a missing animal), loading fails with a clear message and the application does not silently overwrite it.

The JSON file is excluded by `.gitignore`. That prevents local names, contact information, and test data from being published by mistake.

The file has a `schemaVersion`. A version 1 file (written before schedules existed) is converted in memory, validated, copied to `vetcare-data.json.v1-backup.json`, and only then saved in the new format. If the old file is invalid, nothing is written. If a file comes from a newer version, it is refused untouched.

## 7.1. Appointment life cycle

```text
              ┌──> COMPLETED   (attended)
SCHEDULED ────┼──> NO_SHOW     (patient did not come)
              └──> CANCELLED
```

Only `SCHEDULED` can change. The three others are final, which protects history: a completed visit cannot later become "cancelled". `COMPLETED` and `NO_SHOW` can only be recorded after the start time, and never automatically, because the clock passing 10:30 does not prove the patient was seen.

## 7.2. Deleting and anonymizing

- Deleting an owner removes their animals and those animals' appointments too, children first. Deleting only the owner would leave animals pointing to nobody, and the next startup would reject the file.
- Anonymizing keeps the appointment history but replaces the owner's name, phone, and email, renames the animals, and clears all notes.
- Both are refused while a related appointment is still `SCHEDULED`.
- The CLI shows how many records are affected and requires typing `DELETE` or `ANONYMIZE`.
- If the save fails, memory is restored from a snapshot taken before the change, so the screen and the file never disagree about personal data.

These features support a deletion request; they do not make the system legally compliant by themselves.

## 8. How the tests work

The tests use JUnit 6 and real project objects instead of a mocking library.

- `Clock.fixed(...)` makes “now” deterministic.
- `@TempDir` gives persistence tests an isolated temporary folder.
- An in-memory repository exercises scheduling behavior without console input.
- Small service tests cover rule boundaries, such as appointments that touch but do not overlap.

This test style is appropriate for the project because the collaborators are small and inexpensive to create.

## 9. Java concepts used

- classes and objects;
- private fields and encapsulation;
- constructors and dependency injection;
- interfaces and implementation classes;
- enums and records;
- generics;
- `List`, `Map`, `Set`, `EnumMap`, and `PriorityQueue`;
- streams, predicates, and comparators;
- `LocalDate`, `LocalDateTime`, `Instant`, and `Clock`;
- checked I/O errors and custom runtime exceptions;
- file operations and JSON serialization;
- unit tests, parameterized tests, and temporary test files;
- a state machine written as an enum;
- data migration with a backup.

## 10. Decisions and trade-offs

### Why a CLI instead of Swing?

The project goal is to demonstrate logic, object-oriented design, persistence, and tests. A graphical interface would add event-handling and layout work without improving those rules. A CLI is smaller and easier to explain.

### Why JSON instead of SQL?

JSON keeps persistence visible and understandable at this stage. SQL is an important next step, but adding a database now would increase setup and hide the core rules behind infrastructure.

### Why one generic schedule type instead of `Veterinarian` and `Room`?

The rule is identical for both: one appointment at a time. A single `ScheduleResource` keeps one conflict check. Booking a veterinarian **and** a room for the same appointment would need two checks and is a documented limitation.

### Why cancel instead of delete for appointments?

Cancellation preserves history and frees the time slot. Physical deletion exists only for personal data (owner or animal), behind a typed confirmation, because removing records by mistake is irreversible.

## 11. Interview questions Amanda should be ready to answer

### 1. Why did you choose this project?

I wanted a real scheduling problem rather than an isolated exercise. The veterinary domain was familiar to me, so I could focus on translating rules into Java while keeping the project centered on software development.

### 2. Where is the main business logic?

Scheduling coordination is in `AppointmentService`. Priority rules are isolated in `TriageService`, overlap rules in `ScheduleConflictService`, and reports in `ReportService`. The CLI only gathers input and displays results.

### 3. How is triage priority calculated?

The system checks emergency conditions and signs first, then urgent conditions and signs, and otherwise returns standard. Emergency is checked first so a weaker rule cannot lower the result.

### 4. Why did you use a `PriorityQueue`?

The triage view needs the next appointment according to multiple priority rules. A `PriorityQueue` retrieves the highest-priority element through its comparator, and I poll it because direct iteration is not guaranteed to be ordered. For building the full list once, this costs the same as sorting (O(n log n)); I chose it because it models "who is next" and would pay off if the queue were kept and consumed incrementally.

### 5. How do you detect a schedule conflict?

I treat each appointment as a time interval. Two intervals overlap when each starts before the other ends. That permits adjacent appointments but rejects partial and complete overlap.

### 6. Why do cancelled appointments not create conflicts, but completed ones do?

Cancelled and no-show appointments are kept for history, but the time was free. A completed appointment really used the time. Each status declares this through `occupiesSchedule()`, and the conflict service filters by it and by schedule.

### 7. How does rescheduling avoid conflicting with itself?

The conflict check receives the appointment ID to ignore. It compares the candidate interval with every other active appointment.

### 8. How is encapsulation used?

Model fields are private. State changes happen through meaningful methods such as `reschedule`, `updateClinicalDetails`, and `changeStatus`, instead of letting callers modify fields directly.

### 9. Why use UUIDs?

UUIDs give each entity a stable ID without a database sequence. The CLI still shows numbered choices so the user does not need to type a UUID.

### 10. How do the data survive after the program closes?

After each successful change, a repository snapshot is converted to JSON. Startup loads that file back into the in-memory maps.

### 11. What happens if the JSON is corrupted?

There are two cases. If the file is not valid JSON, Gson fails and loading throws a `DataPersistenceException` with the file location. If it is valid JSON but contains impossible data, such as an unknown enum value (which Gson silently turns into `null`), a duration of 5000 minutes, or an appointment for an animal that does not exist, `ClinicSnapshotValidator` rejects it and the message names the record and the rule. In both cases the application does not start and does not change the file, so the original data remains available for inspection.

An earlier version only handled the first case; the second loaded silently and later crashed the daily report. The fix and its tests are in `ClinicSnapshotValidator` and `InvalidClinicDataTest`.

### 12. Why inject `Clock`?

Rules about future dates depend on the current time. A fixed clock makes those tests repeatable on any day and in any environment.

### 13. What is tested most carefully?

The strongest business rules: priority precedence, interval boundaries, conflicts per schedule, every status transition, attendance timing, deletion without broken references, undo after a failed save, migration of a real old file, validation, and saving/loading dates and enums.

### 14. What would you improve next?

Appointments that need two resources at once (veterinarian and room), retention rules for personal data, and a SQL implementation of `ClinicDataStore`. After that, a REST API with Spring Boot would be a useful separate learning step.

### 15. What are the current limitations?

It is a local single-user CLI with simplified educational triage rules. Each appointment uses one resource. It has no authentication, HTTP API, concurrent access, retention rules, or real clinical protocol, and migration backups keep old data until removed by hand.

### 16. How do you prevent overlapping appointments when there are several veterinarians?

Each appointment references a schedule. The conflict check first keeps only appointments on the same schedule that still occupy it, then applies the interval rule. Dr. Ana and Dr. Bruno can both have 14:00; Dr. Ana cannot have two.

### 17. How are status changes controlled?

`AppointmentStatus.canTransitionTo` allows only `SCHEDULED` to become `COMPLETED`, `NO_SHOW`, or `CANCELLED`. The other statuses are final. A parameterized test checks all 16 (from, to) pairs.

### 18. Why not mark past appointments as completed automatically?

Because the passing of time does not prove the patient came. An automatic rule would create false history. A person records the outcome, and only after the start time.

### 19. What happens to an owner's animals and appointments when the owner is deleted?

They are deleted together, children first, after a typed confirmation. The operation is refused while any of those appointments is still scheduled. Otherwise the file would contain animals with a missing owner, which the startup validation rejects.

### 20. What is the difference between deleting and anonymizing?

Deleting removes the records. Anonymizing keeps them for statistics but removes the name, contact details, animal names, and notes. Species, birth date, and clinical data stay, so the result is not guaranteed to be impossible to re-identify in every context.

### 21. What if saving fails in the middle of a deletion?

The service took a snapshot before changing anything. `saveOrRestore` reloads that snapshot and throws `ChangeUndoneException`; the CLI says nothing was modified. A test for this was written before the fix and failed against the old message, which wrongly said the change might exist only in memory.

### 22. How did you change the file format without breaking existing users?

The file has a `schemaVersion`. An old file is converted in memory, validated, backed up without overwriting any earlier backup, and then saved. The test uses a file actually written by the previous release, and the same check was repeated by hand with both JARs.

### 23. Does this make the system LGPD-compliant?

No. It provides deletion and anonymization tools, but compliance also depends on legal bases, retention periods, access control, backups, and processes that are outside this project.

