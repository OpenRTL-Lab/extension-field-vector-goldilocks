/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Extension-Field Vector Hardware Accelerator
 * Chisel hardware for vector arithmetic over the quadratic extension defined by u squared = 7.
 * Repository: https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks
 * File: operators/extension-field-vector/src/main/scala/zk/extension/Product.scala
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

case class Config(maxLength: Int = 65536, contexts: Int = 16) {
  require(maxLength >= 16 && maxLength <= 65536)
  require(contexts == 16 || contexts == 32)
}
object Field {
  val P = BigInt("18446744069414584321")
  def add(a: UInt, b: UInt): UInt = {
    val s = AsicPrefixArithmetic.add(a, b, 65)
    Mux(s >= P.U(65.W), AsicPrefixArithmetic.subtract(s, P.U(65.W), 65), s)(63,0)
  }
  def sub(a: UInt, b: UInt): UInt = {
    val d = AsicPrefixArithmetic.subtract(a, b, 65)
    Mux(d(64), AsicPrefixArithmetic.add(d, P.U(65.W), 65), d)(63,0)
  }
  def neg(a: UInt): UInt = sub(0.U(64.W), a)
  def seven(a: UInt): UInt = { val t=add(a,a); add(add(t,t),add(t,a)) }
}
class TaskRecord extends Bundle {
  val tag=UInt(32.W); val mode=UInt(3.W); val length=UInt(17.W); val baseOperand=Bool()
}
class InputRecord extends Bundle {
  val a0=UInt(64.W); val a1=UInt(64.W); val b0=UInt(64.W); val b1=UInt(64.W)
  val c0=UInt(64.W); val c1=UInt(64.W); val last=Bool()
}
class ResultRecord extends Bundle {
  val tag=UInt(32.W); val index=UInt(17.W); val r0=UInt(64.W); val r1=UInt(64.W)
  val last=Bool(); val zeroFlag=Bool()
}
class CompletionRecord extends Bundle {
  val tag=UInt(32.W); val status=UInt(2.W); val processed=UInt(17.W); val zeroCount=UInt(17.W)
}
class IngressRecord extends Bundle {val data=new InputRecord;val index=UInt(17.W);val last=Bool()}

