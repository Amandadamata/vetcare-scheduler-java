package com.amandadamata.vetcare.model;

import java.util.UUID;

/**
 * Something that can only attend one appointment at a time: a veterinarian, a room, or a
 * piece of equipment. Each appointment belongs to exactly one resource, and conflicts are only
 * checked between appointments of the same resource.
 */
public class ScheduleResource {
    private UUID id;
    private String name;

    @SuppressWarnings("unused")
    private ScheduleResource() {
        // Required by Gson.
    }

    public ScheduleResource(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
