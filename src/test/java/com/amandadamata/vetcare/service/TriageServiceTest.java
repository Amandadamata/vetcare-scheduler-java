package com.amandadamata.vetcare.service;

import com.amandadamata.vetcare.model.ClinicalSign;
import com.amandadamata.vetcare.model.PatientCondition;
import com.amandadamata.vetcare.model.TriagePriority;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TriageServiceTest {
    private final TriageService service = new TriageService();

    @Test
    void classifiesCriticalConditionAndEmergencySignsAsEmergency() {
        assertEquals(TriagePriority.EMERGENCY,
                service.calculate(PatientCondition.CRITICAL, Set.of()));

        for (ClinicalSign sign : Set.of(
                ClinicalSign.BREATHING_DIFFICULTY,
                ClinicalSign.UNCONTROLLED_BLEEDING,
                ClinicalSign.UNCONSCIOUSNESS,
                ClinicalSign.SEIZURE)) {
            assertEquals(TriagePriority.EMERGENCY,
                    service.calculate(PatientCondition.STABLE, Set.of(sign)), sign.name());
        }
    }

    @Test
    void emergencySignTakesPrecedenceOverConcerningCondition() {
        assertEquals(TriagePriority.EMERGENCY, service.calculate(
                PatientCondition.CONCERNING,
                Set.of(ClinicalSign.SEIZURE, ClinicalSign.SEVERE_PAIN)));
    }

    @Test
    void classifiesConcerningConditionAndUrgentSignsAsUrgent() {
        assertEquals(TriagePriority.URGENT,
                service.calculate(PatientCondition.CONCERNING, Set.of()));

        for (ClinicalSign sign : Set.of(
                ClinicalSign.SEVERE_PAIN,
                ClinicalSign.REPEATED_VOMITING,
                ClinicalSign.UNABLE_TO_STAND)) {
            assertEquals(TriagePriority.URGENT,
                    service.calculate(PatientCondition.STABLE, Set.of(sign)), sign.name());
        }
    }

    @Test
    void classifiesStablePatientWithoutRelevantSignsAsStandard() {
        assertEquals(TriagePriority.STANDARD,
                service.calculate(PatientCondition.STABLE, null));
        assertEquals(TriagePriority.STANDARD,
                service.calculate(PatientCondition.STABLE, Set.of(ClinicalSign.OTHER)));
    }
}