/** v1.1.0：两级弹性入口、39拍普通元素流水、II1；任务本身不重叠。 */
class ExtensionFieldVector(cfg: Config) extends Module {
  val NormalLatency=39
  val io=IO(new Bundle {
    val enable=Input(Bool()); val task=Flipped(Decoupled(new TaskRecord))
    val input=Flipped(Decoupled(new InputRecord)); val output=Decoupled(new ResultRecord)
    val completion=Decoupled(new CompletionRecord)
  })
  val idle :: run :: emptyDot :: complete :: Nil = Enum(4)
  val state=RegInit(idle); val task=Reg(new TaskRecord)
  val inCount=RegInit(0.U(17.W)); val outCount=RegInit(0.U(17.W))
  val credit=RegInit(0.U(7.W)); val status=RegInit(0.U(2.W)); val zeroCount=RegInit(0.U(17.W))
  val acc0=RegInit(VecInit(Seq.fill(8)(0.U(64.W))));val acc1=RegInit(VecInit(Seq.fill(8)(0.U(64.W))))
  val dotBank=RegInit(0.U(3.W))
  val q=Module(new Queue(new ResultRecord,64,pipe=true))
  io.output <> q.io.deq
  io.task.ready := state===idle && io.enable
  io.completion.valid := state===complete
  io.completion.bits.tag:=task.tag; io.completion.bits.status:=status
  io.completion.bits.processed:=inCount; io.completion.bits.zeroCount:=zeroCount
  when(io.task.fire) {
    task:=io.task.bits; inCount:=0.U; outCount:=0.U; credit:=0.U; zeroCount:=0.U
    for(k<-0 until 8){acc0(k):=0.U;acc1(k):=0.U};dotBank:=0.U;status:=0.U
    when(io.task.bits.mode===7.U || io.task.bits.length>cfg.maxLength.U) {status:=1.U; state:=complete}
      .elsewhen(io.task.bits.length===0.U) {state:=Mux(io.task.bits.mode===6.U,emptyDot,complete)}
      .otherwise {state:=run}
  }
  when(io.completion.fire) {state:=idle}
  when(io.output.fire) {outCount:=outCount+1.U; when(io.output.bits.last) {state:=complete}}
  val inverse=Module(new InverseSlots(cfg))
  val isInv=task.mode===4.U; val isDot=task.mode===6.U
  val ingress0=Reg(new IngressRecord);val ingress1=Reg(new IngressRecord)
  val ingressValid0=RegInit(false.B);val ingressValid1=RegInit(false.B)
  val consume=state===run && ingressValid1 && (!isInv || inverse.io.input.ready)
  val ready1= !ingressValid1 || consume
  val ready0= !ingressValid0 || ready1
  io.input.ready := state===run && inCount<task.length && (isDot || credit<64.U) && ready0
  val fire=io.input.fire
  val raw=ingress0.data
  val canonical=Seq(raw.a0,raw.a1,raw.b0,raw.b1,raw.c0,raw.c1).map(_<Field.P.U).reduce(_&&_) && (!task.baseOperand || raw.b1===0.U)
  when(ready0){ingressValid0:=fire;when(fire){ingress0.data:=io.input.bits;ingress0.index:=inCount;ingress0.last:=inCount===task.length-1.U}}
  when(ready1){ingressValid1:=ingressValid0;when(ingressValid0){
    ingress1.data:=Mux(canonical,raw,0.U.asTypeOf(new InputRecord));ingress1.index:=ingress0.index;ingress1.last:=ingress0.last
  }}
  val safe=ingress1.data;val last=ingress1.last
  when(fire){inCount:=inCount+1.U}
  when(ingressValid0 && ready1) {
    when(!canonical || raw.last=/=ingress0.last) {status:=3.U}
      .elsewhen(isInv && raw.a0===0.U && raw.a1===0.U && status===0.U) {status:=2.U}
    when(isInv && raw.a0===0.U && raw.a1===0.U) {zeroCount:=zeroCount+1.U}
  }
  when(!isDot && !io.task.fire) {
    when(fire && !io.output.fire) {credit:=credit+1.U}
    when(!fire && io.output.fire) {credit:=credit-1.U}
  }
  inverse.io.input.valid:=ingressValid1 && state===run && isInv
  inverse.io.input.bits.a0:=safe.a0; inverse.io.input.bits.a1:=safe.a1
  inverse.io.input.bits.index:=ingress1.index; inverse.io.input.bits.last:=last
  inverse.io.nextIndex:=outCount
  // 预处理四拍；无valid的寄存器翻转不能统计为有效工作。
  val normalFire=consume && !isInv
  val preValid=ShiftRegister(normalFire,4,false.B,true.B)
  val a0=ShiftRegister(safe.a0,4);val a1=ShiftRegister(safe.a1,4)
  val b0=ShiftRegister(Mux(task.mode===3.U,safe.a0,safe.b0),4)
  val b1=ShiftRegister(Mux(task.mode===3.U,safe.a1,Mux(task.baseOperand,0.U,safe.b1)),4)
  val sumA=PipelinedField.add(safe.a0,safe.a1)
  val sumB=PipelinedField.add(Mux(task.mode===3.U,safe.a0,safe.b0),Mux(task.mode===3.U,safe.a1,Mux(task.baseOperand,0.U,safe.b1)))
  val muls=Seq.fill(3)(Module(new ExtensionGoldilocksMul))
  muls(0).io.a:=Mux(isInv,inverse.io.mulA,a0); muls(0).io.b:=Mux(isInv,inverse.io.mulB,b0)
  muls(1).io.a:=a1; muls(1).io.b:=b1; muls(2).io.a:=sumA; muls(2).io.b:=sumB
  inverse.io.product:=muls(0).io.product
  val x=muls(0).io.product; val y=muls(1).io.product; val z=muls(2).io.product
  val twice=PipelinedField.add(y,y);val cross0=PipelinedField.sub(z,x);val y1=ShiftRegister(y,4);val x1=ShiftRegister(x,4)
  val four=PipelinedField.add(twice,twice);val three=PipelinedField.add(twice,y1)
  val cross1=PipelinedField.sub(cross0,y1);val x2=ShiftRegister(x1,4)
  val seven=PipelinedField.add(four,three);val cross2=ShiftRegister(cross1,4);val x3=ShiftRegister(x2,4)
  val co0=PipelinedField.add(x3,seven);val co1=ShiftRegister(cross2,4)
  val c0=ShiftRegister(safe.c0,35);val c1=ShiftRegister(safe.c1,35)
  val r0=Mux(task.mode===5.U,PipelinedField.add(co0,c0),ShiftRegister(co0,4))
  val r1=Mux(task.mode===5.U,PipelinedField.add(co1,c1),ShiftRegister(co1,4))
  val bypass0=ShiftRegister(Mux(task.mode===1.U,PipelinedField.sub(safe.a0,safe.b0),PipelinedField.add(safe.a0,safe.b0)),35)
  val bypass1=ShiftRegister(Mux(task.mode===1.U,PipelinedField.sub(safe.a1,safe.b1),PipelinedField.add(safe.a1,safe.b1)),35)
  val valid=ShiftRegister(normalFire,NormalLatency,false.B,true.B)
  val index=ShiftRegister(ingress1.index,NormalLatency);val end=ShiftRegister(last,NormalLatency)
  val normal0=Mux(task.mode<2.U,bypass0,r0); val normal1=Mux(task.mode<2.U,bypass1,r1)
  val dotIssue=valid && isDot
  val total0=PipelinedField.add(acc0(dotBank),normal0);val total1=PipelinedField.add(acc1(dotBank),normal1)
  val writeDot=ShiftRegister(dotIssue,4,false.B,true.B);val writeBank=ShiftRegister(dotBank,4)
  when(dotIssue){dotBank:=dotBank+1.U}
  when(writeDot){acc0(writeBank):=total0;acc1(writeBank):=total1}
  def merge(banks:Vec[UInt]):UInt={
    val pairs=(0 until 4).map(k=>PipelinedField.add(banks(2*k),banks(2*k+1)))
    val halves=(0 until 2).map(k=>PipelinedField.add(pairs(2*k),pairs(2*k+1)))
    PipelinedField.add(halves(0),halves(1))
  }
  val dot0=merge(acc0);val dot1=merge(acc1)
  val dotEnd=ShiftRegister(dotIssue && end,17,false.B,true.B)
  q.io.enq.valid := (valid && !isDot) || dotEnd || (isInv && inverse.io.output.valid) || state===emptyDot
  q.io.enq.bits.tag:=task.tag
  q.io.enq.bits.index:=Mux(state===emptyDot || isDot,0.U,Mux(isInv,inverse.io.output.bits.index,index))
  q.io.enq.bits.r0:=Mux(state===emptyDot,0.U,Mux(isInv,inverse.io.output.bits.r0,Mux(isDot,dot0,normal0)))
  q.io.enq.bits.r1:=Mux(state===emptyDot,0.U,Mux(isInv,inverse.io.output.bits.r1,Mux(isDot,dot1,normal1)))
  q.io.enq.bits.last:=state===emptyDot || isDot || Mux(isInv,inverse.io.output.bits.last,end)
  q.io.enq.bits.zeroFlag:=isInv && inverse.io.output.bits.zeroFlag
  inverse.io.output.ready:=isInv && q.io.enq.ready
  when(state===emptyDot && q.io.enq.fire) {state:=run}
  assert(!((valid && !isDot) || dotEnd) || q.io.enq.ready,"reserved output credit exhausted")
  val effective=preValid && task.mode>=2.U && task.mode=/=4.U
  chisel3.layer.block(chisel3.layers.Verification) {
    val cycle=RegInit(0.U(64.W)); cycle:=cycle+1.U
    for(k<-0 until 3) {
      val normalWork=RegNext(effective,false.B)
      val work=if(k==0) Mux(isInv,RegNext(inverse.io.issue,false.B),normalWork) else normalWork && !isInv
      when(work) { printf(p"WORK unit=${k.U} cycle=${cycle} tag=${task.tag}\n") }
    }
    def group(name:String, valid:Bool):Unit={when(valid){printf(p"GROUP name=${name} cycle=${cycle}\n")}}
    val arith=normalFire && task.mode>=2.U
    group("pre_add",arith)
    group("post1",ShiftRegister(arith,19,false.B,true.B))
    group("post2",ShiftRegister(arith,23,false.B,true.B))
    group("post3",ShiftRegister(arith,27,false.B,true.B))
    group("post4",ShiftRegister(arith,31,false.B,true.B))
    group("fma_add",ShiftRegister(normalFire && task.mode===5.U,35,false.B,true.B))
    group("bypass_add",normalFire && task.mode===0.U)
    group("bypass_sub",normalFire && task.mode===1.U)
    group("dot_add",dotIssue)
    group("dot_merge1",ShiftRegister(dotIssue && end,5,false.B,true.B))
    group("dot_merge2",ShiftRegister(dotIssue && end,9,false.B,true.B))
    group("dot_merge3",ShiftRegister(dotIssue && end,13,false.B,true.B))
  }
}
