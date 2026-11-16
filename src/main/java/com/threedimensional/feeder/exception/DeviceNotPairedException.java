package com.threedimensional.feeder.exception;

import java.util.UUID;

/**
 * Thrown when a hardware device queries its configuration before being registered to any user
 * account. The device id stays in the exception message for logging; the API answers 404.
 */
public class DeviceNotPairedException extends RuntimeException {

    public DeviceNotPairedException(UUID deviceId) {
        super("device %s is not paired to any feeder".formatted(deviceId));
    }
}
