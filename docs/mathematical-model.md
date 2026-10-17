<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Extension-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic over the quadratic extension defined by u squared = 7.
Repository: https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks
File: docs/mathematical-model.md

SPDX-FileCopyrightText: 2026 Goldilocks extension-field vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 数学模型

[English](mathematical-model.en.md)

本文严格定义本加速器使用的 Goldilocks 二次扩域、系数编码和七类运算；输入输出均以规范域元素表示，使软件模型与硬件实现具有一致且无歧义的数学含义。

## 域与表示

令

$$p=2^{64}-2^{32}+1=18446744069414584321,\qquad F=\mathbb{F}_p,\qquad E=F[u]/(u^2-7).$$

在 $F$ 中有 $7^{(p-1)/2}=-1$，因此 7 是二次非剩余，$u^2-7$ 不可约，$E$ 是含 $p^2$ 个元素的域。每个元素唯一表示为 $a=a_0+a_1u$，其中 $0\leq a_0,a_1<p$。端口顺序为常数系数在前、一次系数在后；每个系数占 64 位。原生电路端口不是字节协议，参考向量按低次系数优先的无符号域字编码。

以下等式的所有系数运算均在 $F$ 内执行，硬件输出规范代表元。定义 $b=b_0+b_1u$、$c=c_0+c_1u$。

## 元素运算

$$a+b=(a_0+b_0)+(a_1+b_1)u,\qquad a-b=(a_0-b_0)+(a_1-b_1)u.$$

$$ab=(a_0b_0+7a_1b_1)+(a_0b_1+a_1b_0)u.$$

$$a^2=(a_0^2+7a_1^2)+(2a_0a_1)u.$$

令 $n(a)=a_0^2-7a_1^2$。对 $a\ne0$，$n(a)\ne0$，因此

$$a^{-1}=n(a)^{p-2}(a_0-a_1u).$$

对向量，令 L 为共同的扩域元素长度，0 ≤ L ≤ 生成的 maxLength。下文 $a_i$ 指向量索引 i 的完整扩域元素，其两个系数为 $a_{i,0}$、$a_{i,1}$，不是单个元素的系数 a0 或 a1；$b_i$、$c_i$ 同理。

乘加定义为 $ab+c$，它是精确有限域运算，不涉及浮点数的舍入约定。点积定义为

$$\mathrm{DOT}(a,b)=\sum_{i=0}^{L-1}a_ib_i\in E.$$

## 任务定义

| mode | 名称 | 数学结果 | 输出数量 |
| --- | --- | --- | ---: |
| 0 | ADD | $a_i+b_i$ | $L$ |
| 1 | SUB | $a_i-b_i$ | $L$ |
| 2 | MUL | $a_ib_i$ | $L$ |
| 3 | SQUARE | $a_i^2$ | $L$ |
| 4 | INV | $a_i^{-1}$，零元素使用下述约定 | $L$ |
| 5 | FMA | $a_ib_i+c_i$ | $L$ |
| 6 | DOT | $\sum_i a_ib_i$ | 1 |

空向量的 ADD/SUB/MUL/SQUARE/INV/FMA 无输出；空 DOT 输出唯一的零元素。零元素没有数学逆元，硬件为该位置输出 `(0,0)` 和 `zeroFlag=1`，完成状态为 `ZERO_DENOM`，其余非零元素正常求逆。INV 的非零结果满足 $a_i r_i=1$。

`baseOperand=1` 表示每个 $b_i$ 属于嵌入基域，即 $b_{i,1}=0$；MUL/FMA/DOT 使用相同公式。SQUARE/INV 忽略 $b,c$ 的算术用途，但所有六个输入系数仍必须规范，且基域标志仍要求 $b_1=0$。非法模式、长度或数据帧的协议处理见 [原生接口](interface.md)。

本定义不依赖某个证明框架；需要同一 $p$ 和同一 $u^2=7$ 扩域的计算均可使用。其他扩域关系和其他模数应使用对应电路配置或其他算子，而不能仅改变数据标签。

## 参考交叉核对

[C 参考模型](../operators/extension-field-vector/cmodel/) 用扩域乘法及指数 $p^2-2$ 独立计算非零扩域逆元；[生成与验证脚本](../tools/) 中的 Python 参考使用上述范数公式和四项乘法，并检查 Euler 非剩余条件、$u\cdot u=7$ 及逆元恒等式。两种求逆算法与 RTL 的上下文调度实现分离。
