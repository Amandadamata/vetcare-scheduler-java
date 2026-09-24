package com.amandadamata.vetcare.cli;

import com.amandadamata.vetcare.exception.DataPersistenceException;
import com.amandadamata.vetcare.exception.NotFoundException;
import com.amandadamata.vetcare.exception.ScheduleConflictException;
import com.amandadamata.vetcare.exception.ValidationException;
import com.amandadamata.vetcare.model.Animal;
import com.amandadamata.vetcare.model.Appointment;
import com.amandadamata.vetcare.model.AppointmentStatus;
import com.amandadamata.vetcare.model.AppointmentType;
import com.amandadamata.vetcare.model.ClinicalSign;
import com.amandadamata.vetcare.model.Owner;
import com.amandadamata.vetcare.model.PatientCondition;
import com.amandadamata.vetcare.model.Species;
import com.amandadamata.vetcare.model.TriagePriority;
import com.amandadamata.vetcare.service.AnimalService;
import com.amandadamata.vetcare.service.AppointmentFilter;
import com.amandadamata.vetcare.service.AppointmentService;
import com.amandadamata.vetcare.service.DailySummary;
import com.amandadamata.vetcare.service.OwnerService;
import com.amandadamata.vetcare.service.ReportService;

import java.io.PrintWriter;
import java.io.Reader;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Console interface for VetCare Scheduler. Business rules remain in the service layer.
 */
public final class VetCareCli {
    private static final DateTimeFormatter DATE_TIME_DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int LAST_MENU_OPTION = 16;

    private final OwnerService ownerService;
    private final AnimalService animalService;
    private final AppointmentService appointmentService;
    private final ReportService reportService;
    private final Clock clock;
    private final ConsoleInput input;
    private final PrintWriter output;

    private boolean running;
    private boolean persistenceHealthy;

    public VetCareCli(
            OwnerService ownerService,
            AnimalService animalService,
            AppointmentService appointmentService,
            ReportService reportService,
            Clock clock,
            Reader inputReader,
            PrintWriter output) {
        this.ownerService = Objects.requireNonNull(ownerService, "ownerService");
        this.animalService = Objects.requireNonNull(animalService, "animalService");
        this.appointmentService = Objects.requireNonNull(appointmentService, "appointmentService");
        this.reportService = Objects.requireNonNull(reportService, "reportService");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.output = Objects.requireNonNull(output, "output");
        this.input = new ConsoleInput(Objects.requireNonNull(inputReader, "inputReader"), output);
    }

    public void run() {
        running = true;
        persistenceHealthy = true;
        printWelcome();
        try {
            while (running) {
                printMenu();
                int option = input.readInt("Choose an option: ", 0, LAST_MENU_OPTION);
                output.println();
                execute(option);
            }
        } catch (ConsoleInput.InputClosedException exception) {
            output.println();
            output.println("Input closed. VetCare Scheduler is shutting down safely.");
        }
        output.flush();
    }

    private void execute(int option) {
        if (option == 0) {
            running = false;
            if (persistenceHealthy) {
                output.println("Goodbye. Clinic data was saved after each completed change.");
            } else {
                output.println("Goodbye. Warning: at least one change could not be saved; "
                        + "review the persistence error above.");
            }
            return;
        }

        try {
            switch (option) {
                case 1 -> registerOwner();
                case 2 -> registerAnimal();
                case 3 -> scheduleAppointment();
                case 4 -> listOwners();
                case 5 -> searchOwners();
                case 6 -> updateOwner();
                case 7 -> listAnimals();
                case 8 -> searchAnimals();
                case 9 -> updateAnimal();
                case 10 -> listAppointments();
                case 11 -> filterAppointments();
                case 12 -> updateAppointment();
                case 13 -> rescheduleAppointment();
                case 14 -> cancelAppointment();
                case 15 -> showTriageQueue();
                case 16 -> showDailySummary();
                default -> throw new IllegalStateException("Unexpected menu option: " + option);
            }
        } catch (DataPersistenceException exception) {
            persistenceHealthy = false;
            output.println("Persistence error: " + exception.getMessage());
            output.println("The change may exist only in memory. "
                    + "Check the data-file path and permissions before continuing.");
        } catch (ValidationException | NotFoundException | ScheduleConflictException exception) {
            output.println("Could not complete the operation: " + exception.getMessage());
        }
        output.println();
    }

