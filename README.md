# Common device tree for Xiaomi SM8250 devices

The thermal profile `sconfig` IDs in XiaomiParts are verified against the
Xiaomi Munch vendor thermal map. Devices reusing this common tree must provide
compatible vendor thermal-map entries before enabling these Munch-specific
profiles; profile IDs are vendor-config-specific and must not be assumed to
match across SM8250 devices.

```
#
# Copyright (C) 2021 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#
```
