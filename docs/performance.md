<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Extension-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic over the quadratic extension defined by u squared = 7.
Repository: https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks
File: docs/performance.md

SPDX-FileCopyrightText: 2026 Goldilocks extension-field vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 性能指标

[English](performance.en.md)

本文件分别报告任务延迟、连续任务吞吐率和计算单元有效忙率：理想接口下长向量乘法接近每拍一个扩域元素，三条基域模乘流水线的任务内有效占用率达到 99.95%；全部值由公开仿真链路按时钟计数复现。

L 为每任务扩域元素数，每元素含两个基域系数。ADD/SUB/MUL/SQUARE/INV/FMA 输出 L 个元素，DOT 归约输出一个元素；II 是元素启动间隔。16个求逆上下文把同一任务的元素交错送入第0条基域模乘，不是16个任务或16条模乘。

## 定义与测量

典型配置为最大长度 65,536、单元素通路、16 个求逆上下文。测量使用无外部存储带宽/延迟限制的原生接口；验证环境有数据即提供，输出与完成接收方始终 ready，产品自己的队列和上下文准入照常生效。

对任务 j，A 为 task 握手周期，F 为首个输出握手周期，O 为最后输出握手周期，C 为 completion 握手周期。任务延迟定义为 C−A，结果可用延迟为 O−A；空无输出任务不定义结果延迟。不是仿真机器的墙钟运行时长。

连续任务批次共 K 个同模式同长度任务，以最后和第一个完成时刻的差值 T=C_last−C_first 测量任务吞吐率 `(K−1)/T`，输入元素吞吐率为 `L×(K−1)/T`。DOT 的输出元素吞吐率等于任务吞吐率。不由单个任务延迟倒推批次实测值。

有效发射忙率为窗口内实际被使用的模乘发射拍数除以 C−A；有效占用忙率为这些发射的 14 拍算术阶段区间并集长度除以 C−A。占用忙拍加空闲拍等于任务窗口。未携带有效数据的寄存器翻转不算工作；入口寄存器另计入 15 拍反馈延迟，不重复计入算术占用。

## 长任务延迟

| 运算与输入 | 输入元素数 | 任务延迟（拍） | 最后结果延迟（拍） |
| --- | ---: | ---: | ---: |
| ADD / SUB / MUL / SQUARE / FMA | 65,536 | 65,579 | 65,578 |
| DOT | 65,536 | 65,596 | 65,595 |
| INV，全部非零 | 4,096 | 552,987 | 552,986 |
| INV，全部为零 | 65,536 | 131,076 | 131,075 |

普通元素算术通路 II=1。INV 的全零测试是零处理边界覆盖，不能作为非零求逆性能。非零 INV 的长任务实测长度为 4,096。

## 连续任务吞吐率

| 模式 | 每任务长度 | 批次任务数 | 单任务延迟（拍） | 完成间隔（拍） | 任务/拍 | 输入元素/拍 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| ADD / SUB / MUL / SQUARE / FMA | 32 | 各 64 | 75 | 76 | 0.0131579 | 0.421053 |
| DOT | 32 | 64 | 92 | 93 | 0.0107527 | 0.344086 |
| INV，全部非零 | 256 | 16 | 34,587 | 34,588 | 0.0000289118 | 0.00740141 |

当前产品一个任务在途，任务之间不重叠；以上完成间隔包含任务释放和重新准入。延迟与吞吐率分别测量，内部元素流水线持续复用并不表示任务级并行。

## 计算单元忙率

| 工作负载 | 计算单元 | 有效发射拍 | 有效占用拍 | 空闲拍 | 发射忙率 | 占用忙率 |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| MUL / SQUARE / FMA，L=65,536 | 三条基域模乘，每条 | 65,536 | 65,549 | 30 | 99.9344% | 99.9543% |
| DOT，L=65,536 | 三条基域模乘，每条 | 65,536 | 65,549 | 47 | 99.9085% | 99.9283% |
| INV，4,096 个非零元素 | 第 0 条基域模乘 | 536,576 | 551,392 | 1,595 | 97.0323% | 99.7116% |
| INV，同上 | 第 1、2 条基域模乘，每条 | 0 | 0 | 552,987 | 0% | 0% |

ADD/SUB 与全零 INV 没有有效模乘发射。普通乘法计算段中的连续发射达到每拍一次；任务窗口数值包括填充与排空。统计工具还逐任务输出预加、后处理、FMA、旁路、DOT 累加/三级合并、INV 范数/取负共 17 组逻辑算术工作，记录组内字段运算数量、忙拍、空闲拍、任务内和计算段内忙率。完整数值由 `performance.json` 提供，避免将组并行字段数误当成流水线拍数。

## 周期与时间换算

任意给定时钟 f 下，时间为 `cycles/f`，吞吐率为 `tasks_per_clock×f`。按已通过 setup 门禁的 500 MHz 参数计算：65,536 元素 MUL 任务为 131.158 µs，连续 32 元素 MUL 为 6.57895 百万任务/秒、210.526 百万元素/秒；这些是周期结果的频率换算，不是新增的物理实测。综合条件见 [综合摘要](synthesis-summary.md)。

## 如何复测

完整批次包含 444 个任务、411,963 个结果记录，覆盖七种运算、长度边界、错误帧、零逆元和连续批次。重新生成产品与随包产品分别运行理想、背压、三类复位恢复及负向参考测试；负向测试故意改变一个期望值，必须检出一个错误并返回非零。32 上下文另行复测功能，但不冒充随包 16 上下文的性能结果。

执行 [快速复现](reproduction.md) 的完整性能命令，检查 `results.json` 和两个产品的 `performance.json`。工具直接比较两份性能结果相等；每个时钟标记和 valid 计数来自仿真环境，产品不含统计端口。
