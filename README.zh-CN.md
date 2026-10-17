<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Extension-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic over the quadratic extension defined by u squared = 7.
Repository: https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks
File: README.zh-CN.md

SPDX-FileCopyrightText: 2026 Goldilocks extension-field vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# extension-field-vector-goldilocks 硬件加速器

[English](README.md)

本仓库提供使用 Chisel 开发的 Goldilocks 二次扩域独立硬件加速器，将向量加法、减法、乘法、平方、求逆、乘加和点积集成在统一原生接口中。它可集成到 ASIC 或 FPGA，用于采用 `u²=7` 二次扩域的零知识证明系统中的扩域挑战运算、多项式求值、线性组合及相关计算。

采用 [MIT 许可证](LICENSE)，允许免费商用、修改和再分发，须保留版权及许可声明；第三方软件说明见[第三方声明](THIRD_PARTY_NOTICES.md)。

[Release v1.1.0](https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks/releases/tag/v1.1.0) · [原生接口](docs/interface.md) · [快速复现](docs/reproduction.md) · [性能指标](docs/performance.md) · [综合摘要](docs/synthesis-summary.md)

## 计算与任务单位

扩域元素为 x = x0 + x1 * u，u 的平方为 7。有序对 (x0, x1) 的系数是 Goldilocks 基域中的规范无符号 64 位值，满足 0 <= xi < p，p = 2^64 - 2^32 + 1，常数系数在前。这是二次扩域，不是三次扩域，也不是一个模 p 的 128 位整数。

一个任务选择运算和向量长度 L。每个数据拍提供一个索引的扩域操作数 a、b、c，每个操作数使用两个系数；即使某操作数未使用，所提供的系数仍须规范。ADD/SUB/MUL 计算 a+b、a-b 或 a*b；SQUARE 计算 a*a；FMA 在域上计算 a*b+c。这些模式返回 L 个元素。INV 逐元素返回 a 的逆，零输入返回零结果及标志；DOT 对全部 a*b 乘积求和，返回一个扩域元素，空点积返回零。

单通道指一条元素数据通路。16 个求逆上下文保存同一 INV 任务中不同元素的状态，并交织使用一颗基域模乘；普通扩域乘法则使用三条并行基域模乘流水线产生一个扩域结果。上下文数和模乘数均不是并发向量任务数。任务串行执行。元素启动间隔 II = 1 表示理想条件下普通流水线每拍可接收一个新元素运算，不表示每拍接收一个完整向量或完成一次求逆。

## 配置速览

| 集成问题 | 发布的硬件 |
| --- | --- |
| 计算 | Fp[u] / (u^2 - 7) 上的 ADD、SUB、MUL、SQUARE、INV、FMA 和 DOT |
| 表示 | 每扩域元素含两个规范 64 位 Goldilocks 系数；二次扩域，而非三次 |
| 随包配置 | 一条元素通道，向量长度最多 65,536，16 个求逆上下文 |
| 产品顶层 | `ExtensionFieldVector`；普通乘法使用三条基域模乘流水线 |
| 原生边界 | 任务、有序 a/b/c 输入、带标签/索引的结果及完成 ready/valid 通道 |

## 快速开始

从 [Release v1.1.0](https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks/releases/tag/v1.1.0) 下载源码包，或克隆当前发布快照：

```bash
git clone --branch v1.1.0 --depth 1 https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks.git
cd extension-field-vector-goldilocks
```

按[工具链说明](docs/toolchain.md)安装准确依赖，然后在仓库根目录使用全新输出目录运行：

```bash
python3 tools/emit_sv.py --out build/emit --max-length 65536 --contexts 16
```

该生成命令不是完整 RTL 回归。文档规定的验证入口是：

```bash
python3 tools/verify.py --out build/verify --jobs 8
```

预期结果、负控判读以及短功能验证与完整性能运行的区别见[快速复现](docs/reproduction.md)。

## 验证与集成边界

默认快速验证有 92 个任务、六类仿真：理想、停顿、普通复位、故意破坏参考、INV 复位及 DOT 复位。文档说明了 C/Python 算术交叉核对，以及新生成与随包产品一致性检查。单独的 --large 运行包含 444 个任务、每类测试 411,963 个已核对结果记录，复现长向量、重复任务和非零求逆性能。

仅实现 u 的平方为 7 的二次扩域；三次扩域和其他模数不能直接替换。INV 使用交织上下文和零标志，DOT 归约为一个扩域元素。普通乘法 II=1 不表示每拍完成一次完整求逆或一个向量任务。完整任务串行执行，输出保持索引顺序；目标协议和数据搬运仍由外部负责。

### 正确理解性能

