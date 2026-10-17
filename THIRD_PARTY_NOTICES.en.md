<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Extension-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic over the quadratic extension defined by u squared = 7.
Repository: https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks
File: THIRD_PARTY_NOTICES.en.md

SPDX-FileCopyrightText: 2026 Goldilocks extension-field vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Third-party notices

[简体中文](THIRD_PARTY_NOTICES.md)

This document describes third-party tools and mathematical parameters used in design, generation, and verification. Published design and reference code use the MIT License; third-party tools are obtained under their own licenses and are not redistributed with this repository.

The repository fixes the Goldilocks modulus `2^64−2^32+1` and extension relation `u²=7`. Their mathematical identity and irreducibility checks are described in [Mathematical model](docs/mathematical-model.en.md). These are public mathematical definitions, not private protocol parameters or process data, and do not constitute a complete adapter for a particular proof framework.

The build uses Mill, Scala, Chisel, and CIRCT; verification uses Python, Verilator, and GCC. Users installing these tools and Maven dependencies must comply with their respective licenses. This repository includes version requirements and its own code, not copies of those tools' binaries or sources. See [Toolchain](docs/toolchain.en.md) for versions.

`LICENSE` covers the repository's original RTL design, generation tools, reference/simulation code, and documentation. Commercial synthesis tools and process libraries are outside the MIT license scope and are not distributed here.