    private void printWelcome() {
        output.println("========================================");
        output.println("          VetCare Scheduler");
        output.println("========================================");
        output.println("Veterinary scheduling and triage management");
        output.println();
    }

    private void printMenu() {
        output.println("Main menu");
        output.println("  1. Register owner");
        output.println("  2. Register animal");
        output.println("  3. Schedule appointment");
        output.println("  4. List owners");
        output.println("  5. Search owners");
        output.println("  6. Update owner");
        output.println("  7. List animals");
        output.println("  8. Search animals");
        output.println("  9. Update animal");
        output.println(" 10. List all appointments");
        output.println(" 11. Filter appointments");
        output.println(" 12. Update appointment details");
        output.println(" 13. Reschedule appointment");
        output.println(" 14. Cancel appointment");
        output.println(" 15. View triage queue");
        output.println(" 16. View daily summary");
        output.println("  0. Exit");
    }

    private void registerOwner() {
        output.println("Register owner");
        String name = input.readRequiredText("Name: ");
        String phone = input.readRequiredText("Phone: ");
        String email = input.readLine("Email (optional): ");

        Owner owner = ownerService.register(name, phone, email);
        output.println("Owner registered successfully.");
        printOwner(owner, null);
    }

    private void listOwners() {
        printOwners(ownerService.listAll(), "Registered owners");
    }

    private void searchOwners() {
        String query = input.readRequiredText("Search owners by name, phone, or email: ");
        String normalized = query.toLowerCase(Locale.ROOT);
        List<Owner> matches = ownerService.listAll().stream()
                .filter(owner -> owner.getName().toLowerCase(Locale.ROOT).contains(normalized)
                        || owner.getPhone().toLowerCase(Locale.ROOT).contains(normalized)
                        || owner.getEmail().toLowerCase(Locale.ROOT).contains(normalized))
                .toList();
        printOwners(matches, "Owner search results");
    }

    private void updateOwner() {
        Owner owner = selectOwner("Select an owner to update");
        if (owner == null) {
            return;
        }

        output.println("Press Enter to keep the current value. Enter - to clear the optional email.");
        String name = readKeepingCurrent("Name", owner.getName());
        String phone = readKeepingCurrent("Phone", owner.getPhone());
        String email = readClearableKeepingCurrent("Email", owner.getEmail());

        Owner updated = ownerService.update(owner.getId(), name, phone, email);
        output.println("Owner updated successfully.");
        printOwner(updated, null);
    }

    private void registerAnimal() {
        Owner owner = selectOwner("Select the animal's owner");
        if (owner == null) {
            output.println("Register an owner before registering an animal.");
            return;
        }

        String name = input.readRequiredText("Animal name: ");
        Species species = input.readEnum("Species", Species.values(), Species::getDisplayName);
        LocalDate birthDate = input.readDate("Birth date (yyyy-MM-dd): ");
        String notes = input.readLine("Notes (optional): ");

        Animal animal = animalService.register(owner.getId(), name, species, birthDate, notes);
        output.println("Animal registered successfully.");
        printAnimal(animal, null);
    }

    private void listAnimals() {
        printAnimals(animalService.listAll(), "Registered animals");
    }

    private void searchAnimals() {
        String query = input.readRequiredText("Search animal by name: ");
        printAnimals(animalService.searchByName(query), "Animal search results");
    }

    private void updateAnimal() {
        Animal animal = selectAnimal("Select an animal to update");
        if (animal == null) {
            return;
        }

        output.println("Press Enter to keep the current value. Enter - to clear notes.");
        String name = readKeepingCurrent("Name", animal.getName());
        Species species = input.readEnumOrDefault(
                "Species",
                Species.values(),
                Species::getDisplayName,
                animal.getSpecies());
        LocalDate birthDate = input.readDateOrDefault(
                "Birth date (yyyy-MM-dd) [" + animal.getBirthDate() + "]: ",
                animal.getBirthDate());
        String notes = readClearableKeepingCurrent("Notes", animal.getNotes());

        Animal updated = animalService.update(animal.getId(), name, species, birthDate, notes);
        output.println("Animal updated successfully.");
        printAnimal(updated, null);
    }

