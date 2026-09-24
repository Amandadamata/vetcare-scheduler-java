# VetCare Scheduler: Project Walkthrough

This guide explains the project for someone at the beginning of a Systems Analysis and Development degree. It focuses on what the code does, why the classes exist, and what Amanda should be able to explain in an interview.

## 1. Application flow

When the application starts, `Main` performs five steps:

1. Chooses the JSON data file.
2. Creates the repository and data store.
3. Loads previously saved data into memory.
4. Creates the services that contain the application rules.
5. Starts the command-line menu.

The normal flow of an action is:

```text
CLI input → service validation/rule → repository update → JSON save → CLI result
```

The CLI never calculates triage priority or checks time overlap itself. This matters because presentation code changes more often than a business rule and is harder to test through console input.

## 2. Main classes and responsibilities

### Model

- `Owner`: identifies the person responsible for one or more animals.
- `Animal`: stores the owner relationship, name, species, birth date, and notes.
- `Appointment`: stores scheduling and clinical inputs plus the calculated priority and status.
- Enums such as `Species`, `AppointmentType`, and `TriagePriority`: limit values to a known set instead of accepting inconsistent strings.

Fields are private and changed through methods such as `update`, `reschedule`, and `cancel`. This is encapsulation: an object controls the operations that can change its state.

### Repository

`ClinicRepository` keeps owners, animals, and appointments in `Map<UUID, ...>` collections while the application is running.

A `Map` is useful because an entity can be retrieved by ID without scanning a complete list. `LinkedHashMap` also gives predictable insertion order when a snapshot is saved.

### Services

- `OwnerService`: validates, registers, updates, and lists owners.
- `AnimalService`: validates owner relationships and animal data, lists animals, and performs partial-name search.
- `AppointmentService`: schedules, updates, filters, reschedules, cancels, and builds the triage queue.
- `TriageService`: contains only priority rules.
- `ScheduleConflictService`: contains only interval-overlap logic.
- `ReportService`: calculates daily totals and grouped counts.

The small rule services are easy to test directly. The larger `AppointmentService` coordinates those rules rather than duplicating them.

### Persistence

- `ClinicSnapshot`: one serializable view of all three collections.
- `ClinicDataStore`: an interface for loading and saving a snapshot.
- `JsonClinicDataStore`: the JSON implementation of that interface.
- `PersistenceCoordinator`: connects the repository snapshot to the data store.

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

`AppointmentService.triageQueue` selects active appointments for one date and inserts them into a `PriorityQueue` with this comparator:

1. priority rank;
2. registration time;
3. scheduled time;
4. ID as a final stable tie-breaker.

`EMERGENCY` has rank `0`, so it comes before `URGENT` (`1`) and `STANDARD` (`2`).

One subtle Java detail: iterating a `PriorityQueue` does **not** guarantee priority order. The code repeatedly calls `poll()` on a copied queue to produce the ordered result.

## 5. Schedule-conflict logic

An appointment is a half-open interval: its start belongs to the interval, but its end can be the next appointment's start.

Two intervals overlap when both statements are true:

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
| cancelled 10:00–10:30 | 10:00–10:30 | allowed |

During rescheduling, the service ignores the appointment being moved. Otherwise it would always conflict with its old interval.

## 6. Validation and error handling

`Validators` centralizes reusable checks:

- required and maximum-length text;
- email shape;
- reasonable phone digit count;
- birth date not in the future;
- appointment strictly in the future;
- duration from 15 to 180 minutes.

The services also validate relationships: an animal needs an existing owner and an appointment needs an existing animal.

Custom exceptions describe categories of expected failure:

- `ValidationException`: input violates a rule;
- `NotFoundException`: an ID or relationship does not exist;
- `ScheduleConflictException`: an active time interval overlaps;
- `DataPersistenceException`: JSON could not be safely read or written.

The CLI catches these errors and prints a short message rather than exposing a stack trace to the user.

## 7. How data is saved

