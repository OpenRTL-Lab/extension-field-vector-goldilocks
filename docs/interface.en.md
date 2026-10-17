<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Extension-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic over the quadratic extension defined by u squared = 7.
Repository: https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks
File: docs/interface.en.md

SPDX-FileCopyrightText: 2026 Goldilocks extension-field vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Native interface

[简体中文](interface.md)

This document defines the four ready/valid channels of product top `ExtensionFieldVector`, including data formats, status codes, and recovery rules for direct task and stream integration. The exact arithmetic is specified in [Mathematical model](mathematical-model.en.md).

An operand a is a0+a1*u with u^2=7, and likewise for b and c. Each coefficient is an unsigned canonical integer 0 <= x < p, p = 2^64 - 2^32 + 1; it is not a packed 128-bit residue. L=length counts extension elements (one input beat each), with 0 <= L <= maxLength. Inverse contexts hold different elements of the same INV task sharing one base-field multiplier; they are not concurrent vector tasks.

## Clock and handshakes

The product uses one `clock` and synchronous active-high `reset`. `io_enable` controls admission of new tasks, not progress of an accepted task. A channel transfers only at an edge where `valid && ready`; the sender holds valid and every payload field while waiting. Results retain natural input-index order.

Only one task is in flight. A new task can be accepted after the previous completion handshake. Elements are pipelined internally; tasks do not overlap. The generation parameter `maxLength` ranges from 16 to 65,536, while task length may be zero or a non-power-of-two value. The bundled configuration has maximum length 65,536 and 16 inverse contexts.

## Control plane

The task channel is `io_task_valid/ready`; payload fields have prefix `io_task_bits_`:

| Field | Bits | Meaning |
| --- | ---: | --- |
| tag | 32 | User task identifier, returned unchanged |
| mode | 3 | 0 ADD, 1 SUB, 2 MUL, 3 SQUARE, 4 INV, 5 FMA, 6 DOT; 7 invalid |
| length | 17 | Exact input beat count |
| baseOperand | 1 | When 1, requires b1=0 on every beat, embedding b in the base field |

The completion channel is `io_completion_valid/ready`; payload fields have prefix `io_completion_bits_`:

| Field | Bits | Meaning |
| --- | ---: | --- |
| tag | 32 | Original task tag |
| status | 2 | Status defined below |
| processed | 17 | Accepted input beats; 0 for invalid parameters |
| zeroCount | 17 | Original a=(0,0) elements in INV |

| status | Name | Behavior |
| ---: | --- | --- |
| 0 | OK | Valid task/data; results follow the mathematical definition |
| 1 | BAD_PARAM | mode=7 or excessive length; no input consumed and no result |
| 2 | ZERO_DENOM | INV includes original zero inputs, whose results are zero with zeroFlag |
| 3 | BAD_FRAME | Incorrect last position, noncanonical coefficient, or nonzero b1 in base mode |

Completion becomes valid after acceptance of the last result. Empty non-DOT and invalid-parameter tasks require no result. BAD_FRAME takes precedence over ZERO_DENOM. A bad frame still consumes exactly length beats; early last does not truncate it. A noncanonical or invalid base embedding replaces a, b, and c of that beat with zero. A last-only error preserves the original arithmetic data. Do not treat bad-status outputs as successful mathematical results. zeroCount counts original zeros, not zeros introduced by safe replacement.

## Data plane

The `io_input_valid/ready` channel has `io_input_bits_` fields `a0/a1/b0/b1/c0/c1`, each 64 bits, and one-bit `last`. A beat carries the operands of one complete quadratic-extension element operation. All six coefficients must satisfy $0\leq x<p$, even when b or c is unused. Only the final beat has last=1.

The `io_output_valid/ready` channel has these `io_output_bits_` fields:

| Field | Bits | Meaning |
| --- | ---: | --- |
| tag | 32 | Task tag |
| index | 17 | Natural element index starting at 0; DOT uses 0 |
| r0/r1 | 64 each | Canonical result, constant coefficient first |
| last | 1 | Final result; 1 for the sole DOT result |
| zeroFlag | 1 | INV zero-input indication; 0 in other modes |

## Backpressure, storage, and reset

The output queue is a finite 64×179-bit array with synchronous writes and combinational reads. Data is not initialized or cleared by reset. Reset clears control validity and cancels all in-flight tasks, data, and completion messages; the system resubmits the complete task. Preserve the queue's combinational-read semantics; synchronous-read SRAM integration requires handshake/latency adaptation.

Non-DOT input admission uses queue credits; INV also depends on finite context availability, so input.ready may drop. Deasserting enable does not discard in-flight data. Handshakes define the supply rate without assuming the interface is always ready.