    private void scheduleAppointment() {
        Animal animal = selectAnimal("Select the animal for the appointment");
        if (animal == null) {
            output.println("Register an animal before scheduling an appointment.");
            return;
        }

        AppointmentType type = input.readEnum(
                "Appointment type",
                AppointmentType.values(),
                AppointmentType::getDisplayName);
        LocalDateTime startTime = input.readDateTime("Start (yyyy-MM-dd HH:mm): ");
        int duration = input.readInt("Duration in minutes (15-180): ", 15, 180);
        PatientCondition condition = input.readEnum(
                "Patient condition",
                PatientCondition.values(),
                PatientCondition::getDisplayName);
        Set<ClinicalSign> signs = readClinicalSigns(null);
        String notes = input.readLine("Notes (optional): ");

        Appointment appointment = appointmentService.schedule(
                animal.getId(), type, startTime, duration, condition, signs, notes);
        output.printf("Appointment scheduled successfully with %s priority.%n",
                appointment.getPriority().getDisplayName());
        printAppointment(appointment, null);
    }

    private void listAppointments() {
        printAppointments(appointmentService.list(AppointmentFilter.all()), "All appointments");
    }

    private void filterAppointments() {
        output.println("Filter appointments (leave a value blank to include any)");
        LocalDate date = input.readOptionalDate("Date (yyyy-MM-dd, optional): ");
        AppointmentStatus status = input.readOptionalEnum(
                "Status",
                AppointmentStatus.values(),
                AppointmentStatus::getDisplayName);
        AppointmentType type = input.readOptionalEnum(
                "Appointment type",
                AppointmentType.values(),
                AppointmentType::getDisplayName);
        TriagePriority priority = input.readOptionalEnum(
                "Triage priority",
                TriagePriority.values(),
                TriagePriority::getDisplayName);

        AppointmentFilter filter = new AppointmentFilter(date, status, type, priority);
        printAppointments(appointmentService.list(filter), "Filtered appointments");
    }

    private void updateAppointment() {
        Appointment appointment = selectScheduledAppointment("Select an appointment to update");
        if (appointment == null) {
            return;
        }

        output.println("Press Enter to keep the current value. Enter - to clear notes.");
        AppointmentType type = input.readEnumOrDefault(
                "Appointment type",
                AppointmentType.values(),
                AppointmentType::getDisplayName,
                appointment.getType());
        PatientCondition condition = input.readEnumOrDefault(
                "Patient condition",
                PatientCondition.values(),
                PatientCondition::getDisplayName,
                appointment.getCondition());
        Set<ClinicalSign> signs = readClinicalSigns(appointment.getClinicalSigns());
        String notes = readClearableKeepingCurrent("Notes", appointment.getNotes());

        Appointment updated = appointmentService.updateClinicalDetails(
                appointment.getId(), type, condition, signs, notes);
        output.printf("Appointment updated successfully. Triage priority is now %s.%n",
                updated.getPriority().getDisplayName());
        printAppointment(updated, null);
    }

    private void rescheduleAppointment() {
        Appointment appointment = selectScheduledAppointment("Select an appointment to reschedule");
        if (appointment == null) {
            return;
        }

        output.println("Press Enter to keep the current start time or duration.");
        LocalDateTime startTime = input.readDateTimeOrDefault(
                "New start (yyyy-MM-dd HH:mm) [" + DATE_TIME_DISPLAY.format(appointment.getStartTime()) + "]: ",
                appointment.getStartTime());
        int duration = input.readIntOrDefault(
                "New duration in minutes [" + appointment.getDurationMinutes() + "]: ",
                15,
                180,
                appointment.getDurationMinutes());

        Appointment updated = appointmentService.reschedule(appointment.getId(), startTime, duration);
        output.println("Appointment rescheduled successfully.");
        printAppointment(updated, null);
    }

    private void cancelAppointment() {
        Appointment appointment = selectScheduledAppointment("Select an appointment to cancel");
        if (appointment == null) {
            return;
        }

        printAppointment(appointment, null);
        if (!input.readConfirmation("Cancel this appointment? (yes/no): ")) {
            output.println("Cancellation aborted. No changes were made.");
            return;
        }

        Appointment cancelled = appointmentService.cancel(appointment.getId());
        output.println("Appointment cancelled successfully.");
        printAppointment(cancelled, null);
    }

    private void showTriageQueue() {
        LocalDate today = LocalDate.now(clock);
        LocalDate date = input.readDateOrDefault(
                "Queue date (yyyy-MM-dd, Enter for " + today + "): ",
                today);
        printAppointments(appointmentService.triageQueue(date), "Triage queue for " + date);
    }

