<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Extension-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic over the quadratic extension defined by u squared = 7.
Repository: https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks
File: docs/reproduction.en.md

SPDX-FileCopyrightText: 2026 Goldilocks extension-field vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Reproduction

[简体中文](reproduction.md)

This guide covers tool preparation, reference checks, Chisel generation, product SV checks, and six simulation tests. The default run provides quick functional verification; the full-performance command reproduces 444 tasks and all published cycle metrics without process libraries, commercial synthesis tools, or an internal repository.

## Obtain the release snapshot

[Release v1.1.0](https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks/releases/tag/v1.1.0)

```bash
git clone --branch v1.1.0 --depth 1 https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks.git
cd extension-field-vector-goldilocks
```

The version tag identifies the current source tree, including source notices and documentation. Release downloads provide hardware sources and bundled RTL for this version. Archive annotations and documentation can differ from the online tree. For RTL integration, use the product file list rather than verification environments; use the verification and synthesis documents for supported configurations and measurement conditions.

## 1. Prepare tools

Install the tools specified in [Toolchain](toolchain.en.md), make their launch commands available through PATH, and enter this repository's root. All commands below run from that root; `build` is a local output directory, not an external project dependency.

The first run requires Mill to resolve public Scala/Chisel dependencies and Chisel to resolve the matching firtool. Tool installation is a prerequisite for generation and simulation, not itself verification.

Before the commands: use a fresh relative directory below `build/` for each run. `--max-length` sets hardware vector capacity (16..65,536); `--contexts` selects 16 or 32 inverse element-state slots in one task. `--jobs` is compilation parallelism (1..64), not extra arithmetic lanes. The 92/444 tasks are test workloads; the six simulation scenarios exercise the seven product operations rather than adding six operation modes.

## 2. Quick functional verification

The following command compiles the C reference, independently checks field arithmetic, generates the Chisel environment and product, lints the product, and tests regenerated and bundled SV under ideal interfaces, backpressure, ordinary reset, a negative reference, INV reset, and DOT reset:

```sh
python3 tools/verify.py --out build/verify --jobs 8
```

The default contains 92 tasks. The script should return successfully, with `status=0` and `bundled_product_equal=true` in `build/verify/results.json`. The negative test intentionally corrupts one expected value and must detect exactly one error with a nonzero exit; the overall script passes only if that intended failure is detected correctly.

## 3. Full performance reproduction

The following command adds long vectors, repeated batches, and a long nonzero INV workload, reproducing the complete measurements in [Performance](performance.en.md):

```sh
python3 tools/verify.py --out build/verify-full --large --jobs 8
```

Expect 444 tasks and 411,963 checked result records per test. Generated and bundled products each require zero errors in five positive tests and exactly one error in the negative test. Their ideal-mode task latency, batch throughput, and busy/idle statistics must match exactly.

| Output | Contents |
| --- | --- |
| `results.json` | Configuration, checked/error/cycle counts for each test, and overall status |
| `reference-check.json` | Independent C/Python arithmetic cross-checks |
| `plans.json` | Per-task mode, length, tag, boundary case, and expected status |
| `generated/performance.json` | Regenerated-product latency, utilization, and batch throughput |
| `bundled/performance.json` | Same measurements for bundled SV, automatically compared |
| `generated/*.log`, `bundled/*.log` | Simulation events, useful arithmetic work, and SUMMARY |
| `rtl/product.f` | Product-only dependency closure |
| `rtl/simulation.f` | Complete verification-environment dependencies |

These files are generated beneath the selected `--out` directory. Reference ROMs and C++/Verilator build outputs belong to simulation, not the product SV.

## 4. Generate without simulation

The following command generates reference vectors, the environment, and a standalone product dependency list without running Verilator tests. Use `build/emit/rtl/product.f` for product compilation:

```sh
python3 tools/emit_sv.py --out build/emit --max-length 65536 --contexts 16
```

The five bundled product modules are also ready to use in [Generated SV](../rtl/v1.1.0/gl64-e2-lane1-ctx16-l65536/). Its `files.f` is relative to that directory; the top is `ExtensionFieldVector`.

## 5. Generation-time configuration

The following command independently verifies the 32-context configuration in a new output directory:

```sh
python3 tools/verify.py --out build/verify-ctx32 --contexts 32 --jobs 8
```

`--max-length` supports 16–65,536 and `--contexts` supports 16 or 32. Configuration changes capacity and scheduling, not the field, operation modes, or port widths. Bundled SV uses 16 contexts and maximum length 65,536; other configurations verify the regenerated product rather than combining it with a different bundled configuration.

## 6. Preserve results and diagnose failures

Outputs must use a new relative subdirectory inside `build`. Existing results are never overwritten; choose a different `--out` to repeat a run. On failure, inspect the current reference, emit, lint, compile, or individual-test log, rather than supplying dependencies from an external internal project. C++ drives only clock, reset, and test selection; native-port stimulus and comparisons are performed by the Chisel environment.
