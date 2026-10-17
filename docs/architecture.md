<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Extension-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic over the quadratic extension defined by u squared = 7.
Repository: https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks
File: docs/architecture.md

SPDX-FileCopyrightText: 2026 Goldilocks extension-field vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 架构说明

[English](architecture.en.md)

本加速器以三条基域模乘流水线实现二次扩域乘法，以交织上下文复用其中一条流水线完成求逆，并用条带化累加保持点积的连续元素供给；单任务、统一接口的结构便于独立集成和验证。

扩域元素 a=a0+a1*u 含两个规范基域系数，u²=7、p=2^64-2^32+1；b、c 和结果 r 使用同一系数顺序。II 为元素启动间隔，不是完整向量间隔；公式定义见 [数学模型](mathematical-model.md)。

## 普通元素通路

任务入口锁存模式、长度和标签。数据经过两级弹性入口与规范性检查，再进入普通算术通路；后者延迟 39 拍、元素启动间隔 II=1。其分段加减法每次 4 拍，基域模乘含入口寄存器的反馈延迟为 15 拍，其中有效算术覆盖 14 拍。

扩域乘法使用三个基域乘积：$x=a_0b_0$、$y=a_1b_1$、$z=(a_0+a_1)(b_0+b_1)$，随后计算 $r_0=x+7y$、$r_1=z-x-y$。FMA 再加 c。每条基域模乘通过四个 32×32 整数部分积构造 64×64 乘法，三条通路共 12 个整数部分积。约简利用 Goldilocks 模数结构，不调用 FPGA DSP/RAM 原语。

ADD/SUB 走对齐后的加减旁路，SQUARE 将 b 替换为 a；普通模式共用结果排序及队列。`ntt.asic` 名称下的公共源码只提供前缀进位算术工具，本仓库不依赖 NTT 产品。

## DOT

DOT 将各元素乘积累加成一个扩域元素；条带是独立部分和寄存器，不是输入通道。每个系数设置八条累加条带，连续结果轮流进入各条带，相同条带间隔 8 拍，覆盖 4 拍加法反馈。任务尾部通过三级合并得到单个扩域结果，包含 17 拍尾部处理。点积无需保存整个向量，输出为一个元素。

## INV

范数 n(a)=a0²−7*a1² 属于基域，用其逆乘 (a0−a1*u) 得到 a 的逆；上下文保存单元素求逆的中间状态，不是向量任务。INV 使用 16 或 32 个生成期配置的上下文，按序交织，并复用第一条基域模乘流水线。每个非零元素需要 131 次有效基域模乘发射：范数的两次平方、64 次平方、63 次乘法，以及输出系数的两次乘法。范数处理为 17 拍，最终取负为 4 拍；上下文调度满足 15 拍模乘反馈依赖。其余两条模乘通路不参与 INV 的有效工作。

输出保持自然索引顺序；上下文占用和输出队列信用约束输入准入。零元素走专门处理并返回零标志，非零元素走范数求逆计算。

## 存储和控制

产品仅有固定容量 64 条结果队列，数据位为 11,456 位，加上上下文和流水寄存器；无整向量 SRAM、初始化 ROM、SoC 或板级依赖。当前小型数组是组合读寄存器存储契约。任务在完成握手后才释放，因此“元素流水线 II=1”与“连续任务吞吐率”是两个不同指标，具体见 [性能指标](performance.md)。

## 验证边界

Chisel 验证环境负责理想供数、比较、背压、协议与复位测试，C++ 驱动只推进时钟、复位和测试选择。环境通过层级信号统计有 valid 的算术工作，不为产品增加测试探针端口。产品 SV 文件清单不含参考 ROM 或仿真顶层。
