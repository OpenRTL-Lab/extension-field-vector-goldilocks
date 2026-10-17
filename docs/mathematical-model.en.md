<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Extension-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic over the quadratic extension defined by u squared = 7.
Repository: https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks
File: docs/mathematical-model.en.md

SPDX-FileCopyrightText: 2026 Goldilocks extension-field vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Mathematical model

[简体中文](mathematical-model.md)

This document precisely defines the Goldilocks quadratic extension, coefficient encoding, and seven operations implemented by the accelerator. Canonical field elements give the software reference and hardware identical, unambiguous input/output semantics.

## Field and representation

Let

$$p=2^{64}-2^{32}+1=18446744069414584321,\qquad F=\mathbb{F}_p,\qquad E=F[u]/(u^2-7).$$

In $F$, $7^{(p-1)/2}=-1$. Therefore 7 is a quadratic nonresidue, $u^2-7$ is irreducible, and $E$ is a field with $p^2$ elements. Each element has a unique representation $a=a_0+a_1u$ with $0\leq a_0,a_1<p$. Ports list the constant coefficient first and the degree-one coefficient second, using 64 bits per coefficient. The native ports are not a byte protocol; reference vectors encode unsigned field words in increasing coefficient degree.

All coefficient operations below take place in $F$, and hardware outputs canonical representatives. Write $b=b_0+b_1u$ and $c=c_0+c_1u$.

## Element operations

$$a+b=(a_0+b_0)+(a_1+b_1)u,\qquad a-b=(a_0-b_0)+(a_1-b_1)u.$$

$$ab=(a_0b_0+7a_1b_1)+(a_0b_1+a_1b_0)u.$$

$$a^2=(a_0^2+7a_1^2)+(2a_0a_1)u.$$

Define $n(a)=a_0^2-7a_1^2$. For $a\ne0$, $n(a)\ne0$, hence

$$a^{-1}=n(a)^{p-2}(a_0-a_1u).$$

For vectors, let L be their common extension-element length, 0 <= L <= generated maxLength. In the formulas below, $a_i$ is the entire element at vector index i, with coefficients $a_{i,0}$ and $a_{i,1}$; it is not the coefficient a0 or a1 of a single element. $b_i$ and $c_i$ follow the same convention.

Multiply-add means $ab+c$, an exact finite-field operation without floating-point rounding conventions. The dot product is

$$\mathrm{DOT}(a,b)=\sum_{i=0}^{L-1}a_ib_i\in E.$$

## Task definitions

| mode | Name | Mathematical result | Output count |
| --- | --- | --- | ---: |
| 0 | ADD | $a_i+b_i$ | $L$ |
| 1 | SUB | $a_i-b_i$ | $L$ |
| 2 | MUL | $a_ib_i$ | $L$ |
| 3 | SQUARE | $a_i^2$ | $L$ |
| 4 | INV | $a_i^{-1}$, with the zero convention below | $L$ |
| 5 | FMA | $a_ib_i+c_i$ | $L$ |
| 6 | DOT | $\sum_i a_ib_i$ | 1 |

Empty ADD/SUB/MUL/SQUARE/INV/FMA vectors produce no output; an empty DOT produces one zero element. Zero has no mathematical inverse: hardware emits `(0,0)` with `zeroFlag=1` for that position and reports `ZERO_DENOM`, while processing other nonzero elements normally. Every nonzero INV result satisfies $a_i r_i=1$.

`baseOperand=1` requires every $b_i$ to lie in the embedded base field, so $b_{i,1}=0$. MUL/FMA/DOT retain the same formulas. SQUARE/INV ignore the arithmetic value of b and c, but all six input coefficients must still be canonical and the base-field flag still requires b1=0. Invalid modes, lengths, and frames are handled as specified in [Native interface](interface.en.md).

The definition is independent of a particular proof framework. Computations requiring this p and this extension relation can use the module. Other extension relations or moduli require the corresponding circuit configuration or another operator, not merely a different data tag.

## Independent reference checks

The [C reference model](../operators/extension-field-vector/cmodel/) computes nonzero inverses using extension-field exponentiation by $p^2-2$. The independent Python reference in [Generation and verification tools](../tools/) uses the norm formula and four-term multiplication, checking the Euler nonresidue condition, $u\cdot u=7$, and inverse identities. Both inverse algorithms are separate from the RTL context scheduler.
