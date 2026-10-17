<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Extension-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic over the quadratic extension defined by u squared = 7.
Repository: https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks
File: docs/architecture.en.md

SPDX-FileCopyrightText: 2026 Goldilocks extension-field vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Architecture

[简体中文](architecture.md)

The accelerator uses three pipelined base-field multipliers for quadratic-extension multiplication, interleaved contexts sharing one multiplier for inversion, and striped accumulation for continuous dot-product input. A single-task native interface makes the product straightforward to integrate and verify independently.

An extension element a=a0+a1*u has two canonical base-field coefficients, with u^2=7 and p=2^64-2^32+1. b, c and result r use the same order. II denotes element initiation interval, not complete-vector interval. The arithmetic formulas are defined in [Mathematical model](mathematical-model.en.md).

## Ordinary element datapath

The task input captures mode, length, and tag. Data passes through two elastic ingress stages and canonicality checks before the ordinary arithmetic datapath, which has a 39-cycle latency and element II=1. Each staged field addition/subtraction takes four cycles. A base-field multiplier has a 15-cycle feedback latency including its entry register, with 14 arithmetic occupancy stages.

Extension multiplication uses three base-field products: $x=a_0b_0$, $y=a_1b_1$, and $z=(a_0+a_1)(b_0+b_1)$, then $r_0=x+7y$ and $r_1=z-x-y$. FMA adds c. Each base-field multiplier constructs its 64×64 product from four 32×32 integer partial products; the three datapaths contain 12 such partial products. Reduction uses the Goldilocks modulus structure without FPGA DSP/RAM primitives.

ADD/SUB use aligned arithmetic bypasses; SQUARE substitutes a for b. Ordinary modes share ordering and output queue logic. The shared sources under the namespace `ntt.asic` provide only prefix-carry arithmetic utilities, not an NTT product dependency.

## DOT

DOT sums all element products into one extension element. A stripe is an independent partial-sum register, not an input lane. Each coefficient has eight accumulator stripes. Consecutive results rotate between stripes; an eight-cycle recurrence interval covers the four-cycle adder feedback. Three merge levels produce the final extension result, with 17 cycles of tail processing. No complete vector is stored, and the output contains one element.

## INV

The norm is n(a)=a0^2-7*a1^2 in the base field, used to form a inverse as n(a) inverse times (a0-a1*u). A context stores one element's intermediate inverse state, not a vector task. INV has 16 or 32 generation-time contexts and interleaves them while sharing the first base-field multiplier. Each nonzero element issues 131 useful base-field multiplications: two norm squares, 64 squares, 63 multiplications, and two output-coefficient multiplications. Norm processing takes 17 cycles and final negation four cycles. Scheduling respects the multiplier's 15-cycle feedback dependency. The other two multiplication pipelines do not perform useful INV work.

Output retains natural-index order, while context occupancy and queue credits constrain admission. Zero elements use dedicated handling and return a zero flag; nonzero elements follow norm-based inversion.

## Storage and control

The product has a fixed 64-record result queue with 11,456 data bits, plus contexts and pipeline registers. It has no whole-vector SRAM, initialized ROM, SoC, or board dependency. The small array currently specifies combinational-read register storage. A task releases only after its completion handshake, so element II=1 and repeated-task throughput are separate metrics; see [Performance](performance.en.md).

## Verification boundary

The Chisel environment supplies ideal inputs, compares outputs, and performs backpressure, protocol, and reset tests. C++ advances clock/reset and selects tests. Hierarchical signals count valid arithmetic work without adding probe ports to the product. The product SV file list excludes reference ROMs and simulation tops.
