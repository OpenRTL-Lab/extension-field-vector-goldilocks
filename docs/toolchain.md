<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Extension-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic over the quadratic extension defined by u squared = 7.
Repository: https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks
File: docs/toolchain.md

SPDX-FileCopyrightText: 2026 Goldilocks extension-field vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 工具链说明

[English](toolchain.en.md)

本文列出已用于本仓库独立电路生成与完整功能/性能复测的工具版本，说明依赖安装和可选启动设置；Scala、Chisel 与插件版本由构建文件固定，不依赖内部工程或机器专用目录。

| 工具 | 已验证版本 | 用途 |
| --- | --- | --- |
| Mill | 1.1.7 | Scala 构建与生成入口 |
| Scala | 2.13.18 | 设计语言 |
| Chisel 与插件 | 7.13.0 | 电路构建 |
| CIRCT firtool | 1.149.0 | SystemVerilog 降级与拆分 |
| OpenJDK | 21.0.11 | JVM |
| Python | 3.12 | 参考交叉核对及验证脚本 |
| Verilator | 5.050 | lint 与 RTL 仿真 |
| GCC / G++ | 16.2.0 | C 参考与仿真构建 |

安装上述开源工具后，使 `java`、`mill`、`python3`、`verilator`、`cc`、`g++` 可从 PATH 调用。Mill 通过公开 Maven 解析 Scala/Chisel；首次解析需要正常网络或用户自行准备的依赖缓存。Chisel 解析对应的 firtool，使用上表版本以保持生成结果一致。项目不随包提供工具二进制或机器专用缓存。

可选环境变量 `MILL`、`VERILATOR`、`CC` 指定对应启动命令；脚本按 shell 参数规则拆分命令，不执行任意 shell 表达式。缓存与临时目录由用户环境决定，不在仓库中绑定绝对路径。验证将编译 C 与 C++，因此需完整编译器和 `make`。

检查安装版本时，可以先运行以下只读命令，再进行 [快速复现](reproduction.md)：

```sh
java -version
mill --version
python3 --version
verilator --version
cc --version
g++ --version
```

产品生成与仿真只需开源工具。综合摘要中的商业综合工具不是运行本仓库验证链路的依赖。
