<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Extension-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic over the quadratic extension defined by u squared = 7.
Repository: https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks
File: docs/reproduction.md

SPDX-FileCopyrightText: 2026 Goldilocks extension-field vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 快速复现

[English](reproduction.en.md)

本文从工具准备到参考模型、Chisel 生成、产品 SV 检查和六类仿真测试给出完整步骤；默认可快速核对功能，完整性能命令重现 444 个任务和全部公开周期指标，不需要工艺库、商业综合工具或内部仓库。

## 获取版本快照

[Release v1.1.0](https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks/releases/tag/v1.1.0)

```bash
git clone --branch v1.1.0 --depth 1 https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks.git
cd extension-field-vector-goldilocks
```

版本标签标识包含来源声明和文档的当前源码树；Release 下载提供本版本硬件源码与随包 RTL。源码包与在线目录的注释及文档可能不同。RTL 集成使用产品文件清单，不把验证环境作为产品电路；支持配置、验证范围和测量条件见验证及综合文档。

## 1. 准备工具

按 [工具链说明](toolchain.md) 安装工具，使启动命令可从 PATH 调用，然后进入本仓库根目录。所有以下命令均从仓库根目录执行；`build` 是本仓库内的输出目录，不是预先下载的工程依赖。

第一次运行需要 Mill 能解析公开 Scala/Chisel 依赖，以及 Chisel 能解析对应 firtool。工具就绪是生成和仿真命令的前置条件，不等于已经进行了验证。

执行前注意：每次运行均使用 `build/` 下新的相对目录。`--max-length` 设置硬件向量容量（16..65,536），`--contexts` 选择同一任务内16或32个求逆元素状态槽；`--jobs` 是编译并行度（1..64），不是额外算术通道。92/444任务是验证工作负载；六类仿真场景覆盖七种产品运算，不是六个新增运算模式。

## 2. 快速功能验证

以下命令编译 C 参考，独立核对域运算，生成 Chisel 环境和产品，检查 lint，并对重新生成及随包产品分别测试理想接口、背压、普通复位、负向期望值、INV 复位、DOT 复位：

```sh
python3 tools/verify.py --out build/verify --jobs 8
```

默认包含 92 个任务。程序最终应成功返回，且 `build/verify/results.json` 的 `status` 为 0、`bundled_product_equal` 为 true。负向测试故意破坏一个期望值，必须检出一个错误、返回非零；总脚本仅在这个预设负向行为正确时通过。

## 3. 完整性能复测

以下命令增加长向量、连续批次和非零 INV 长任务，复现 [性能指标](performance.md) 的完整实测集：

```sh
python3 tools/verify.py --out build/verify-full --large --jobs 8
```

预期 444 个任务、每类测试检查 411,963 个结果记录。generated 与 bundled 各五个正向测试必须零错误，负向测试恰好一个错误；两份理想模式任务延迟、批次吞吐率和 busy/idle 统计必须完全一致。

| 输出 | 内容 |
| --- | --- |
| `results.json` | 配置、各测试检查数/错误数/周期数及总体通过状态 |
| `reference-check.json` | C/Python 数学交叉核对 |
| `plans.json` | 每任务模式、长度、标签、边界及期望状态 |
| `generated/performance.json` | 重新生成产品的逐任务延迟、忙率和批次吞吐率 |
| `bundled/performance.json` | 随包产品的同口径测量，自动比较相等 |
| `generated/*.log`、`bundled/*.log` | 仿真事件、有效算术工作和 SUMMARY |
| `rtl/product.f` | 仅产品依赖闭包，不含验证环境 |
| `rtl/simulation.f` | 完整验证环境依赖 |

这些文件在所选 `--out` 目录下生成。参考 ROM 和 C++/Verilator 构建文件只属于仿真输出，不属于产品 SV。

## 4. 只生成电路

以下命令生成参考向量、环境和独立产品依赖清单，不运行 Verilator 功能测试；将 `build/emit/rtl/product.f` 用于产品编译：

```sh
python3 tools/emit_sv.py --out build/emit --max-length 65536 --contexts 16
```

随包的五个产品模块可直接使用 [生成好的 SV](../rtl/v1.1.0/gl64-e2-lane1-ctx16-l65536/)；其中 `files.f` 以该目录为相对基准。顶层为 `ExtensionFieldVector`。

## 5. 生成期配置

以下命令独立验证 32 上下文配置，使用新目录保留已有结果：

```sh
python3 tools/verify.py --out build/verify-ctx32 --contexts 32 --jobs 8
```

`--max-length` 支持 16–65,536，`--contexts` 支持 16 或 32；配置只改变容量和调度，不改变数学域、运算模式与接口位宽。随包 SV 对应 16 上下文和最大长度 65,536；其他配置验证重新生成的产品，不与该随包配置混用。

## 6. 结果保留与问题定位

输出目录必须是 `build` 内的新相对目录。脚本拒绝覆盖已有结果；复测请更换 `--out`。失败时先查看当前输出目录的参考、emit、lint 或 compile 日志，以及具体测试日志，不使用外部仓库填补依赖。C++ 驱动只驱动时钟、复位和测试选择，所有原生端口操作及结果比较由 Chisel 环境执行。
