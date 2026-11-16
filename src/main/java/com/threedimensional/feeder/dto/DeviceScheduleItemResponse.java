package com.threedimensional.feeder.dto;

import com.threedimensional.feeder.model.FeederConfig;
import com.threedimensional.feeder.model.enums.ConfigType;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

/**
 * A single active feeding schedule formatted for consumption by embedded firmware.
 * Attributes that do not apply to the specific schedule shape are left null (e.g. intervalMinutes
 * for DAILY_TIME, or timeOfDay and weekdays for INTERVAL).
 */
public record DeviceScheduleItemResponse(
        UUID id,
        ConfigType type,
        Integer intervalMinutes,
        LocalTime timeOfDay,
        Set<DayOfWeek> weekdays,
        int dose) {

    public static DeviceScheduleItemResponse from(FeederConfig config) {
        return new DeviceScheduleItemResponse(
                config.getId(),
                config.getType(),
                config.getIntervalMinutes(),
                config.getTimeOfDay(),
                config.getWeekdays(),
                config.getDose());
    }
}
