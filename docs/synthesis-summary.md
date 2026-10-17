<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Extension-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic over the quadratic extension defined by u squared = 7.
Repository: https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks
File: docs/synthesis-summary.md

SPDX-FileCopyrightText: 2026 Goldilocks extension-field vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 综合摘要

[English](synthesis-summary.en.md)

典型配置已通过 TSMC 28 nm HPC+ 最差归档工艺角的 500 MHz setup 门禁：时钟不确定度为周期的 10%，setup 违例端点为零，总 cell 面积 0.142596 mm²，99.91% 的覆盖路径 Z/ZN 逻辑级数严格小于 50；本文仅转述我们核对的综合条件和关键结果。

求逆上下文是同一任务元素的状态槽，共用第0条基域模乘，不是额外向量通道。WNS 为最差 setup 裕量，TNS 为负 setup 裕量之和。

## 综合对象与条件

| 项目 | 条件 |
| --- | --- |
| 产品 | ExtensionFieldVector；最大长度 65,536，16 个求逆上下文，单元素通路 |
| 工艺库 | TSMC 28 nm HPC+，标准单元默认阈值库 |
| 工艺角 | SSG，0.81 V，−40°C |
| 综合工具 | Synopsys Design Compiler NXT X-2025.06-SP3 |
| 时钟 | 周期 2 ns，500 MHz |
| Setup 时钟不确定度 | 0.20 ns，即周期的 10% |
| 时钟 transition | 0.10 ns |
| 最大数据 transition | 0.25 ns |
| 输入 transition | 0.125 ns |
| I/O 延迟 | 最大 1 ns，最小 0 ns，含同步复位输入 |
| 输出负载 | 0.02，使用库的电容单位 |
| 最大扇出 | 32 |
| 综合方式 | compile_ultra，禁止自动解层次及边界优化，零 wire-load 模型 |
| 存储映射 | 标准单元寄存器；无 SRAM 宏或黑盒 |

## Setup 与面积

| 指标 | 结果 |
| --- | ---: |
| Setup WNS | +0.000016034 ns |
| Setup TNS | 0 ns |
| Setup 违例端点 | 0 |
| 总 cell 面积 | 142,595.979 µm²（0.142596 mm²） |
| 非组合 cell 面积 | 91,413.420 µm² |
| 组合 cell 面积，含 buffer/inverter | 51,182.558 µm² |
| 其中 buffer/inverter 面积 | 3,048.094 µm² |
| SRAM 宏 / 黑盒数量 | 0 |

面积口径是映射 cell 面积，不是芯片核心面积。组合面积已包含 buffer/inverter，不应重复相加。层次面积中结果队列为 30,179.002 µm²，求逆上下文为 30,030.630 µm²，三条基域模乘各约 9,909–9,915 µm²；父子层次面积不相加。

## 逻辑级数门禁

对全部 55,113 个受约束 setup 端点各提取一条最差 max 数据路径，在路径上遇到精确叶单元输出引脚 `Z` 或 `ZN` 才计一级。不按报告行数、时钟路径长度或任意引脚名字计级。

| 指标 | 结果 |
| --- | ---: |
| 覆盖路径 | 55,113 |
| 内部到内部 | 47,493 |
| 输入到内部 | 7,369 |
| 内部到输出 | 251 |
| 严格少于 50 级 | 55,064（99.9111%） |
| 恰为 50 级 | 6 |
| 超过 50 级 | 43 |
| 最大级数 | 60 |

因此 setup 与“大部分逻辑级数小于 50”均达标。本门禁评价 setup 与上述逻辑深度，不以 hold 作为该配置验收判据。

## 提供范围

我们提供本文综合转述和公开 RTL/仿真复现链路。很抱歉，受商业工具与工艺库授权约束，不能提供工艺库、原始综合 Tcl/SDC、原始 DC 报告、映射网表或综合快照。公开仿真可直接按 [快速复现](reproduction.md) 执行。
