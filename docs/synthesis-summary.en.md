<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Extension-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic over the quadratic extension defined by u squared = 7.
Repository: https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks
File: docs/synthesis-summary.en.md

SPDX-FileCopyrightText: 2026 Goldilocks extension-field vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Synthesis summary

[简体中文](synthesis-summary.md)

The representative configuration passed the 500 MHz setup gate at the archived worst TSMC 28 nm HPC+ process corner, with clock uncertainty equal to 10% of the period, zero setup-violating endpoints, total cell area of 0.142596 mm², and 99.91% of covered paths strictly below 50 Z/ZN logic levels. This document summarizes the synthesis conditions and key results we checked.

Inverse contexts are state slots for elements of one task sharing base-field multiplier 0; they are not additional vector lanes. WNS is the worst setup slack; TNS sums negative setup slacks.

## Object and conditions

| Item | Condition |
| --- | --- |
| Product | ExtensionFieldVector; maximum length 65,536, 16 inverse contexts, one element datapath |
| Process library | TSMC 28 nm HPC+, default-threshold standard cells |
| Corner | SSG, 0.81 V, −40°C |
| Tool | Synopsys Design Compiler NXT X-2025.06-SP3 |
| Clock | 2 ns period, 500 MHz |
| Setup clock uncertainty | 0.20 ns, 10% of period |
| Clock transition | 0.10 ns |
| Maximum data transition | 0.25 ns |
| Input transition | 0.125 ns |
| I/O delay | Maximum 1 ns, minimum 0 ns, including synchronous reset input |
| Output load | 0.02 in library capacitance units |
| Maximum fanout | 32 |
| Method | compile_ultra; no automatic ungrouping or boundary optimization; zero wire-load model |
| Storage mapping | Standard-cell registers; no SRAM macros or black boxes |

## Setup and area

| Metric | Result |
| --- | ---: |
| Setup WNS | +0.000016034 ns |
| Setup TNS | 0 ns |
| Setup-violating endpoints | 0 |
| Total cell area | 142,595.979 µm² (0.142596 mm²) |
| Noncombinational cell area | 91,413.420 µm² |
| Combinational cell area, including buffers/inverters | 51,182.558 µm² |
| Buffer/inverter portion | 3,048.094 µm² |
| SRAM macros / black boxes | 0 |

Area is mapped cell area, not chip core area. Combinational area already includes buffers/inverters. Hierarchical subtotals include 30,179.002 µm² for the output queue, 30,030.630 µm² for inverse contexts, and approximately 9,909–9,915 µm² per base-field multiplier. Parent and child subtotals must not be added together.

## Logic-depth gate

One worst max data path is extracted for each of 55,113 constrained setup endpoints. A level is counted only on an exact leaf-cell output pin named `Z` or `ZN` along the data path, not by report rows, clock-path length, or arbitrary pin names.

| Metric | Result |
| --- | ---: |
| Covered paths | 55,113 |
| Internal to internal | 47,493 |
| Input to internal | 7,369 |
| Internal to output | 251 |
| Strictly below 50 levels | 55,064 (99.9111%) |
| Exactly 50 levels | 6 |
| Above 50 levels | 43 |
| Maximum depth | 60 |

Both setup and the requirement that most paths have fewer than 50 levels are satisfied. This publication gate evaluates setup and that depth distribution; hold is not its acceptance criterion.

## Distribution scope

We provide this checked synthesis summary together with the public RTL/simulation reproduction chain. We apologize that commercial-tool and process-library licensing restrictions prevent distribution of libraries, original synthesis Tcl/SDC, raw DC reports, mapped netlists, or synthesis snapshots. Public simulation can be run as described in [Reproduction](reproduction.en.md).
