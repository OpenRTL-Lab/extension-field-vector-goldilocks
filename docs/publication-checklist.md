<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Extension-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic over the quadratic extension defined by u squared = 7.
Repository: https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks
File: docs/publication-checklist.md

SPDX-FileCopyrightText: 2026 Goldilocks extension-field vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 发布检查

[English](publication-checklist.en.md)

本文件汇总随包内容和复现检查口径：仓库包含独立 Chisel 设计、产品 SV、参考与仿真链路、双语文档及 MIT 许可；功能、性能和已归档综合门禁均有对应说明，用户可按公开步骤检查产品一致性。

- 产品闭包五个 SV 模块，以 `ExtensionFieldVector` 为顶层；不含仿真 ROM、FPGA 原语、SoC 或板级工程。
- 重新生成的典型产品与随包 SV 去除注释/空白后逐模块一致；两套产品均通过完整功能/性能测试。
- 七种运算、长度边界、错误帧和零逆元有参考核对；三种在途复位恢复及背压测试通过；负向对照检出指定错误。
- 完整性能集为 444 个任务、每类测试 411,963 个结果记录；延迟、连续任务吞吐率和计算单元 busy/idle 分别统计。
- 随包典型配置为最大长度 65,536、16 个求逆上下文；32 上下文重新生成配置另行功能复测。
- 500 MHz setup、10% 周期不确定度和 Z/ZN 深度分布见 [综合摘要](synthesis-summary.md)，不随包提供原始商业综合资料。
- 公开文件无机器专用绝对路径、凭证、内部签名或追溯摘要；不包含内部维护记录、构建日志和工具缓存。
- 默认 README.md 为英文，README.zh-CN.md 为独立中文全文；全部自编 Markdown 分别提供独立中英文文件和双向语言链接，正文索引指向同语言说明。
- 完整源包包括全部公开文件；GitHub 自动提供的提交标识是平台版本信息，不是内部追溯资料。

检查公开复现方法见 [快速复现](reproduction.md)，指标口径见 [性能指标](performance.md)。