    private void showDailySummary() {
        LocalDate today = LocalDate.now(clock);
        LocalDate date = input.readDateOrDefault(
                "Summary date (yyyy-MM-dd, Enter for " + today + "): ",
                today);
        DailySummary summary = reportService.dailySummary(date);

        output.println("Daily summary for " + summary.date());
        output.println("  Total appointments: " + summary.total());
        output.println("  Scheduled: " + summary.scheduled());
        output.println("  Cancelled: " + summary.cancelled());
        output.println("  By priority:");
        for (TriagePriority priority : TriagePriority.values()) {
            output.printf("    %-10s %d%n", priority.getDisplayName() + ":", summary.byPriority().get(priority));
        }
        output.println("  By type:");
        for (AppointmentType type : AppointmentType.values()) {
            output.printf("    %-13s %d%n", type.getDisplayName() + ":", summary.byType().get(type));
        }
    }

    private Owner selectOwner(String heading) {
        List<Owner> owners = ownerService.listAll();
        if (owners.isEmpty()) {
            output.println("No owners are registered yet.");
            return null;
        }
        printOwners(owners, heading);
        return readSelection(owners, Owner::getId, "owner");
    }

    private Animal selectAnimal(String heading) {
        List<Animal> animals = animalService.listAll();
        if (animals.isEmpty()) {
            output.println("No animals are registered yet.");
            return null;
        }
        printAnimals(animals, heading);
        return readSelection(animals, Animal::getId, "animal");
    }

    private Appointment selectScheduledAppointment(String heading) {
        List<Appointment> appointments = appointmentService.list(
                new AppointmentFilter(null, AppointmentStatus.SCHEDULED, null, null));
        if (appointments.isEmpty()) {
            output.println("There are no scheduled appointments available for this action.");
            return null;
        }
        printAppointments(appointments, heading);
        return readSelection(appointments, Appointment::getId, "appointment");
    }

