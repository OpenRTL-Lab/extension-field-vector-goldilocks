<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Extension-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic over the quadratic extension defined by u squared = 7.
Repository: https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks
File: THIRD_PARTY_NOTICES.md

SPDX-FileCopyrightText: 2026 Goldilocks extension-field vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 第三方声明

[English](THIRD_PARTY_NOTICES.en.md)

本文件说明本仓库设计、生成和验证所用第三方工具及数学参数的边界：发布的设计和参考代码采用 MIT 许可证，第三方工具按其各自许可获取，不随本仓库重新分发。

本仓库固定 Goldilocks 模数 `2^64−2^32+1` 和扩域关系 `u²=7`，其数学身份及不可约性核对见 [数学模型](docs/mathematical-model.md)。这些是公开数学定义，不包含私有协议参数或工艺数据，也不声明为某个证明框架的完整适配器。

构建使用 Mill、Scala、Chisel、CIRCT，验证使用 Python、Verilator 和 GCC。用户安装这些工具及 Maven 依赖时应遵守对应工具和依赖许可证；本仓库仅提供版本要求与自身代码，不捆绑它们的二进制或源码副本。详细版本见 [工具链说明](docs/toolchain.md)。

`LICENSE` 覆盖本仓库的原创 RTL 设计、生成工具、参考/仿真代码与文档。商业综合工具与工艺库不属于 MIT 许可范围；本仓库不分发这些材料。
