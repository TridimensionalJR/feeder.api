package com.threedimensional.feeder.dto;

import com.threedimensional.feeder.model.FeederConfig;
import com.threedimensional.feeder.model.enums.ConfigType;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

/**
 * A feeder configuration as the front sees it. The entity is never serialized directly: it
 * carries the owning feeder association, which is lazy and must not be read once the transaction
 * ends ({@code open-in-view=false}), and the owner/feeder relationship is already established
 * by the request path.
 * <p>
 * {@code active} is derived with a null-safe comparison, matching {@link FeederResponse}.
 * Nulls for {@code intervalMinutes} and {@code timeOfDay} are kept and serialized on purpose,
 * reflecting the schedule shape discriminator {@link ConfigType}.
 */
public record FeederConfigResponse(
        UUID id,
        ConfigType type,
        Integer intervalMinutes,
        LocalTime timeOfDay,
        Set<DayOfWeek> weekdays,
        int dose,
        Instant lastRunAt,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static FeederConfigResponse from(FeederConfig config) {
        return new FeederConfigResponse(
                config.getId(),
                config.getType(),
                config.getIntervalMinutes(),
                config.getTimeOfDay(),
                config.getWeekdays(),
                config.getDose(),
                config.getLastRunAt(),
                Boolean.TRUE.equals(config.getIsActive()),
                config.getCreatedAt(),
                config.getUpdatedAt());
    }
}
