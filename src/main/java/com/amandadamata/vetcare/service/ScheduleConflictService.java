package com.amandadamata.vetcare.service;

import com.amandadamata.vetcare.exception.ScheduleConflictException;
import com.amandadamata.vetcare.model.Appointment;
import com.amandadamata.vetcare.model.AppointmentStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;

public class ScheduleConflictService {
    public void ensureAvailable(
            Collection<Appointment> existingAppointments,
            LocalDateTime candidateStart,
            int candidateDurationMinutes,
            UUID appointmentToIgnore) {
        LocalDateTime candidateEnd = candidateStart.plusMinutes(candidateDurationMinutes);

        existingAppointments.stream()
                .filter(appointment -> appointment.getStatus() == AppointmentStatus.SCHEDULED)
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
                                    + conflicting.getStartTime() + " to " + conflicting.getEndTime() + ".");
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