理想原生接口下，65,536 元素的 MUL 任务在 65,579 周期内完成，包含流水线填充/排空及完成通知。重复 32 元素 MUL 任务的完成间隔为 76 周期，因此其任务率不是长向量的元素率。长 MUL 测试中，每条基域模乘在 65,549 / 65,579 个任务窗口周期内至少有一级有效算术，即占用率 99.9543%；这与 65,536 / 65,579 = 99.9344% 的有效发射忙率不同。三条模乘协作产生每个扩域结果，而非每拍产生三个扩域结果。

这些是理想原生接口条件下记录的 RTL 周期测量。按 MHz 换算的时间/速率不是板级或硅后实测。Design Compiler 结果仅适用于文档规定的配置、库工艺角和约束，不代表布局布线签核或系统吞吐率。条件见[性能指标](docs/performance.md)与[综合摘要](docs/synthesis-summary.md)。

## 仓库内容

- [Chisel 设计](operators/extension-field-vector/src/main/scala/zk/extension/)：产品、求逆上下文与完整验证环境。
- [公共算术工具](common/arithmetic/src/main/scala/ntt/asic/)：前缀进位加减法。
- [生成好的 SV](rtl/v1.1.0/gl64-e2-lane1-ctx16-l65536/)：最大向量长度 65,536、16 个求逆上下文的独立产品。
- [生成与验证脚本](tools/)：参考向量、电路生成、产品 lint、功能与性能统计。
- [C 参考模型](operators/extension-field-vector/cmodel/) 与 [仿真驱动](operators/extension-field-vector/sim/)：可复现的完整验证链路。
- [架构说明](docs/architecture.md)、[原生接口](docs/interface.md) 与 [工具链说明](docs/toolchain.md)。

## 应用场景

当证明计算需要在 Goldilocks 域的二次扩域 `F_p[u]/(u²−7)` 中处理大量元素时，本模块可提供七类算术操作。例如，可用于二次扩域随机挑战相关的多项式批量求值、FRI 等协议中的扩域线性组合，以及验证侧的扩域算术子步骤；这些是集成示例，不限制其他使用相同数学域的应用。

上层将多项式求值拆为乘加，将批量线性组合拆为点积，将除法拆为求逆与乘法，即可通过任务、输入、输出和完成四个握手通道调用。完整算法、基域/三次扩域或其他模数的运算由系统按需组合；具体端口见 [原生接口](docs/interface.md)。

## 性能指标

理想接口下，65,536 元素的 MUL 任务在 65,579 周期完成，普通乘法通路的元素启动间隔为一周期。重复 32 元素 MUL 任务每 76 周期完成一个，三条基域模乘流水线在长向量测试中的有效占用率均为 99.95%。各模式的指标定义、方法和实测值见[性能指标](docs/performance.md)。

## 数学模型

电路在模数 `2^64−2^32+1`、关系 `u²=7` 的二次扩域中，以两个规范 64 位系数表示元素，提供 ADD、SUB、MUL、SQUARE、INV、FMA、DOT，并明确零逆元与空点积的行为。严格定义见 [数学模型](docs/mathematical-model.md)。

## 快速复现

从公开的 Chisel 源码重新生成电路，以 C 模型和 Python 独立参考交叉核对，并对重新生成及随包 SV 运行功能、恢复与性能测试；详细步骤见 [快速复现](docs/reproduction.md)。

## 综合结果

典型配置使用 DC NXT 在 TSMC 28 nm HPC+ 的 SSG 0.81 V、−40°C 工艺角通过 500 MHz setup 门禁，时钟不确定度为周期的 10%；总 cell 面积为 0.142596 mm²，99.91% 的覆盖路径 Z/ZN 逻辑级数严格小于 50，按文档指定的数据路径 Z/ZN 输出引脚计级，不按报告行数或时钟树点计数。条件和结果见 [综合摘要](docs/synthesis-summary.md)。

## 公开边界

本仓库提供 RTL 设计和完整仿真复现链路，产品通过原生接口供用户集成，不包含 SoC、总线适配器或板级工程。综合部分提供我们核对后转述的条件和关键结果。很抱歉，受商业工具及工艺库授权约束，无法提供工艺库、原始 DC 脚本、原始报告、映射网表或综合快照。

## OpenRTL与定制集成

通过[OpenRTL组织主页](https://github.com/OpenRTL-Lab)、[ZK加速器集合](https://github.com/OpenRTL-Lab/zk-accelerators)及[OpenRTL网站](https://openrtl.top/)查找相关模块与合作联系入口。需要定制数据通路、接口或目标平台适配时可联系维护者；MIT商用权不附加购买服务的要求，不要在公开Issue提交保密规格。

## 发布源码与集成

版本标签标识包含来源声明和文档的当前源码树；Release 下载提供本版本硬件源码与随包 RTL。源码包与在线目录的注释及文档可能不同。RTL 集成使用产品文件清单，不把验证环境作为产品电路；支持配置、验证范围和测量条件见验证及综合文档。
