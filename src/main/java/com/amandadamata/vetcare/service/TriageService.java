package com.amandadamata.vetcare.service;

import com.amandadamata.vetcare.model.ClinicalSign;
import com.amandadamata.vetcare.model.PatientCondition;
import com.amandadamata.vetcare.model.TriagePriority;

import java.util.EnumSet;
import java.util.Set;

public class TriageService {
    private static final Set<ClinicalSign> EMERGENCY_SIGNS = EnumSet.of(
            ClinicalSign.BREATHING_DIFFICULTY,
            ClinicalSign.UNCONTROLLED_BLEEDING,
            ClinicalSign.UNCONSCIOUSNESS,
            ClinicalSign.SEIZURE);

    private static final Set<ClinicalSign> URGENT_SIGNS = EnumSet.of(
            ClinicalSign.SEVERE_PAIN,
            ClinicalSign.REPEATED_VOMITING,
            ClinicalSign.UNABLE_TO_STAND);

    public TriagePriority calculate(PatientCondition condition, Set<ClinicalSign> clinicalSigns) {
        Set<ClinicalSign> safeSigns = clinicalSigns == null ? Set.of() : clinicalSigns;
        if (condition == PatientCondition.CRITICAL || containsAny(safeSigns, EMERGENCY_SIGNS)) {
            return TriagePriority.EMERGENCY;
        }
        if (condition == PatientCondition.CONCERNING || containsAny(safeSigns, URGENT_SIGNS)) {
            return TriagePriority.URGENT;
        }
        return TriagePriority.STANDARD;
    }

    private boolean containsAny(Set<ClinicalSign> provided, Set<ClinicalSign> expected) {
        return provided.stream().anyMatch(expected::contains);
    }
}

