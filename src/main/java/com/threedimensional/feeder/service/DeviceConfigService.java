package com.threedimensional.feeder.service;

import com.threedimensional.feeder.dto.DeviceConfigResponse;
import com.threedimensional.feeder.exception.DeviceNotPairedException;
import com.threedimensional.feeder.model.Feeder;
import com.threedimensional.feeder.model.FeederConfig;
import com.threedimensional.feeder.repository.FeederConfigRepository;
import com.threedimensional.feeder.repository.FeederRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Hardware-facing configuration service. Lookups are keyed strictly by physical {@code deviceId}.
 * Only active rules are retrieved ({@link FeederConfigRepository#findByFeederIdAndIsActiveTrue}),
 * shielding the embedded device from parsing disabled rules.
 */
@Service
public class DeviceConfigService {

    private final FeederRepository feederRepository;
    private final FeederConfigRepository feederConfigRepository;
    private final Clock clock;

    public DeviceConfigService(FeederRepository feederRepository,
                               FeederConfigRepository feederConfigRepository,
                               Clock clock) {
        this.feederRepository = feederRepository;
        this.feederConfigRepository = feederConfigRepository;
        this.clock = clock;
    }

    /**
     * Resolves the operational configuration for a physical device. Throws {@link DeviceNotPairedException}
     * if the hardware identifier is not associated with any feeder.
     */
    @Transactional(readOnly = true)
    public DeviceConfigResponse getConfiguration(UUID deviceId) {
        Feeder feeder = feederRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new DeviceNotPairedException(deviceId));

        List<FeederConfig> activeConfigs =
                feederConfigRepository.findByFeederIdAndIsActiveTrue(feeder.getId());

        return DeviceConfigResponse.from(feeder, activeConfigs, clock.instant());
    }
}
