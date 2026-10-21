# VetCare Scheduler

[![Java CI](https://github.com/Amandadamata/vetcare-scheduler-java/actions/workflows/ci.yml/badge.svg)](https://github.com/Amandadamata/vetcare-scheduler-java/actions/workflows/ci.yml)

**English** · [Português (Brasil)](README.pt-BR.md)

VetCare Scheduler is a command-line Java application for managing veterinary appointments, calculating a simple triage priority, and preventing schedule conflicts.

It is a personal learning project built to turn Java and software-development fundamentals into a working application with business rules, file persistence, and automated tests.

## Features

- Register and update owners and animals
- Search animals by partial name
- Schedule, update, reschedule, list, filter, and cancel appointments (up to two years ahead)
- Detect overlapping appointments in a single-clinic schedule
- Calculate `EMERGENCY`, `URGENT`, or `STANDARD` triage priority
- Order a daily triage queue using `PriorityQueue`
- Produce a daily summary by status, priority, and appointment type
- Save data locally as JSON and load it again on the next run, rejecting damaged or edited files
- Validate user input and show clear domain errors

## Business logic

### Triage priority

The application calculates priority instead of asking the user to choose it directly:

1. `EMERGENCY`: a critical condition, breathing difficulty, uncontrolled bleeding, unconsciousness, or seizure.
2. `URGENT`: a concerning condition, severe pain, repeated vomiting, or inability to stand, unless an emergency rule already applies.
3. `STANDARD`: no emergency or urgent rule applies.

The daily queue sorts by priority first. Within the same priority, the appointment registered first comes first (as in a triage line, where arrival order breaks ties), then scheduled time, then ID.

The queue is built with a `PriorityQueue` and emptied with `poll()`, which always returns the highest-priority appointment. Producing the whole ordered list this way costs O(n log n), the same as sorting a list; a `PriorityQueue` would pay off if the queue were kept between operations and appointments were taken one at a time. It is used here because it models "who is next" directly, and because iterating a `PriorityQueue` without `poll()` would **not** return elements in order.

> These rules are intentionally simplified for a software-learning project. They are not a veterinary protocol or medical guidance.

### Schedule conflict detection

The current version represents one clinic room/team. Two active appointments conflict when their time intervals overlap:

```java
candidateStart.isBefore(existingEnd)
        && existingStart.isBefore(candidateEnd)
```

Adjacent appointments are allowed: `10:00–10:30` and `10:30–11:00` do not overlap. Cancelled appointments no longer block a time slot, and an appointment ignores itself when it is rescheduled.

### Persistence

Data is stored in `data/vetcare-data.json` with Gson. Dates use ISO-8601 values, which are readable and unambiguous. The application writes a temporary file first and then replaces the main file, reducing the chance of leaving a partially written data file.

Gson creates objects without calling the services, so loaded data would otherwise skip every business rule. On startup, `ClinicSnapshotValidator` checks each record: unique IDs, existing owner and animal references, known enum values, and the same field rules used for new input. If anything is wrong, the application reports the record ID and the problem, does not start, and leaves the file untouched for inspection.

Local JSON data is ignored by Git so personal test records are not published.

## Programming concepts demonstrated

- Java 21 and object-oriented programming
- Encapsulation and small, focused classes
- Collections: `Map`, `Set`, `List`, and `PriorityQueue`
- Comparators, streams, sorting, and filtering
- Date/time interval logic
- Input validation and custom exceptions
- JSON file I/O and persistence boundaries
- Dependency injection through constructors
- Automated tests with JUnit 5
- Maven build and GitHub Actions continuous integration

## Project structure

```text
src/main/java/com/amandadamata/vetcare/
├── cli/          # Console menu and input handling
├── exception/    # Domain and persistence errors
├── model/        # Owner, animal, appointment, and enums
├── persistence/  # JSON loading and saving
├── repository/   # In-memory collection access
├── service/      # Validation, triage, scheduling, and reports
├── util/         # Shared validation helpers
└── Main.java     # Application composition and startup

src/test/java/    # Unit and integration-style tests
docs/             # Architecture, design decisions, testing, walkthrough
```

`Main` only connects the repository, persistence, services, and CLI. Business rules stay outside the console interface so they can be tested without typing into a terminal.

## Requirements

- Java 21 or newer
- Internet access on the first build so the Maven Wrapper can download Maven and dependencies

No global Maven installation is required.

## How to run

Clone the repository and enter its directory:

```bash
git clone https://github.com/Amandadamata/vetcare-scheduler-java.git
cd vetcare-scheduler-java
```

Run the tests:

```bash
./mvnw clean test
```

Build the executable JAR:

```bash
./mvnw clean package
```

Start the application:

```bash
java -jar target/vetcare-scheduler.jar
```

On Windows, replace `./mvnw` with `mvnw.cmd`.

To keep test data somewhere else, set `VETCARE_DATA_FILE` before starting the application:

```bash
VETCARE_DATA_FILE=/tmp/vetcare-demo.json java -jar target/vetcare-scheduler.jar
```

## Tests

The test suite covers:

- emergency, urgent, and standard triage decisions
- emergency rules taking precedence over urgent rules
- partial, contained, and adjacent appointment intervals
- ignored cancelled appointments and safe rescheduling
- required fields, email, phone, date, and duration validation
- chronological ordering, combined filters, and triage ordering
- cancellation freeing a time slot
- daily report totals and groupings
- JSON round-trip persistence and invalid-file handling
- rejection of valid JSON that contains impossible data (unknown enum values, out-of-range durations, broken references, duplicate IDs)
- owner and animal registration, updates, searches, and the failed-save path
- clinical updates that recalculate the triage priority
- complete menu sessions driven by simulated keyboard input

GitHub Actions runs the full Maven verification on every push and pull request. See [docs/TESTING.md](docs/TESTING.md) for what each test class verifies.

## Current limitations

- The schedule models one room/team, not multiple veterinarians or resources.
- The application is local and intended for one running process at a time.
- JSON is appropriate for this learning scope but does not provide database concurrency or schema migrations.
- There is no authentication, network API, or graphical interface.
- Owners and animals cannot be deleted, so personal data can only be removed by editing or deleting the data file.
- Appointments in the past stay `SCHEDULED`; there is no completed or no-show status.
- If saving fails, the change remains in memory until the next successful save. The CLI warns the user when this happens.
- Triage rules are educational examples and must not be used for clinical decisions.

These boundaries are deliberate: the project focuses on understandable Java logic, separation of responsibilities, testing, and documentation.

## What I learned

This project helped me move from individual Java exercises to a complete application flow. The most important lessons were deciding where each rule belongs, representing time intervals correctly, using collections for a real purpose, keeping file operations separate from business logic, and testing behavior rather than only individual methods.

## Documentation

- [Architecture](docs/ARCHITECTURE.md): layer and sequence diagrams, startup, error handling, saving
- [Design decisions](docs/DESIGN_DECISIONS.md): what was chosen, why, alternatives, and the tests that verify it
- [Testing](docs/TESTING.md): how to run tests and what each class verifies
- [Project walkthrough](docs/PROJECT_WALKTHROUGH.md): class-by-class explanation and interview questions

## License

This project is available under the [MIT License](LICENSE).
