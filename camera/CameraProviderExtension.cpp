/*
 * Copyright (C) 2024 LibreMobileOS Foundation
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "CameraProviderExtension.h"

#include <algorithm>
#include <fstream>
#include <string>

#define TORCH_BRIGHTNESS "brightness"
#define TOGGLE_SWITCH "/sys/devices/platform/soc/c440000.qcom,spmi/spmi-0/spmi0-05/c440000.qcom,spmi:qcom,pm8150l@5:qcom,leds@d300/leds/led:switch_0/brightness"

static const std::string kTorchLedPath =
        "/sys/devices/platform/soc/c440000.qcom,spmi/spmi-0/spmi0-05/c440000.qcom,spmi:qcom,pm8150l@5:qcom,leds@d300/leds/led:torch_0";

/**
 * Write value to path and close file.
 */
template <typename T>
static bool set(const std::string& path, const T& value) {
    std::ofstream file(path);
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

bool supportsTorchStrengthControlExt() {
    return true;
}

bool supportsSetTorchModeExt() {
    return false;
}

int32_t getTorchDefaultStrengthLevelExt() {
    // 59 is about 23% of the advertised 0-255 range.
    return 59;
}

int32_t getTorchMaxStrengthLevelExt() {
    // Cap the advertised strength below the hardware maximum.
    return 255;
}

int32_t getTorchStrengthLevelExt() {
    auto node = kTorchLedPath + "/" + TORCH_BRIGHTNESS;
    return std::clamp(get(node, 0), 0, getTorchMaxStrengthLevelExt());
}

void setTorchStrengthLevelExt(int32_t torchStrength, bool enabled) {
    const int32_t strength = std::clamp(torchStrength, 0, getTorchMaxStrengthLevelExt());
    auto node = kTorchLedPath + "/" + TORCH_BRIGHTNESS;
    const bool success = set(node, strength);

    // Avoid switching off the torch during live strength changes.
    if (success && enabled && strength > 0) {
        set(TOGGLE_SWITCH, 255);
    } else {
        set(TOGGLE_SWITCH, 0);
    }
}

void setTorchModeExt(bool enabled) {
    int32_t strength = getTorchDefaultStrengthLevelExt();
    setTorchStrengthLevelExt(enabled ? strength : 0, enabled);
}
