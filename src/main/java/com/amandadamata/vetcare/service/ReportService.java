package com.amandadamata.vetcare.service;

import com.amandadamata.vetcare.exception.ValidationException;
import com.amandadamata.vetcare.model.Appointment;
import com.amandadamata.vetcare.model.AppointmentStatus;
import com.amandadamata.vetcare.model.AppointmentType;
import com.amandadamata.vetcare.model.TriagePriority;
import com.amandadamata.vetcare.repository.ClinicRepository;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ReportService {
    private final ClinicRepository repository;

    public ReportService(ClinicRepository repository) {
        this.repository = repository;
    }

    public DailySummary dailySummary(LocalDate date) {
        if (date == null) {
            throw new ValidationException("A date is required for the daily summary.");
        }

        List<Appointment> appointments = repository.allAppointments().stream()
                .filter(appointment -> appointment.getStartTime().toLocalDate().equals(date))
                .toList();
        Map<AppointmentStatus, Long> byStatus = zeroCounts(AppointmentStatus.class);
        Map<TriagePriority, Long> byPriority = zeroCounts(TriagePriority.class);
        Map<AppointmentType, Long> byType = zeroCounts(AppointmentType.class);

        appointments.forEach(appointment -> {
            byStatus.compute(appointment.getStatus(), (key, value) -> value + 1);
            byPriority.compute(appointment.getPriority(), (key, value) -> value + 1);
            byType.compute(appointment.getType(), (key, value) -> value + 1);
        });

        return new DailySummary(
                date,
                appointments.size(),
                Map.copyOf(byStatus),
                Map.copyOf(byPriority),
                Map.copyOf(byType));
    }

    /** Every enum value starts at zero, so the report also shows categories with no appointments. */
    private static <E extends Enum<E>> Map<E, Long> zeroCounts(Class<E> enumType) {
        Map<E, Long> counts = new EnumMap<>(enumType);
        for (E value : enumType.getEnumConstants()) {
            counts.put(value, 0L);
        }
        return counts;
    }
}
