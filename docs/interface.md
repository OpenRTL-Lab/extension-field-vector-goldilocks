<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Extension-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic over the quadratic extension defined by u squared = 7.
Repository: https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks
File: docs/interface.md

SPDX-FileCopyrightText: 2026 Goldilocks extension-field vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 原生接口

[English](interface.en.md)

本文给出产品顶层 `ExtensionFieldVector` 的四个 ready/valid 通道、数据格式、状态码和恢复规则，供集成方直接构造任务与数据流；数学运算身份见 [数学模型](mathematical-model.md)。

操作数 a 表示 a0+a1*u，u²=7，b、c 同理。每个系数为无符号规范整数 0 ≤ x < p，p = 2^64 - 2^32 + 1，不是拼接的128位残数。L=length 为扩域元素数（每元素一个输入拍），0 ≤ L ≤ maxLength。求逆上下文保存同一 INV 任务的不同元素状态，共用一条基域模乘，不是并行向量任务。

## 时钟与握手

产品使用单时钟 `clock` 和同步高有效 `reset`。`io_enable` 仅控制新任务准入，不暂停在途任务。通道仅在时钟沿 `valid && ready` 时传输；发送方在等待 `ready` 时保持 `valid` 和全部 payload。结果顺序与输入自然索引一致。

同一时刻仅一个任务在途，前任务的 completion 握手后才能接受新任务。元素计算内部有流水线，任务本身不重叠。最大任务长度由生成参数 `maxLength` 决定，范围 16–65,536；任务 length 允许 0 和非 2 的幂。随包配置为最大长度 65,536、16 个求逆上下文。

## 控制面

任务通道为 `io_task_valid/ready`，payload 使用 `io_task_bits_` 前缀：

| 字段 | 位宽 | 含义 |
| --- | ---: | --- |
| tag | 32 | 用户任务标识，结果与完成原样返回 |
| mode | 3 | 0 ADD、1 SUB、2 MUL、3 SQUARE、4 INV、5 FMA、6 DOT；7 非法 |
| length | 17 | 本任务准确输入拍数 |
| baseOperand | 1 | 为 1 时要求每拍 b1=0，表示 b 属于基域 |

完成通道为 `io_completion_valid/ready`，payload 使用 `io_completion_bits_` 前缀：

| 字段 | 位宽 | 含义 |
| --- | ---: | --- |
| tag | 32 | 原始任务标签 |
| status | 2 | 下表状态 |
| processed | 17 | 已接受输入拍数；非法参数为 0 |
| zeroCount | 17 | INV 中原始 a=(0,0) 的数量 |

| status | 名称 | 行为 |
| ---: | --- | --- |
| 0 | OK | 合法任务与数据，结果满足数学定义 |
| 1 | BAD_PARAM | mode=7 或长度超上限，不接受数据、无输出 |
| 2 | ZERO_DENOM | INV 存在原始零输入；该位置输出零并置 zeroFlag |
| 3 | BAD_FRAME | last 位置错误、系数非规范，或基域模式下 b1 非零 |

完成信号在最后一个结果被接受后有效；空非 DOT 及非法参数任务无需结果。BAD_FRAME 优先于 ZERO_DENOM。发生帧错误仍接收恰好 length 拍，早到的 last 不截断任务。非规范或错误基域嵌入的该拍 a、b、c 全部替换为零；只有 last 错误时仍计算原数据。错误状态的结果不作为成功数学结果使用。zeroCount 统计原始零，不把安全替换导致的零计入。

## 数据面

输入通道 `io_input_valid/ready` 的 `io_input_bits_` 字段为 `a0/a1/b0/b1/c0/c1`，各 64 位，以及 `last`，1 位。每拍携带一个完整二次扩域元素运算的操作数；即使某模式不使用 b 或 c，其系数仍需满足 $0\leq x<p$。最后且仅最后一拍 last=1。

输出通道 `io_output_valid/ready` 的 `io_output_bits_` 字段如下：

| 字段 | 位宽 | 含义 |
| --- | ---: | --- |
| tag | 32 | 任务标签 |
| index | 17 | 自然元素索引，从 0 起；DOT 为 0 |
| r0/r1 | 各 64 | 规范结果系数，常数系数在前 |
| last | 1 | 最后一个结果；DOT 唯一结果为 1 |
| zeroFlag | 1 | INV 零输入的结果标志；其他模式为 0 |

## 背压、存储与复位

输出队列是 64×179 位有限容量数组，一同步写、一组合读，无数据初始化和复位清数组。控制有效位在 reset 时清空；所有在途任务、数据和完成消息被取消，集成方重新提交完整任务。队列数组的组合读语义应保持；若集成同步读 SRAM，需要相应握手/延迟适配。

非 DOT 元素按队列信用控制准入；INV 还受有限上下文影响，input.ready 可能下降。enable 下降不丢失正在执行的数据。握手信号本身定义供数速率，集成方无需假设接口始终 ready。
