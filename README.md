# VetCare Scheduler

[![Java CI](https://github.com/Amandadamata/vetcare-scheduler-java/actions/workflows/ci.yml/badge.svg)](https://github.com/Amandadamata/vetcare-scheduler-java/actions/workflows/ci.yml)

**English** · [Português (Brasil)](README.pt-BR.md)

VetCare Scheduler is a command-line Java application for veterinary appointments. It keeps a
separate schedule for each veterinarian or room, calculates a simple triage priority, prevents
overlapping bookings on the same schedule, tracks each appointment until it is completed,
cancelled, or missed, and lets the clinic delete or anonymize personal data.

It is a personal learning project built to turn Java and software-development fundamentals into a
working application with business rules, file persistence, data migration, and automated tests.

## Features

- Register and update owners and animals; search animals by partial name
- Schedules (resources): one per veterinarian, room, or other resource that attends one appointment at a time
- Schedule, update, reschedule (including to another schedule), list, filter, and cancel appointments
- Conflict detection per schedule: two schedules can book the same time; one schedule cannot
- Appointment life cycle: scheduled → completed, no-show, or cancelled, with validated transitions
- `EMERGENCY`, `URGENT`, or `STANDARD` triage priority calculated from condition and clinical signs
- Daily triage queue (`PriorityQueue`) and daily summary by status, priority, and type
- Delete an owner (with animals and appointments) or one animal; or anonymize an owner while keeping the appointment history
- Local JSON storage with atomic writes, validation on load, and automatic migration of older files with a backup

## Business logic

### Schedules and conflicts

Each appointment belongs to exactly one **schedule** (`ScheduleResource`): "Dr. Ana", "Room 2", and
so on. Two appointments conflict when they are on the **same schedule** and their time intervals
overlap:

```java
candidateStart.isBefore(existingEnd) && existingStart.isBefore(candidateEnd)
```

- Dr. Ana at 14:00 does not block Dr. Bruno at 14:00.
- Back-to-back appointments (10:00–10:30 and 10:30–11:00) are allowed.
- Only appointments that still occupy the slot count: **scheduled** and **completed**.
  **Cancelled** and **no-show** appointments free it.

A single generic resource type covers veterinarians and rooms. Modeling both at once (a vet *and* a
room per appointment) would require checking two kinds of conflict, which the current requirements
do not need.

### Appointment life cycle

```text
              ┌──> COMPLETED   (attended; keeps the slot occupied)
SCHEDULED ────┼──> NO_SHOW     (patient did not come; frees the slot)
              └──> CANCELLED   (frees the slot)
```

- Only scheduled appointments can be updated, rescheduled, or change status. The other three
  statuses are final.
- Completed and no-show can only be recorded **after the appointment has started**. Nothing is
  marked as completed automatically: the passing of time does not prove that a visit happened.

### Triage priority

1. `EMERGENCY`: a critical condition, breathing difficulty, uncontrolled bleeding, unconsciousness, or seizure.
2. `URGENT`: a concerning condition, severe pain, repeated vomiting, or inability to stand.
3. `STANDARD`: no emergency or urgent rule applies.

The daily queue orders scheduled appointments by priority, then registration time (arrival order),
then start time.

> These rules are intentionally simplified for a software-learning project. They are not a
> veterinary protocol or medical guidance.

### Deleting and anonymizing personal data

| Operation | What happens | What is kept |
|---|---|---|
| Delete owner | The owner, their animals, and all appointments of those animals are removed | Nothing |
| Delete animal | The animal and its appointments are removed | The owner and other animals |
| Anonymize owner | Owner name, phone, and email are replaced; animal names and every free-text note are cleared | Appointment dates, types, condition, clinical signs, priorities, statuses; animal species and birth date |

- All three are refused while any related appointment is still **scheduled**: cancel it or record
  its outcome first.
- Records are removed or replaced as a whole, so no animal points to a missing owner and no
  appointment points to a missing animal.
- The CLI shows what will be affected and requires typing `DELETE` or `ANONYMIZE`.
- If saving fails, the change is undone in memory.

These operations help a clinic act on a deletion request. They do **not** by themselves make the
application compliant with the LGPD or any other law: there are no retention policies, no access
control, and older backups (see below) still contain the original data.

### Persistence and older files

Data is stored in `data/vetcare-data.json` with Gson. The application writes a temporary file and
then replaces the main file, so an interrupted save cannot corrupt it. On startup, every record is
validated: unique IDs, existing owners, animals, and schedules, known enum values, and the same field
rules used for new input.

Files now carry `"schemaVersion": 2`. When the application finds a file from the previous version
(without schedules), it:

1. converts it in memory, putting existing appointments in a schedule named `Main schedule`;
2. validates the result; if it is invalid, nothing is written;
3. copies the original file to `vetcare-data.json.v1-backup.json` (never overwriting an existing backup);
4. saves the converted data and tells the user where the backup is.

Files written by a newer version are refused without changes. Local JSON data and backups are
ignored by Git.

## Programming concepts demonstrated

- Java 21, object-oriented design, encapsulation, and small focused classes
- Layered architecture (`cli`, `service`, `repository`, `persistence`, `model`) and constructor injection
- Programming to an interface (`ClinicDataStore`)
- Collections: `Map`, `Set`, `EnumSet`, `EnumMap`, and `PriorityQueue`
- Comparators, streams, lambdas, and `Optional`
- Date/time interval logic with `java.time`
- A state machine expressed with an enum
- Referential integrity, data migration with backup, and validation of loaded data
- Automated tests with JUnit 6; Maven and GitHub Actions

## Project structure

```text
src/main/java/com/amandadamata/vetcare/
├── cli/          # Console menu and input handling
├── exception/    # Domain and persistence errors
├── model/        # Owner, Animal, Appointment, ScheduleResource, enums
├── persistence/  # JSON loading/saving, validation, migration
├── repository/   # In-memory collections
├── service/      # Scheduling, triage, conflicts, reports, schedules, privacy
├── util/         # Shared validation helpers
└── Main.java     # Application composition and startup

src/test/java/        # Unit, integration, migration, and CLI tests
src/test/resources/   # A data file written by the previous release, for migration tests
docs/                 # Architecture, design decisions, testing, walkthrough
```

## Requirements

- Java 21 or newer
- Internet access on the first build, so the Maven Wrapper can download Maven and dependencies

## How to run

```bash
git clone https://github.com/Amandadamata/vetcare-scheduler-java.git
cd vetcare-scheduler-java
./mvnw clean package
java -jar target/vetcare-scheduler.jar
```

On Windows, replace `./mvnw` with `mvnw.cmd`. To keep demo data elsewhere:

```bash
VETCARE_DATA_FILE=/tmp/vetcare-demo.json java -jar target/vetcare-scheduler.jar
```

Before scheduling, register at least one schedule (menu option 17). With a single schedule, it is
selected automatically.

## Tests

```bash
./mvnw test
```

The suite covers triage rules, interval boundaries, conflicts per schedule, simultaneous bookings on
different schedules, every allowed and forbidden status transition, attendance before the start time,
deletion and anonymization without broken references, restoring memory after a failed save, invalid
data files, migration of a file written by the previous release, and complete menu sessions. See
[docs/TESTING.md](docs/TESTING.md).

GitHub Actions runs the full Maven verification on Java 21 and 25 for every push and pull request.

## Current limitations

- Each appointment uses one resource; booking a veterinarian **and** a room together is not modeled.
- No retention periods, access control, or encryption; migration backups keep the original data until deleted manually.
- Anonymization keeps animal species and birth date for statistics.
- One process at a time; JSON offers no concurrent access or database transactions.
- If an update (not a deletion or anonymization) fails to save, the change stays in memory until the next successful save; the CLI warns the user.
- No authentication, network API, or graphical interface.
- Triage rules are educational examples and must not be used for clinical decisions.

## What I learned

This project moved me from individual Java exercises to a complete application flow: deciding where
each rule belongs, representing time intervals correctly, using collections for a real purpose,
modeling a life cycle with explicit transitions, keeping references valid when data is removed, and
evolving a stored file format without losing existing data.

## Documentation

- [Architecture](docs/ARCHITECTURE.md): layer, domain, and sequence diagrams; status machine; deletion; migration
- [Design decisions](docs/DESIGN_DECISIONS.md): what was chosen, why, alternatives, and the tests that verify it
- [Testing](docs/TESTING.md): how to run tests and what each class verifies
- [Project walkthrough](docs/PROJECT_WALKTHROUGH.md): class-by-class explanation and interview questions

## License

This project is available under the [MIT License](LICENSE).
