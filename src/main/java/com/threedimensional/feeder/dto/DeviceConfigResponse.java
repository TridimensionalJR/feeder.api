package com.threedimensional.feeder.dto;

import com.threedimensional.feeder.model.Feeder;
import com.threedimensional.feeder.model.FeederConfig;

import java.time.Instant;
import java.util.List;

/**
 * Operational configuration payload consumed by the physical feeder hardware.
 * Carries global device activation state, the feeder's IANA timezone for local wall-clock
 * interpretation, a server timestamp for clock verification, and only active feeding rules.
 * <p>
 * Does not navigate or expose any owner or account association.
 */
public record DeviceConfigResponse(
        boolean active,
        String timezone,
        Instant serverTime,
        List<DeviceScheduleItemResponse> schedules) {

    public static DeviceConfigResponse from(Feeder feeder, List<FeederConfig> activeConfigs, Instant serverTime) {
        return new DeviceConfigResponse(
                Boolean.TRUE.equals(feeder.getIsActive()),
                feeder.getTimezone(),
                serverTime,
                activeConfigs.stream().map(DeviceScheduleItemResponse::from).toList());
    }
}
