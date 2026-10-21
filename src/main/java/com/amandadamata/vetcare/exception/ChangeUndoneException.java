package com.amandadamata.vetcare.exception;

/**
 * A save failed and the change was undone in memory, so memory and the data file still agree.
 * Thrown by operations that must never leave the two different, such as deleting personal data.
 */
public class ChangeUndoneException extends DataPersistenceException {
    public ChangeUndoneException(DataPersistenceException cause) {
        super(cause.getMessage(), cause);
    }
}
