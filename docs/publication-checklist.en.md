<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Extension-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic over the quadratic extension defined by u squared = 7.
Repository: https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks
File: docs/publication-checklist.en.md

SPDX-FileCopyrightText: 2026 Goldilocks extension-field vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Publication checklist

[简体中文](publication-checklist.md)

This document summarizes the deliverables and reproduction checks: independent Chisel sources, product SV, reference and simulation chain, bilingual documentation, and MIT licensing. Functional/performance verification and archived synthesis acceptance have corresponding descriptions, allowing users to check product consistency through the published steps.

- The product closure contains five SV modules with top `ExtensionFieldVector`, excluding simulation ROMs, FPGA primitives, SoCs, and board projects.
- Regenerated representative SV matches the bundled modules after removing comments/whitespace; both products pass full functional and performance tests.
- Seven operations, length boundaries, bad frames, and zero inversion have reference checks. Backpressure and three in-flight reset recoveries pass; the negative control detects its intended error.
- The complete performance set contains 444 tasks and 411,963 checked results per test, separately measuring latency, repeated-task throughput, and arithmetic busy/idle cycles.
- Bundled SV uses maximum length 65,536 and 16 inverse contexts; a regenerated 32-context configuration receives a separate functional test.
- The 500 MHz setup gate, 10% clock uncertainty, and Z/ZN depth distribution are described in [Synthesis summary](synthesis-summary.en.md); raw commercial synthesis materials are not bundled.
- Published files exclude machine-specific absolute paths, credentials, internal signatures/provenance digests, internal maintenance records, build logs, and tool caches.
- The default README.md is English and README.zh-CN.md contains the complete Chinese translation. Every authored Markdown has separate English and Chinese files with reciprocal language links; body links point to documentation in the same language.
- The full source archive includes all published files. GitHub-generated commit identifiers are platform version metadata, not internal provenance material.

See [Reproduction](reproduction.en.md) for public verification steps and [Performance](performance.en.md) for measurement definitions.
