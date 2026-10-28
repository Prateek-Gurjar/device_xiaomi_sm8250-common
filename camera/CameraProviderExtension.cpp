/*
 * Copyright (C) 2024 LibreMobileOS Foundation
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "CameraProviderExtension.h"

#include <algorithm>
#include <array>
#include <cstdint>
#include <fstream>
#include <string>

#define TORCH_BRIGHTNESS "brightness"
#define TORCH_MAX_BRIGHTNESS "max_brightness"
#define TOGGLE_SWITCH "/sys/devices/platform/soc/c440000.qcom,spmi/spmi-0/spmi0-05/c440000.qcom,spmi:qcom,pm8150l@5:qcom,leds@d300/leds/led:switch_2/brightness"

namespace {

constexpr int32_t kTorchStrengthSafetyLimit = 255;

const std::array<std::string, 2> kTorchLedPaths = {{
        "/sys/devices/platform/soc/c440000.qcom,spmi/spmi-0/spmi0-05/c440000.qcom,spmi:qcom,pm8150l@5:qcom,leds@d300/leds/led:torch_0",
        "/sys/devices/platform/soc/c440000.qcom,spmi/spmi-0/spmi0-05/c440000.qcom,spmi:qcom,pm8150l@5:qcom,leds@d300/leds/led:torch_1",
}};

const std::string kTorchSwitchPath = TOGGLE_SWITCH;

/**
 * Write value to path and close file.
 */
template <typename T>
static bool set(const std::string& path, const T& value) {
    std::ofstream file(path);
    if (!file.is_open()) {
        return false;
    }

    file << value;
    file.flush();
    return file.good();
}

/**
 * Read value from the path and close file.
 */
template <typename T>
static T get(const std::string& path, const T& def) {
    std::ifstream file(path);
    T result{};

    file >> result;
    return file.fail() ? def : result;
}

}  // namespace

bool supportsTorchStrengthControlExt() {
    return true;
}

bool supportsSetTorchModeExt() {
    return false;
}

int32_t getTorchDefaultStrengthLevelExt() {
    // Preserve the established default while keeping the PMIC current capped.
    return std::min(59, getTorchMaxStrengthLevelExt());
}

int32_t getTorchMaxStrengthLevelExt() {
    // Use the lower channel limit so both LEDs can always be driven evenly.
    int32_t maxStrength = kTorchStrengthSafetyLimit;
    for (const auto& path : kTorchLedPaths) {
        const auto maxBrightnessNode = path + "/" + TORCH_MAX_BRIGHTNESS;
        const int32_t hardwareMax = get(maxBrightnessNode, kTorchStrengthSafetyLimit);
        if (hardwareMax > 0) {
            maxStrength = std::min(maxStrength, hardwareMax);
        }
    }
    return maxStrength;
}

int32_t getTorchStrengthLevelExt() {
    // We write same value in the both LEDs,
    // so get from one and keep it within the advertised range.
    auto node = kTorchLedPaths[0] + "/" + TORCH_BRIGHTNESS;
    return std::clamp(get(node, 0), 0, getTorchMaxStrengthLevelExt());
}

void setTorchStrengthLevelExt(int32_t torchStrength, bool enabled) {
    const int32_t strength = std::clamp(torchStrength, 0, getTorchMaxStrengthLevelExt());

    // Keep the two PMIC channels synchronized. Do not re-enable the combined
    // switch if either channel rejected the requested strength.
    const bool switchDisabled = set(kTorchSwitchPath, 0);
    bool channelsUpdated = true;
    for (const auto& path : kTorchLedPaths) {
        const auto node = path + "/" + TORCH_BRIGHTNESS;
        channelsUpdated = set(node, strength) && channelsUpdated;
    }

    if (enabled && strength > 0 && switchDisabled && channelsUpdated) {
        set(kTorchSwitchPath, 255);
    }
}

void setTorchModeExt(bool enabled) {
    int32_t strength = getTorchDefaultStrengthLevelExt();
    setTorchStrengthLevelExt(enabled ? strength : 0, enabled);
}
