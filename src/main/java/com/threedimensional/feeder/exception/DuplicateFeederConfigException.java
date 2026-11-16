package com.threedimensional.feeder.exception;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Thrown when attempting to add or update a schedule that collides with an existing rule on the
 * same feeder: either the same time of day or the same interval. Raised by the service pre-check
 * and, when two requests race past it, again when the unique constraint rejects the flush, so both
 * paths end in the same 409 instead of a 500.
 * <p>
 * The details stay in the message, which the controller advice logs; the response only says what
 * happened.
 */
public class DuplicateFeederConfigException extends RuntimeException {

    public DuplicateFeederConfigException(String message) {
        super(message);
    }

    public static DuplicateFeederConfigException forTimeOfDay(UUID feederId, LocalTime timeOfDay) {
        return new DuplicateFeederConfigException(
                "feeder %s already has a schedule for time %s".formatted(feederId, timeOfDay));
    }

    public static DuplicateFeederConfigException forInterval(UUID feederId, Integer intervalMinutes) {
        return new DuplicateFeederConfigException(
                "feeder %s already has a schedule for interval %d minutes".formatted(feederId, intervalMinutes));
    }
}
