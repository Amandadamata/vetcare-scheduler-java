# Architecture

This document shows how VetCare Scheduler is organized and how data moves through it. For the
reasoning behind each choice, see [DESIGN_DECISIONS.md](DESIGN_DECISIONS.md). For a beginner-
friendly tour and interview questions, see [PROJECT_WALKTHROUGH.md](PROJECT_WALKTHROUGH.md).

## Layers

```mermaid
flowchart TD
    User([User at the terminal]) --> CLI
    subgraph cli [cli]
        CLI[VetCareCli] --> Input[ConsoleInput]
    end
    subgraph service [service]
        OS[OwnerService]
        AS[AnimalService]
        APS[AppointmentService]
        SRS[ScheduleResourceService]
        PS[PrivacyService]
        RS[ReportService]
        TS[TriageService]
        CS[ScheduleConflictService]
    end
    subgraph repository [repository]
        Repo[ClinicRepository<br/>in-memory maps]
    end
    subgraph persistence [persistence]
        PC[PersistenceCoordinator]
        Store[[ClinicDataStore]]
        Json[JsonClinicDataStore]
        Val[ClinicSnapshotValidator]
        Mig[ClinicDataMigration]
    end
    File[(data/vetcare-data.json)]

    CLI --> OS & AS & APS & SRS & PS & RS
    APS --> TS & CS
    OS & AS & APS & SRS & PS --> Repo
    OS & AS & APS & SRS & PS --> PC
    RS --> Repo
    PC --> Repo
    PC --> Store
    Json -. implements .-> Store
    Json --> Mig & Val
    Json <--> File
```

| Package | Responsibility | Depends on |
|---|---|---|
| `cli` | Menu, prompts, parsing typed values, confirmations, printing results | `service`, `model` |
| `service` | Business rules: validation, triage, conflicts, life cycle, schedules, privacy, reports | `repository`, `persistence`, `model`, `util` |
| `repository` | Holds schedules, owners, animals and appointments in `LinkedHashMap`s keyed by `UUID` | `model` |
| `persistence` | Converting the repository to and from JSON, migrating older files, validating loaded data | `repository`, `model`, Gson |
| `model` | Data classes and enums, including the status transition rules | nothing |
| `util` | `Validators`, shared field rules | `exception` |
| `exception` | Domain error types | nothing |

`Main` is the composition root: it is the only place where concrete classes are created and
connected.

## Domain model

```mermaid
classDiagram
    direction LR
    class ScheduleResource {
        UUID id
        String name
    }
    class Owner {
        UUID id
        String name
        String phone
        String email
        boolean anonymized
    }
    class Animal {
        UUID id
        UUID ownerId
        String name
        Species species
        LocalDate birthDate
        String notes
    }
    class Appointment {
        UUID id
        UUID animalId
        UUID resourceId
        AppointmentType type
        LocalDateTime startTime
        int durationMinutes
        PatientCondition condition
        Set~ClinicalSign~ clinicalSigns
        String notes
        TriagePriority priority
        AppointmentStatus status
        Instant createdAt
        +getEndTime() LocalDateTime
    }
    Owner "1" <-- "*" Animal : ownerId
    Animal "1" <-- "*" Appointment : animalId
    ScheduleResource "1" <-- "*" Appointment : resourceId
```

Relationships are stored as IDs, not object references. This keeps the JSON flat and makes each
record independently serializable; the services check that referenced IDs exist when data is
created, and `ClinicSnapshotValidator` checks them again when data is loaded.

A `ScheduleResource` is anything that can attend only one appointment at a time: a veterinarian, a
room, an operating table. Each appointment uses exactly one.

## Appointment status

```mermaid
stateDiagram-v2
    [*] --> SCHEDULED : schedule
    SCHEDULED --> SCHEDULED : update details / reschedule
    SCHEDULED --> CANCELLED : cancel (any time)
    SCHEDULED --> COMPLETED : record attendance (after start)
    SCHEDULED --> NO_SHOW : record attendance (after start)
    CANCELLED --> [*]
    COMPLETED --> [*]
    NO_SHOW --> [*]
```

| Status | Occupies the schedule | Final | Meaning |
|---|---|---|---|
| `SCHEDULED` | yes | no | Booked and not yet resolved |
| `COMPLETED` | yes | yes | The patient was attended; the slot was really used |
| `NO_SHOW` | no | yes | The patient did not come; the slot was free |
| `CANCELLED` | no | yes | The booking was withdrawn |

The table lives in the enum (`occupiesSchedule()`, `isFinal()`, `canTransitionTo()`), so the
conflict check, the services and the validator all read the same rules. `AppointmentService` adds
the time rule: attendance can only be recorded once `startTime` is not after "now".

## Startup and file versions

1. `Main` reads `VETCARE_DATA_FILE` (or uses `data/vetcare-data.json`).
2. `PersistenceCoordinator.load()` asks `JsonClinicDataStore` for a `ClinicSnapshot`.
3. `JsonClinicDataStore` parses the JSON with Gson and checks `schemaVersion`:

```mermaid
flowchart TD
    Read[Parse JSON] --> Blank{Missing or blank?}
    Blank -- yes --> Empty[Empty snapshot]
    Blank -- no --> Version{schemaVersion}
    Version -- "> 2" --> Refuse[Refuse: written by a newer version<br/>file untouched]
    Version -- "2" --> Validate[ClinicSnapshotValidator]
    Version -- "< 2 or absent" --> Convert[ClinicDataMigration.toCurrentVersion<br/>in memory]
    Convert --> ValidateOld[ClinicSnapshotValidator]
    ValidateOld -- invalid --> Fail[DataPersistenceException<br/>nothing written]
    ValidateOld -- valid --> Backup[Copy original to<br/>vetcare-data.json.v1-backup.json<br/>or -2, -3... if it exists]
    Backup --> Save[Save converted file<br/>temp file + atomic move]
    Save --> Ready[Snapshot ready]
    Validate -- invalid --> Fail
    Validate -- valid --> Ready
```

