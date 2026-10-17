<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Extension-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic over the quadratic extension defined by u squared = 7.
Repository: https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks
File: README.md

SPDX-FileCopyrightText: 2026 Goldilocks extension-field vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Goldilocks Quadratic Extension Field Vector Hardware Accelerator

[简体中文](README.zh-CN.md)

This repository provides a standalone Goldilocks quadratic-extension hardware accelerator developed in Chisel, combining vector addition, subtraction, multiplication, squaring, inversion, multiply-add, and dot products behind a native interface. It can be integrated into ASIC or FPGA designs for extension-field challenge arithmetic, polynomial evaluation, linear combinations, and related computations in zero-knowledge systems using the quadratic extension `u²=7`.

It is licensed under the [MIT License](LICENSE), permitting free commercial use, modification, and redistribution, provided the copyright and license notices are retained; see [Third-party notices](THIRD_PARTY_NOTICES.en.md) for third-party software information.

[Release v1.1.0](https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks/releases/tag/v1.1.0) · [Native interface](docs/interface.en.md) · [Reproduction](docs/reproduction.en.md) · [Performance](docs/performance.en.md) · [Synthesis summary](docs/synthesis-summary.en.md)

## Computation and task units

An extension element is x = x0 + x1 * u with u squared = 7. Its ordered pair (x0, x1) contains canonical unsigned 64-bit coefficients in the Goldilocks base field, 0 <= xi < p for p = 2^64 - 2^32 + 1; the constant coefficient comes first. This is a quadratic extension, not a cubic extension or a single 128-bit integer modulo p.

One task selects an operation and vector length L. Each data beat supplies the extension operands a, b and c for one index, using their two coefficients; every supplied coefficient must be canonical even when that operand is unused. ADD/SUB/MUL compute a+b, a-b or a*b; SQUARE computes a*a; FMA computes a*b+c over the field. These modes return L elements. INV returns each a inverse, with zero results and flags for zero inputs; DOT sums all a*b products and returns one extension element, with zero for an empty dot product.

One lane is one element datapath. The 16 inverse contexts hold different elements of the same INV task and interleave use of one base-field multiplier; ordinary extension multiplication instead uses three parallel base-field multipliers for one extension result. Context count and multiplier count are not counts of concurrent vector tasks. Tasks are serialized. Element II = 1 means one new element operation can enter the ordinary pipeline each cycle under ideal conditions, not one entire vector or inverse per cycle.

## At a glance

| Integration question | Released hardware |
| --- | --- |
| Computation | ADD, SUB, MUL, SQUARE, INV, FMA and DOT in Fp[u] / (u^2 - 7) |
| Representation | Two canonical 64-bit Goldilocks coefficients per extension element; quadratic, not cubic |
| Bundled configuration | One element lane, vector length up to 65,536, 16 inverse contexts |
| Product top | `ExtensionFieldVector`; three base-field multiplier pipelines for ordinary multiplication |
| Native boundary | Task, ordered a/b/c input, tagged/indexed result and completion ready/valid channels |

## Quick start

Download the source archive from [Release v1.1.0](https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks/releases/tag/v1.1.0), or clone the current release snapshot:

```bash
git clone --branch v1.1.0 --depth 1 https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks.git
cd extension-field-vector-goldilocks
```

Install the exact prerequisites in [Toolchain](docs/toolchain.en.md), then run from the repository root with a fresh output directory:

```bash
python3 tools/emit_sv.py --out build/emit --max-length 65536 --contexts 16
```

This emission command is not a complete RTL regression. The documented verification entry is:

```bash
python3 tools/verify.py --out build/verify --jobs 8
```

Follow [Reproduction](docs/reproduction.en.md) for expected results, negative-control interpretation and the distinction between short functional and full performance runs.

## Verification and integration boundary

The quick default has 92 tasks and six simulation categories: ideal, stalls, ordinary reset, deliberate-reference fault, INV reset and DOT reset. C/Python arithmetic cross-checks and generated/bundled product equality are documented. The separate --large run has 444 tasks and 411,963 checked result records per test, and reproduces long-vector, repeated-task and nonzero-inversion performance.

Only the quadratic extension u squared = 7 is implemented; cubic fields and other moduli are not drop-in compatible. INV uses context interleaving and zero flags, while DOT reduces to one extension element. Ordinary multiplication II=1 does not imply one complete inverse or vector task per cycle. Complete tasks are serialized and outputs retain index order; target protocols and data movement remain external.

### Reading performance correctly

Under ideal native interfaces, a 65,536-element MUL task completes in 65,579 cycles, including pipeline fill/drain and completion. Repeated 32-element MUL tasks finish 76 cycles apart, so their task rate is not the long-vector element rate. In the long MUL test each base-field multiplier has some valid arithmetic stage in 65,549 / 65,579 task-window cycles = 99.9543%; this occupancy differs from useful-issue utilization of 65,536 / 65,579 = 99.9344%. The three multipliers cooperate on each extension result rather than producing three extension results per cycle.

