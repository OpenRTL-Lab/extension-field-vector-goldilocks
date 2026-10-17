/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Extension-Field Vector Hardware Accelerator
 * Chisel hardware for vector arithmetic over the quadratic extension defined by u squared = 7.
 * Repository: https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks
 * File: operators/extension-field-vector/src/main/scala/zk/extension/Inverse.scala
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

class InverseInput extends Bundle {val a0=UInt(64.W);val a1=UInt(64.W);val index=UInt(17.W);val last=Bool()}
class InverseSlots(cfg: Config) extends Module {
  val io=IO(new Bundle {
    val input=Flipped(Decoupled(new InverseInput)); val output=Decoupled(new ResultRecord)
    val nextIndex=Input(UInt(17.W)); val mulA=Output(UInt(64.W)); val mulB=Output(UInt(64.W))
    val issue=Output(Bool()); val product=Input(UInt(64.W))
  })
  val idle :: sq0 :: sq1 :: norm :: powSq :: powMul :: final0 :: final1 :: waiting :: done :: Nil=Enum(10)
  val phase=RegInit(VecInit(Seq.fill(cfg.contexts)(idle)))
  val a0=Reg(Vec(cfg.contexts,UInt(64.W))); val a1=Reg(Vec(cfg.contexts,UInt(64.W)))
  val n0=Reg(Vec(cfg.contexts,UInt(64.W))); val n1=Reg(Vec(cfg.contexts,UInt(64.W)))
  val base=Reg(Vec(cfg.contexts,UInt(64.W))); val power=Reg(Vec(cfg.contexts,UInt(64.W)))
  val bit=Reg(Vec(cfg.contexts,UInt(6.W))); val r0=Reg(Vec(cfg.contexts,UInt(64.W))); val r1=Reg(Vec(cfg.contexts,UInt(64.W)))
  val index=Reg(Vec(cfg.contexts,UInt(17.W))); val last=Reg(Vec(cfg.contexts,Bool())); val zero=Reg(Vec(cfg.contexts,Bool()))
  val slot=RegInit(0.U(log2Ceil(cfg.contexts).W)); slot:=slot+1.U
  io.input.ready:=phase(slot)===idle
  when(io.input.fire) {
    a0(slot):=io.input.bits.a0; a1(slot):=io.input.bits.a1
    index(slot):=io.input.bits.index;last(slot):=io.input.bits.last
    val z=io.input.bits.a0===0.U && io.input.bits.a1===0.U
    zero(slot):=z;phase(slot):=Mux(z,done,sq0);r0(slot):=0.U;r1(slot):=0.U
  }
  val ph=phase(slot)
  io.issue:=ph===sq0 || ph===sq1 || ph===powSq || ph===powMul || ph===final0 || ph===final1
  io.mulA:=MuxLookup(ph,0.U)(Seq(sq0->a0(slot),sq1->a1(slot),powSq->power(slot),powMul->power(slot),final0->a0(slot),final1->a1(slot)))
  io.mulB:=MuxLookup(ph,0.U)(Seq(sq0->a0(slot),sq1->a1(slot),powSq->power(slot),powMul->base(slot),final0->power(slot),final1->power(slot)))
  when(io.issue) {phase(slot):=waiting}
  val normIssue=ph===norm
  when(normIssue){phase(slot):=waiting}
  val normA=RegNext(n0(slot));val normB=RegNext(n1(slot))
  val twice=PipelinedField.add(normB,normB)
  val four=PipelinedField.add(twice,twice);val three=PipelinedField.add(twice,ShiftRegister(normB,4))
  val seven=PipelinedField.add(four,three)
  val normValue=PipelinedField.sub(ShiftRegister(normA,12),seven)
  val normReady=ShiftRegister(normIssue,17,false.B,true.B);val normSlot=ShiftRegister(slot,17)
  when(normReady){
    base(normSlot):=normValue;power(normSlot):=1.U;bit(normSlot):=63.U;phase(normSlot):=powSq
    assert(normValue=/=0.U,"nonzero extension norm must be nonzero")
  }
  val feedback=ShiftRegister(io.issue,ExtensionGoldilocksMul.Latency,false.B,true.B)
  val fs=ShiftRegister(slot,ExtensionGoldilocksMul.Latency); val fp=ShiftRegister(ph,ExtensionGoldilocksMul.Latency)
  val exponent=(Field.P-2).U(64.W)
  def advance(s: UInt): Unit = {
    when(bit(s)===0.U) {phase(s):=final0}.otherwise {bit(s):=bit(s)-1.U;phase(s):=powSq}
  }
  when(feedback) {
    switch(fp) {
      is(sq0) {n0(fs):=io.product;phase(fs):=sq1}
      is(sq1) {n1(fs):=io.product;phase(fs):=norm}
      is(powSq) {power(fs):=io.product;when(exponent(bit(fs))) {phase(fs):=powMul}.otherwise {advance(fs)}}
      is(powMul) {power(fs):=io.product;advance(fs)}
      is(final0) {r0(fs):=io.product;phase(fs):=final1}
      is(final1) { }
    }
  }
  val negative=PipelinedField.neg(io.product)
  val negativeReady=ShiftRegister(feedback && fp===final1,4,false.B,true.B);val negativeSlot=ShiftRegister(fs,4)
  when(negativeReady){r1(negativeSlot):=negative;phase(negativeSlot):=done}
  val matches=VecInit((0 until cfg.contexts).map(k=>phase(k)===done && index(k)===io.nextIndex))
  val outSlot=PriorityEncoder(matches.asUInt)
  io.output.valid:=matches.asUInt.orR
  io.output.bits.tag:=0.U;io.output.bits.index:=index(outSlot)
  io.output.bits.r0:=r0(outSlot);io.output.bits.r1:=r1(outSlot)
  io.output.bits.last:=last(outSlot);io.output.bits.zeroFlag:=zero(outSlot)
  when(io.output.fire) {phase(outSlot):=idle}
  assert(PopCount(matches)<=1.U,"duplicate inverse index")
  chisel3.layer.block(chisel3.layers.Verification) {
    val cycle=RegInit(0.U(64.W));cycle:=cycle+1.U
    when(RegNext(normIssue,false.B)){printf(p"GROUP name=inv_norm_double cycle=${cycle}\n")}
    when(ShiftRegister(normIssue,5,false.B,true.B)){printf(p"GROUP name=inv_norm_four_three cycle=${cycle}\n")}
    when(ShiftRegister(normIssue,9,false.B,true.B)){printf(p"GROUP name=inv_norm_seven cycle=${cycle}\n")}
    when(ShiftRegister(normIssue,13,false.B,true.B)){printf(p"GROUP name=inv_norm_sub cycle=${cycle}\n")}
    when(feedback && fp===final1) {printf(p"GROUP name=inv_neg cycle=${cycle}\n")}
  }
}
