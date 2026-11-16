package com.threedimensional.feeder.dto;

import com.threedimensional.feeder.model.enums.ConfigType;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

/**
 * The body of a full schedule update. Replaces every mutable scheduling attribute of the rule.
 * Switching between {@link ConfigType#INTERVAL} and {@link ConfigType#DAILY_TIME} is allowed,
 * with stale fields cleared by the domain logic.
 * <p>
 * As with creation, domain validation guards the invariants, and switching active state has
 * dedicated activation/deactivation routes.
 */
public record UpdateFeederConfigRequest(
        @NotNull ConfigType type,
        Integer intervalMinutes,
        LocalTime timeOfDay,
        Set<DayOfWeek> weekdays,
        @NotNull Integer dose) {
}