These are documented RTL cycle measurements under ideal native interfaces. MHz-based time/rate conversions are not board or silicon measurements. The reported Design Compiler results apply only to the documented configuration, library corner and constraints; they do not establish place-and-route signoff or system throughput. See [Performance](docs/performance.en.md) and [Synthesis summary](docs/synthesis-summary.en.md) for conditions.

## Repository contents

- [Chisel design](operators/extension-field-vector/src/main/scala/zk/extension/): product, inverse contexts, and complete verification environment.
- [Shared arithmetic utilities](common/arithmetic/src/main/scala/ntt/asic/): prefix-carry addition and subtraction.
- [Generated SV](rtl/v1.1.0/gl64-e2-lane1-ctx16-l65536/): standalone product with a maximum vector length of 65,536 and 16 inverse contexts.
- [Generation and verification tools](tools/): reference vectors, circuit generation, product lint, functional verification, and performance measurement.
- [C reference model](operators/extension-field-vector/cmodel/) and [Simulation driver](operators/extension-field-vector/sim/): the reproducible verification chain.
- [Architecture](docs/architecture.en.md), [Native interface](docs/interface.en.md), and [Toolchain](docs/toolchain.en.md).

## Applications

The accelerator provides seven arithmetic operations for workloads processing many elements in the Goldilocks quadratic extension `F_p[u]/(u²−7)`. Examples include batched polynomial evaluation involving quadratic-extension random challenges, extension-field linear combinations in protocols such as FRI, and extension-arithmetic steps on the verifier side. These examples do not limit other applications using the same mathematical field.

An integrating system can decompose polynomial evaluation into multiply-add operations, batched linear combinations into dot products, and division into inversion and multiplication. The task, input, output, and completion handshake channels provide the native integration boundary. Complete algorithms and arithmetic over a base field, cubic extension, or another modulus can be composed separately; see [Native interface](docs/interface.en.md) for the ports.

## Performance

Under ideal interfaces, a 65,536-element MUL task completes in 65,579 cycles and the ordinary multiplication datapath has an element initiation interval of one cycle. Repeated 32-element MUL tasks complete every 76 cycles, and all three base-field multiplication pipelines have 99.95% useful occupancy in the long-vector test. Definitions, methodology, and measured values for each mode are in [Performance](docs/performance.en.md).

## Mathematical model

The circuit operates in the quadratic extension with modulus `2^64−2^32+1` and relation `u²=7`, representing each element with two canonical 64-bit coefficients. It provides ADD, SUB, MUL, SQUARE, INV, FMA, and DOT with defined zero-inversion and empty-dot-product behavior. See [Mathematical model](docs/mathematical-model.en.md) for the exact definitions.

## Reproduction

Regenerate the circuit from the published Chisel sources, cross-check a C model against an independent Python reference, and run functional, recovery, and performance tests on both regenerated and bundled SV. The steps are in [Reproduction](docs/reproduction.en.md).

## Synthesis results

The representative configuration passed the 500 MHz setup gate using DC NXT with TSMC 28 nm HPC+ at SSG 0.81 V and −40°C, with clock uncertainty equal to 10% of the period. Total cell area is 0.142596 mm², and 99.91% of covered paths have strictly fewer than 50 Z/ZN logic levels, using the documented count of data-path Z/ZN output pins rather than report lines or clock-tree points. Conditions and results are in [Synthesis summary](docs/synthesis-summary.en.md).

## Publication scope

This repository provides the RTL design and complete simulation reproduction chain. The product exposes native interfaces for integration and does not include a SoC, bus adapter, or board project. For synthesis we provide our checked summary of conditions and key results. We apologize that commercial-tool and process-library licensing restrictions prevent us from distributing the process libraries, original DC scripts, raw reports, mapped netlists, or synthesis snapshots.

## OpenRTL and custom integration

Browse [OpenRTL](https://github.com/OpenRTL-Lab), the [ZK accelerator catalogue](https://github.com/OpenRTL-Lab/zk-accelerators) and [OpenRTL website](https://openrtl.top/) for related modules and the cooperation/contact entry. Custom datapaths, interfaces and target-platform adaptation are optional services; MIT commercial use does not require buying a service. Do not submit confidential specifications in public issues.

## Release sources and integration

The version tag identifies the current source tree, including source notices and documentation. Release downloads provide hardware sources and bundled RTL for this version. Archive annotations and documentation can differ from the online tree. For RTL integration, use the product file list rather than verification environments; use the verification and synthesis documents for supported configurations and measurement conditions.