    private <T> T readSelection(List<T> items, Function<T, UUID> idProvider, String itemName) {
        while (true) {
            String value = input.readRequiredText(
                    "Choose the " + itemName + " by list number or UUID: ");
            try {
                int index = Integer.parseInt(value);
                if (index >= 1 && index <= items.size()) {
                    return items.get(index - 1);
                }
            } catch (NumberFormatException ignored) {
                // It may be a UUID instead.
            }

            try {
                UUID id = UUID.fromString(value);
                for (T item : items) {
                    if (idProvider.apply(item).equals(id)) {
                        return item;
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // The common error message below covers invalid UUIDs too.
            }
            output.printf("Enter a list number from 1 to %d or a UUID shown above.%n", items.size());
        }
    }

    private Set<ClinicalSign> readClinicalSigns(Set<ClinicalSign> currentSigns) {
        ClinicalSign[] values = ClinicalSign.values();
        output.println("Clinical signs");
        for (int index = 0; index < values.length; index++) {
            output.printf("  %d. %s%n", index + 1, values[index].getDisplayName());
        }

        String prompt;
        if (currentSigns == null) {
            prompt = "Enter comma-separated numbers (Enter for none): ";
        } else {
            prompt = "Enter comma-separated numbers (Enter keeps current; 'none' clears): ";
        }

        while (true) {
            String value = input.readLine(prompt);
            if (value.isBlank()) {
                return mutableSignsCopy(currentSigns);
            }
            if (value.equalsIgnoreCase("none")) {
                return EnumSet.noneOf(ClinicalSign.class);
            }

            EnumSet<ClinicalSign> signs = EnumSet.noneOf(ClinicalSign.class);
            boolean valid = true;
            for (String token : value.split(",")) {
                try {
                    int option = Integer.parseInt(token.trim());
                    if (option < 1 || option > values.length) {
                        valid = false;
                        break;
                    }
                    signs.add(values[option - 1]);
                } catch (NumberFormatException exception) {
                    valid = false;
                    break;
                }
            }
            if (valid) {
                return signs;
            }
            output.printf("Enter comma-separated numbers from 1 to %d, or use the blank option described above.%n",
                    values.length);
        }
    }

    private Set<ClinicalSign> mutableSignsCopy(Set<ClinicalSign> signs) {
        if (signs == null || signs.isEmpty()) {
            return EnumSet.noneOf(ClinicalSign.class);
        }
        return EnumSet.copyOf(signs);
    }

    private String readKeepingCurrent(String fieldName, String currentValue) {
        String value = input.readLine(fieldName + " [" + currentValue + "]: ");
        return value.isBlank() ? currentValue : value;
    }

    private String readClearableKeepingCurrent(String fieldName, String currentValue) {
        String displayValue = currentValue.isBlank() ? "not provided" : currentValue;
        String value = input.readLine(fieldName + " [" + displayValue + "]: ");
        if (value.isBlank()) {
            return currentValue;
        }
        return value.equals("-") ? "" : value;
    }

    private void printOwners(List<Owner> owners, String heading) {
        output.println(heading + " (" + owners.size() + ")");
        if (owners.isEmpty()) {
            output.println("  No owners found.");
            return;
        }
        for (int index = 0; index < owners.size(); index++) {
            printOwner(owners.get(index), index + 1);
        }
    }

    private void printOwner(Owner owner, Integer index) {
        String prefix = index == null ? "" : "[" + index + "] ";
        String email = owner.getEmail().isBlank() ? "not provided" : owner.getEmail();
        output.printf("%s%s | phone: %s | email: %s%n", prefix, owner.getName(), owner.getPhone(), email);
        output.println("    ID: " + owner.getId());
    }

    private void printAnimals(List<Animal> animals, String heading) {
        output.println(heading + " (" + animals.size() + ")");
        if (animals.isEmpty()) {
            output.println("  No animals found.");
            return;
        }
        for (int index = 0; index < animals.size(); index++) {
            printAnimal(animals.get(index), index + 1);
        }
    }

    private void printAnimal(Animal animal, Integer index) {
        String prefix = index == null ? "" : "[" + index + "] ";
        output.printf("%s%s | %s | born %s | owner: %s%n",
                prefix,
                animal.getName(),
                animal.getSpecies().getDisplayName(),
                animal.getBirthDate(),
                ownerName(animal.getOwnerId()));
        output.println("    ID: " + animal.getId());
        if (!animal.getNotes().isBlank()) {
            output.println("    Notes: " + animal.getNotes());
        }
    }

    private void printAppointments(List<Appointment> appointments, String heading) {
        output.println(heading + " (" + appointments.size() + ")");
        if (appointments.isEmpty()) {
            output.println("  No appointments found.");
            return;
        }
        for (int index = 0; index < appointments.size(); index++) {
            printAppointment(appointments.get(index), index + 1);
        }
    }

    private void printAppointment(Appointment appointment, Integer index) {
        String prefix = index == null ? "" : "[" + index + "] ";
        output.printf("%s%s to %s | %s | %s | %s | %s%n",
                prefix,
                DATE_TIME_DISPLAY.format(appointment.getStartTime()),
                DATE_TIME_DISPLAY.format(appointment.getEndTime()),
                animalName(appointment.getAnimalId()),
                appointment.getType().getDisplayName(),
                appointment.getPriority().getDisplayName(),
                appointment.getStatus().getDisplayName());
        output.printf("    Condition: %s | Duration: %d minutes | Signs: %s%n",
                appointment.getCondition().getDisplayName(),
                appointment.getDurationMinutes(),
                clinicalSignsText(appointment.getClinicalSigns()));
        output.println("    ID: " + appointment.getId());
        if (!appointment.getNotes().isBlank()) {
            output.println("    Notes: " + appointment.getNotes());
        }
    }

    private String ownerName(UUID ownerId) {
        try {
            return ownerService.findById(ownerId).getName();
        } catch (NotFoundException exception) {
            return "Unknown owner (" + ownerId + ")";
        }
    }

    private String animalName(UUID animalId) {
        try {
            return animalService.findById(animalId).getName();
        } catch (NotFoundException exception) {
            return "Unknown animal (" + animalId + ")";
        }
    }

    private String clinicalSignsText(Set<ClinicalSign> signs) {
        if (signs.isEmpty()) {
            return "none";
        }
        List<ClinicalSign> ordered = new ArrayList<>(signs);
        ordered.sort(Comparator.comparingInt(ClinicalSign::ordinal));
        return ordered.stream()
                .map(ClinicalSign::getDisplayName)
                .collect(Collectors.joining(", "));
    }
}
