package com.threedimensional.feeder.controller;

import com.threedimensional.feeder.dto.DeviceConfigResponse;
import com.threedimensional.feeder.service.DeviceConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Public, hardware-facing endpoint allowing embedded automatic feeders to pull their active
 * configuration, timezone, and clock calibration data using their physical device identifier.
 */
@RestController
@RequestMapping("/devices")
public class DeviceConfigController {

    private final DeviceConfigService deviceConfigService;

    public DeviceConfigController(DeviceConfigService deviceConfigService) {
        this.deviceConfigService = deviceConfigService;
    }

    @GetMapping("/{deviceId}/config")
    public DeviceConfigResponse getConfig(@PathVariable UUID deviceId) {
        return deviceConfigService.getConfiguration(deviceId);
    }
}
