package com.threedimensional.feeder.dto;

import com.threedimensional.feeder.model.enums.ConfigType;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

/**
 * The body of a schedule creation. It carries the scheduling parameters for either an
 * {@link ConfigType#INTERVAL} or a {@link ConfigType#DAILY_TIME} rule. The owning feeder is
 * taken from the URL path, and the authenticated owner is taken from the token, preventing
 * any cross-account rule creation.
 * <p>
 * Only structural presence is validated here ({@code type} and {@code dose} must not be null).
 * Domain constraints — such as mutual exclusivity between {@code intervalMinutes} and
 * {@code timeOfDay}, bounds on interval and dose, and non-empty weekdays for daily rules — are
 * owned by {@link com.threedimensional.feeder.model.FeederConfig} and mapped to a 400 through
 * {@link com.threedimensional.feeder.exception.InvalidFeederDataException}.
 */
public record CreateFeederConfigRequest(
        @NotNull ConfigType type,
        Integer intervalMinutes,
        LocalTime timeOfDay,
        Set<DayOfWeek> weekdays,
        @NotNull Integer dose) {
}
