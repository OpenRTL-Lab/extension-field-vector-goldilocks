/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Extension-Field Vector Hardware Accelerator
 * Chisel hardware for vector arithmetic over the quadratic extension defined by u squared = 7.
 * Repository: https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks
 * File: operators/extension-field-vector/src/main/scala/zk/extension/PipelinedArithmetic.scala
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks extension-field vector contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

package zk.extension

import chisel3._
import chisel3.util._
import ntt.asic.AsicPrefixArithmetic

/** 每个宽进位链都有真实bit31/32寄存边界，不能合并为单拍64位算术。 */
object ChunkedArithmetic {
  val Latency = 2
  def add(a: UInt, b: UInt, width: Int, carry: Bool = false.B): UInt = {
    require(width >= 33 && width <= 66)
    val left=Wire(UInt(width.W));left:=a
    val right=Wire(UInt(width.W));right:=b
    val low=RegNext(AsicPrefixArithmetic.add(left(31,0),right(31,0),33,carry))
    val upperA=RegNext(left(width-1,32));val upperB=RegNext(right(width-1,32))
    val upper=RegNext(AsicPrefixArithmetic.add(upperA,upperB,width-32,low(32)))
    Cat(upper,RegNext(low(31,0)))
  }
  def subtract(a: UInt,b: UInt,width: Int): UInt={
    val right=Wire(UInt(width.W));right:=b
    add(a,~right,width,true.B)
  }
}
object PipelinedField {
  val Latency=4
  def add(a: UInt,b: UInt):UInt={
    val raw=ChunkedArithmetic.add(a,b,65)
    val corrected=ChunkedArithmetic.subtract(raw,Field.P.U(65.W),65)
    Mux(ShiftRegister(raw>=Field.P.U(65.W),2),corrected,ShiftRegister(raw,2))(63,0)
  }
  def sub(a: UInt,b: UInt):UInt={
    val raw=ChunkedArithmetic.subtract(a,b,65)
    val corrected=ChunkedArithmetic.add(raw,Field.P.U(65.W),65)
    Mux(ShiftRegister(raw(64),2),corrected,ShiftRegister(raw,2))(63,0)
  }
  def neg(a:UInt):UInt=sub(0.U(64.W),a)
}
object ExtensionGoldilocksMul {val Latency=15}
/** 已测较浅的32位部分乘法保留；旧64/67/51级常量进位链各分两拍。 */
class ExtensionGoldilocksMul extends Module {
  val io=IO(new Bundle{val a=Input(UInt(64.W));val b=Input(UInt(64.W));val product=Output(UInt(64.W))})
  val a=RegNext(io.a);val b=RegNext(io.b)
  val p=Field.P.U(66.W)
  val p0=RegNext(a(31,0)*b(31,0));val p1=RegNext(a(31,0)*b(63,32))
  val p2=RegNext(a(63,32)*b(31,0));val p3=RegNext(a(63,32)*b(63,32))
  val outerLow=RegNext(p0);val cross=RegNext(AsicPrefixArithmetic.add(p1,p2,65));val outerHigh=RegNext(p3)
  val low=RegNext(AsicPrefixArithmetic.add(outerLow,Cat(cross(31,0),0.U(32.W)),65))
  val crossHigh=RegNext(cross(64,32));val highDelayed=RegNext(outerHigh)
  val productLow=RegNext(low(63,0))
  val productHigh=RegNext(AsicPrefixArithmetic.add(Cat(0.U(1.W),highDelayed),Cat(0.U(32.W),crossHigh),65,low(64)))
  val t0=RegNext(productLow(31,0))
  val highSum=RegNext(AsicPrefixArithmetic.add(productLow(63,32),productHigh(31,0),33))
  val subtrahend=RegNext(AsicPrefixArithmetic.add(productHigh(31,0),productHigh(63,32),33))
  val positive=ChunkedArithmetic.add(Cat(0.U(1.W),highSum,t0),p,66)
  val delayedSub=ShiftRegister(subtrahend,2)
  val candidate=RegNext(AsicPrefixArithmetic.subtract(positive,Cat(0.U(33.W),delayedSub),66))
  val candidateDelay=RegNext(candidate);val firstSubtract=RegNext(candidate>=p)
  val onceSub=ChunkedArithmetic.subtract(candidateDelay,p,66)
  val once=Mux(ShiftRegister(firstSubtract,2),onceSub,ShiftRegister(candidateDelay,2))
  val onceDelay=RegNext(once);val secondSubtract=RegNext(once>=p)
  val twiceSub=ChunkedArithmetic.subtract(onceDelay,p,66)
  val twice=Mux(ShiftRegister(secondSubtract,2),twiceSub,ShiftRegister(onceDelay,2))
  io.product:=twice(63,0)
}
