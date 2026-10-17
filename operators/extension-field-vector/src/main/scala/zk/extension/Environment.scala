/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Extension-Field Vector Hardware Accelerator
 * Chisel hardware for vector arithmetic over the quadratic extension defined by u squared = 7.
 * Repository: https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks
 * File: operators/extension-field-vector/src/main/scala/zk/extension/Environment.scala
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

case class Plan(mode:Int,length:Int,base:Boolean,tag:Int,inOffset:Int,outOffset:Int,outLength:Int,status:Int,zeros:Int)
class ReferenceRom(width:Int,depth:Int,name:String,path:String) extends BlackBox with HasBlackBoxInline {
  val io=IO(new Bundle {val address=Input(UInt(32.W));val value=Output(UInt(width.W))})
  override def desiredName=name
  setInline(name+".sv",s"""module $name(input [31:0] address, output [$width-1:0] value);
  reg [$width-1:0] memory [0:${depth-1}];
  initial $$readmemh("$path",memory);
  assign value=memory[address];
endmodule
""")
}
/** ENV而非C++拥有所有协议、参考比较、测试时间戳。参考ROM不进入产品清单。 */
class ExtensionSimTop(cfg:Config, plans:Seq[Plan], inputDepth:Int, outputDepth:Int, directory:String) extends Module {
  val io=IO(new Bundle {val start=Input(Bool());val testMode=Input(UInt(3.W));val finished=Output(Bool());val errors=Output(UInt(32.W));val checked=Output(UInt(32.W))})
  val dut=Module(new ExtensionFieldVector(cfg))
  val cycle=RegInit(0.U(64.W));cycle:=cycle+1.U
  val active=RegInit(false.B); val finished=RegInit(false.B);val errors=RegInit(0.U(32.W));val checked=RegInit(0.U(32.W))
  val job=RegInit(0.U(log2Ceil(plans.size+1).W));val sentTask=RegInit(false.B)
  val in=RegInit(0.U(17.W));val out=RegInit(0.U(17.W));val retried=RegInit(false.B)
  val taskPending=RegInit(false.B);val inputPending=RegInit(false.B)
  val taskStart=RegInit(0.U(64.W))
  val p=VecInit(plans.map(x=>{
    val w=Wire(new Bundle {val task=new TaskRecord;val inOffset=UInt(32.W);val outOffset=UInt(32.W);val outLength=UInt(17.W);val status=UInt(2.W);val zeros=UInt(17.W)})
    w.task.mode:=x.mode.U;w.task.length:=x.length.U;w.task.baseOperand:=x.base.B;w.task.tag:=x.tag.U
    w.inOffset:=x.inOffset.U;w.outOffset:=x.outOffset.U;w.outLength:=x.outLength.U;w.status:=x.status.U;w.zeros:=x.zeros.U;w
  }))(job)
  val inputRom=Module(new ReferenceRom(385,inputDepth,"ExtensionInputRom",directory+"/input.hex"))
  val outputRom=Module(new ReferenceRom(129,outputDepth,"ExtensionExpectedRom",directory+"/expected.hex"))
  inputRom.io.address:=p.inOffset+in;outputRom.io.address:=p.outOffset+out
  val resetCondition=(io.testMode===2.U && cycle===80.U) ||
    (io.testMode===4.U && sentTask && p.task.mode===4.U && in>=4.U && cycle-taskStart>=80.U) ||
    (io.testMode===5.U && sentTask && p.task.mode===6.U && in>=8.U && cycle-taskStart>=48.U)
  val resetTrial=active && !retried && resetCondition
  dut.reset:=reset.asBool || resetTrial
  dut.io.enable:=true.B
  val stall=io.testMode===1.U
  when(active && !sentTask && (!stall || cycle(2,0)===0.U)) {taskPending:=true.B}
  when(active && sentTask && in<p.task.length && p.status=/=1.U && (!stall || cycle(2,0)=/=3.U)) {inputPending:=true.B}
  dut.io.task.valid:=active && !sentTask && (!stall || taskPending) && !resetTrial
  dut.io.task.bits:=p.task
  dut.io.input.valid:=active && sentTask && (!stall || inputPending) && in<p.task.length && p.status=/=1.U && !resetTrial
  val v=inputRom.io.value
  dut.io.input.bits.a0:=v(63,0);dut.io.input.bits.a1:=v(127,64);dut.io.input.bits.b0:=v(191,128)
  dut.io.input.bits.b1:=v(255,192);dut.io.input.bits.c0:=v(319,256);dut.io.input.bits.c1:=v(383,320);dut.io.input.bits.last:=v(384)
  dut.io.output.ready:=active && (!stall || cycle(3,0)<5.U) && !resetTrial
  dut.io.completion.ready:=active && (!stall || cycle(2,0)===0.U) && !resetTrial
  when(io.start && !active && !finished) {active:=true.B}
  when(dut.io.task.fire) {
    taskStart:=cycle
    sentTask:=true.B;taskPending:=false.B
    printf(p"EVENT A job=${job} tag=${p.task.tag} cycle=${cycle}\n")
  }
  when(dut.io.input.fire) {
    when(in===0.U) {printf(p"EVENT F job=${job} cycle=${cycle}\n")}
    in:=in+1.U;inputPending:=false.B
  }
  val expected0=outputRom.io.value(63,0) ^ Mux(io.testMode===3.U && checked===0.U,1.U(64.W),0.U(64.W))
  val expected1=outputRom.io.value(127,64);val expectedZero=outputRom.io.value(128)
  val outputBad=dut.io.output.fire && (out>=p.outLength || dut.io.output.bits.r0=/=expected0 || dut.io.output.bits.r1=/=expected1 || dut.io.output.bits.zeroFlag=/=expectedZero || dut.io.output.bits.tag=/=p.task.tag || dut.io.output.bits.index=/=out || dut.io.output.bits.last=/=(out===p.outLength-1.U))
  val completionBad=dut.io.completion.fire && (dut.io.completion.bits.status=/=p.status || dut.io.completion.bits.zeroCount=/=p.zeros || dut.io.completion.bits.tag=/=p.task.tag || dut.io.completion.bits.processed=/=Mux(p.status===1.U,0.U,p.task.length) || out=/=p.outLength)
  when(outputBad || completionBad) {
    errors:=errors+1.U
    printf(p"MISMATCH job=${job} index=${out} output=${outputBad} completion=${completionBad} got0=${Hexadecimal(dut.io.output.bits.r0)} expected0=${Hexadecimal(expected0)} got1=${Hexadecimal(dut.io.output.bits.r1)} expected1=${Hexadecimal(expected1)}\n")
  }
  when(dut.io.output.fire) {
    out:=out+1.U;checked:=checked+1.U
    when(dut.io.output.bits.last) {printf(p"EVENT O job=${job} cycle=${cycle}\n")}
  }
  when(dut.io.completion.fire) {
    printf(p"EVENT C job=${job} cycle=${cycle} checked=${out} status=${dut.io.completion.bits.status}\n")
    when(job===(plans.size-1).U) {active:=false.B;finished:=true.B}
      .otherwise {job:=job+1.U;sentTask:=false.B;in:=0.U;out:=0.U;taskPending:=false.B;inputPending:=false.B}
  }
  when(resetTrial) {
    retried:=true.B;job:=0.U;sentTask:=false.B;in:=0.U;out:=0.U;checked:=0.U;errors:=0.U
    taskPending:=false.B;inputPending:=false.B
    printf(p"RESET_RECOVERY cycle=${cycle}\n")
  }
  def stable(valid:Bool,ready:Bool,bits:UInt):Unit={
    val held=RegNext(valid && !ready && !resetTrial,false.B);val prior=RegNext(bits)
    when(held && !resetTrial) {assert(valid && bits===prior,"payload changed under backpressure")}
  }
  stable(dut.io.task.valid,dut.io.task.ready,dut.io.task.bits.asUInt)
  stable(dut.io.input.valid,dut.io.input.ready,dut.io.input.bits.asUInt)
  stable(dut.io.output.valid,dut.io.output.ready,dut.io.output.bits.asUInt)
  stable(dut.io.completion.valid,dut.io.completion.ready,dut.io.completion.bits.asUInt)
  io.finished:=finished;io.errors:=errors;io.checked:=checked
}
