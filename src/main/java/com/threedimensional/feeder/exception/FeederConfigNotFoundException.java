package com.threedimensional.feeder.exception;

import java.util.UUID;

/**
 * Thrown when a feeder configuration cannot be found for the caller, either because the id
 * never existed or because the configuration belongs to another feeder or account. Both cases
 * share this exception on purpose: the API answers one identical 404, so the response cannot
 * be used to learn that somebody else's configuration exists.
 * <p>
 * The id travels in the message, which the controller advice logs; the response never carries it.
 */
public class FeederConfigNotFoundException extends RuntimeException {

    public FeederConfigNotFoundException(UUID id) {
        super("feeder config %s does not exist".formatted(id));
    }
}
