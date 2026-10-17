/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Extension-Field Vector Hardware Accelerator
 * Chisel hardware for vector arithmetic over the quadratic extension defined by u squared = 7.
 * Repository: https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks
 * File: operators/extension-field-vector/sim/driver.cpp
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks extension-field vector contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

#include "VExtensionSimTop.h"
#include "verilated.h"
#include <cstdio>
#include <cstdlib>
int main(int argc,char** argv) {
  Verilated::commandArgs(argc,argv); VExtensionSimTop top;
  top.clock=0;top.reset=1;top.io_start=0;top.io_testMode=argc>1?std::atoi(argv[1]):0;
  auto tick=[&](){top.clock=0;top.eval();top.clock=1;top.eval();};
  for(int i=0;i<5;i++)tick();top.reset=0;top.io_start=1;tick();top.io_start=0;
  unsigned long long cycles=0;
  while(!top.io_finished && !Verilated::gotFinish() && cycles<50000000ULL){tick();cycles++;}
  std::printf("SUMMARY pass=%u checked=%u errors=%u cycles=%llu\n",unsigned(top.io_finished && top.io_errors==0),unsigned(top.io_checked),unsigned(top.io_errors),cycles);
  int status=top.io_finished && top.io_errors==0 ? 0:1;top.final();return status;
}