After a successful change, the service asks `PersistenceCoordinator` to save a snapshot. Gson converts the snapshot to readable JSON.

`JsonClinicDataStore` writes to a `.tmp` file first. Only after the write succeeds does it replace the real data file. If the existing JSON is invalid, loading fails with a clear message and the application does not silently overwrite it.

The JSON file is excluded by `.gitignore`. That prevents local names, contact information, and test data from being published by mistake.

## 8. How the tests work

The tests use JUnit 5 and real project objects instead of a mocking library.

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
- unit tests and temporary test files.

## 10. Decisions and trade-offs

### Why a CLI instead of Swing?

The project goal is to demonstrate logic, object-oriented design, persistence, and tests. A graphical interface would add event-handling and layout work without improving those rules. A CLI is smaller and easier to explain.

### Why JSON instead of SQL?

JSON keeps persistence visible and understandable at this stage. SQL is an important next step, but adding a database now would increase setup and hide the core rules behind infrastructure.

### Why one schedule?

One room/team makes the overlap rule clear. Multiple veterinarians or rooms would require a `Resource` model and conflicts scoped to that resource; that is a possible future extension.

### Why cancel instead of delete?

Cancellation preserves history and frees the time slot. Physical deletion would remove useful information from summaries and make an accidental action irreversible.

## 11. Interview questions Amanda should be ready to answer

### 1. Why did you choose this project?

I wanted a real scheduling problem rather than an isolated exercise. The veterinary domain was familiar to me, so I could focus on translating rules into Java while keeping the project centered on software development.

### 2. Where is the main business logic?

Scheduling coordination is in `AppointmentService`. Priority rules are isolated in `TriageService`, overlap rules in `ScheduleConflictService`, and reports in `ReportService`. The CLI only gathers input and displays results.

### 3. How is triage priority calculated?

The system checks emergency conditions and signs first, then urgent conditions and signs, and otherwise returns standard. Emergency is checked first so a weaker rule cannot lower the result.

### 4. Why did you use a `PriorityQueue`?

The triage view needs the next appointment according to multiple priority rules. A `PriorityQueue` retrieves the highest-priority element through its comparator. I poll a copy because direct iteration is not guaranteed to be ordered.

### 5. How do you detect a schedule conflict?

I treat each appointment as a time interval. Two intervals overlap when each starts before the other ends. That permits adjacent appointments but rejects partial and complete overlap.

### 6. Why do cancelled appointments not create conflicts?

They are retained for history and reporting, but the time resource is available again. The conflict service filters for active `SCHEDULED` appointments.

### 7. How does rescheduling avoid conflicting with itself?

The conflict check receives the appointment ID to ignore. It compares the candidate interval with every other active appointment.

### 8. How is encapsulation used?

Model fields are private. State changes happen through meaningful methods such as `reschedule`, `updateClinicalDetails`, and `cancel`, instead of letting callers modify fields directly.

### 9. Why use UUIDs?

UUIDs give each entity a stable ID without a database sequence. The CLI still shows numbered choices so the user does not need to type a UUID.

### 10. How do the data survive after the program closes?

After each successful change, a repository snapshot is converted to JSON. Startup loads that file back into the in-memory maps.

### 11. What happens if the JSON is corrupted?

Loading throws a `DataPersistenceException` with the file location. The application does not replace the invalid file automatically, so the original data remains available for inspection.

### 12. Why inject `Clock`?

Rules about future dates depend on the current time. A fixed clock makes those tests repeatable on any day and in any environment.

### 13. What is tested most carefully?

The strongest business rules: priority precedence, interval boundaries, validation, ordering, filtering, cancellation, reports, and saving/loading dates and enums.

### 14. What would you improve next?

I would first add a SQL persistence implementation, then consider multiple clinic resources. After that, a REST API with Spring Boot would be a useful separate learning step.

### 15. What are the current limitations?

It is a local single-user CLI with one schedule and simplified educational triage rules. It has no authentication, HTTP API, concurrent access, or real clinical protocol.

