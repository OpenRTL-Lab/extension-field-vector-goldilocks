<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Extension-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic over the quadratic extension defined by u squared = 7.
Repository: https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks
File: docs/toolchain.en.md

SPDX-FileCopyrightText: 2026 Goldilocks extension-field vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Toolchain

[简体中文](toolchain.md)

This document lists the tool versions used for independent circuit generation and complete functional/performance reproduction, along with dependency and optional launcher settings. Scala, Chisel, and plugin versions are fixed by the build file without internal-project or machine-specific dependencies.

| Tool | Verified version | Purpose |
| --- | --- | --- |
| Mill | 1.1.7 | Scala build and generation entry point |
| Scala | 2.13.18 | Design language |
| Chisel and plugin | 7.13.0 | Circuit construction |
| CIRCT firtool | 1.149.0 | SystemVerilog lowering and splitting |
| OpenJDK | 21.0.11 | JVM |
| Python | 3.12 | Independent reference and verification scripts |
| Verilator | 5.050 | Product lint and RTL simulation |
| GCC / G++ | 16.2.0 | C reference and simulation compilation |

Install these open-source tools so `java`, `mill`, `python3`, `verilator`, `cc`, and `g++` are available through PATH. Mill resolves Scala/Chisel through public Maven dependencies, requiring network access or a user-prepared cache on first use. Chisel resolves its corresponding firtool; use the listed version for consistent generation. Tool binaries and machine-specific caches are not bundled.

Optional environment variables `MILL`, `VERILATOR`, and `CC` specify launcher commands. Scripts split command arguments according to shell quoting rules but do not execute arbitrary shell expressions. Cache and temporary locations are selected by the user environment, not bound to absolute paths in the repository. Verification compiles C and C++ and requires compilers plus `make`.

To check installed versions before following [Reproduction](reproduction.en.md), run these read-only commands:

```sh
java -version
mill --version
python3 --version
verilator --version
cc --version
g++ --version
```

Generation and simulation require only open-source tools. The commercial tool named in the synthesis summary is not a dependency of the public verification chain.