4. The snapshot is loaded into `ClinicRepository`.
5. If a migration happened, `Main` prints where the backup was written.
6. Services and the CLI are created with the same repository, persistence coordinator and `Clock`.

The migration creates one schedule named `Main schedule` and assigns every existing appointment to
it, which is exactly how version 1 behaved (a single shared schedule). The migration test uses a
file written by the previous release, not one written by hand.

## Scheduling an appointment

```mermaid
sequenceDiagram
    actor User
    participant CLI as VetCareCli
    participant APS as AppointmentService
    participant CS as ScheduleConflictService
    participant TS as TriageService
    participant Repo as ClinicRepository
    participant PC as PersistenceCoordinator

    User->>CLI: option 3, animal, schedule, type, start, duration, condition, signs
    CLI->>APS: schedule(animalId, resourceId, ...)
    APS->>APS: validate animal, schedule, type, condition,<br/>start (future, within 2 years), duration
    APS->>CS: ensureAvailable(all appointments, resourceId, start, duration, null)
    alt overlap with an appointment on the same schedule that occupies it
        CS-->>CLI: ScheduleConflictException
        CLI-->>User: Could not complete the operation: ...
    else free slot on this schedule
        APS->>TS: calculate(condition, signs)
        TS-->>APS: TriagePriority
        APS->>Repo: saveAppointment
        APS->>PC: save()
        PC->>PC: snapshot → JsonClinicDataStore.save<br/>(temp file, then atomic move)
        CLI-->>User: Appointment scheduled with <priority>
    end
```

Every changing operation follows the same order: **validate → change memory → save the whole
snapshot**. Validation never happens after a change, so a rejected operation leaves memory and
the file as they were.

## Deleting and anonymizing

```mermaid
sequenceDiagram
    actor User
    participant CLI as VetCareCli
    participant PS as PrivacyService
    participant Repo as ClinicRepository
    participant PC as PersistenceCoordinator

    User->>CLI: option 21 (delete owner)
    CLI->>PS: ensureOwnerCanBeDeleted(id)
    Note over CLI,PS: refused here if any related appointment is SCHEDULED,<br/>before asking for confirmation
    CLI->>PS: impactOfOwner(id)
    PS-->>CLI: Impact(animals, appointments)
    CLI-->>User: "This permanently deletes the owner, 2 animal(s) and 5 appointment(s)."<br/>"Type DELETE to confirm, or press Enter to cancel"
    User->>CLI: DELETE
    CLI->>PS: deleteOwnerAndRecords(id)
    PS->>PS: check the same rules again
    PS->>Repo: before = snapshot()
    PS->>Repo: delete appointments, then animals, then the owner
    PS->>PC: saveOrRestore(before)
    alt save fails
        PC->>Repo: load(before)
        PC-->>CLI: ChangeUndoneException
        CLI-->>User: The change was undone, so nothing was modified.
    else saved
        CLI-->>User: Owner and related records deleted
    end
```

- Children are removed before their parent, and always together, so no stored record points to a
  missing one.
- Anonymization replaces whole records with copies (`Owner.anonymizedCopy()`,
  `Animal.anonymizedCopy()`, `Appointment.withoutNotes()`) instead of editing fields one by one.
- Deletion and anonymization use `saveOrRestore`: if the file cannot be written, memory goes back
  to the snapshot taken before the change. For personal data, "deleted on screen but still on
  disk" (or the reverse) would be misleading.
- `ScheduleResourceService.delete` uses the same mechanism and refuses schedules that any
  appointment still references.

## Error handling

| Exception | Meaning | Where it is handled |
|---|---|---|
| `ValidationException` | Input breaks a rule (blank name, past date, forbidden status change, scheduled appointments blocking a deletion) | CLI prints it; menu continues |
| `NotFoundException` | An ID does not exist | CLI prints it; menu continues |
| `ScheduleConflictException` | Time slot overlaps an appointment on the same schedule | CLI prints it; menu continues |
| `DataPersistenceException` on load | File unreadable, invalid, from a newer version, or migration not possible | `Main` prints it and does not start |
| `DataPersistenceException` on save (updates) | File could not be written | CLI warns that the change is only in memory and repeats the warning at exit |
| `ChangeUndoneException` (a `DataPersistenceException`) | Save failed during deletion, anonymization or schedule removal; memory was already restored | CLI says the change was undone; no warning at exit, because memory and file agree |
| `ConsoleInput.InputClosedException` | Input stream ended | CLI ends the session |

All domain exceptions are unchecked (`RuntimeException`): they travel from the service layer to
the CLI without every method in between declaring them.

## Saving

`JsonClinicDataStore.save` writes the full snapshot to `vetcare-data.json.tmp`, then moves it over
the real file with `ATOMIC_MOVE` (falling back to a normal replace when the file system does not
support atomic moves). A crash during writing leaves the previous file intact.

## Data that stays outside the main file

| File | When it exists | Contains |
|---|---|---|
| `vetcare-data.json.tmp` | Only if the process stops in the middle of a save | A full snapshot |
| `vetcare-data.json.v1-backup.json` (or `-2`, `-3`...) | After migrating a version 1 file | The original version 1 data |

Deleting or anonymizing a person changes only the main file. These other files are not touched by
the application and must be removed manually if a deletion request has to cover them. In the default
`data/` folder, `.gitignore` excludes all of them (`data/*.json`, `data/*.tmp`).
