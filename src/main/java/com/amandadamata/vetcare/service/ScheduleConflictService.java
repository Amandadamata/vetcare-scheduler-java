package com.amandadamata.vetcare.service;

import com.amandadamata.vetcare.exception.ScheduleConflictException;
import com.amandadamata.vetcare.model.Appointment;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

public class ScheduleConflictService {
    /**
     * Rejects the candidate interval if it overlaps an appointment of the <b>same resource</b>
     * that still occupies the schedule (scheduled or completed). Appointments of other resources,
     * cancelled appointments, and no-shows never conflict.
     *
     * @param appointmentToIgnore the appointment being rescheduled, or {@code null}
     */
    public void ensureAvailable(
            Collection<Appointment> existingAppointments,
            UUID resourceId,
            LocalDateTime candidateStart,
            int candidateDurationMinutes,
            UUID appointmentToIgnore) {
        LocalDateTime candidateEnd = candidateStart.plusMinutes(candidateDurationMinutes);

        existingAppointments.stream()
                .filter(appointment -> Objects.equals(appointment.getResourceId(), resourceId))
                .filter(appointment -> appointment.getStatus().occupiesSchedule())
                .filter(appointment -> appointmentToIgnore == null
                        || !appointment.getId().equals(appointmentToIgnore))
                .filter(appointment -> overlaps(
                        candidateStart,
                        candidateEnd,
                        appointment.getStartTime(),
                        appointment.getEndTime()))
                .findFirst()
                .ifPresent(conflicting -> {
                    throw new ScheduleConflictException(
                            "Schedule conflict with the appointment from "
                                    + conflicting.getStartTime() + " to " + conflicting.getEndTime()
                                    + " on the same schedule.");
                });
    }

    public boolean overlaps(
            LocalDateTime firstStart,
            LocalDateTime firstEnd,
            LocalDateTime secondStart,
            LocalDateTime secondEnd) {
        return firstStart.isBefore(secondEnd) && secondStart.isBefore(firstEnd);
    }
}
