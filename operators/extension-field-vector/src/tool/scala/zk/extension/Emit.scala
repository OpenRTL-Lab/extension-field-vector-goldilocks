/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Extension-Field Vector Hardware Accelerator
 * Chisel hardware for vector arithmetic over the quadratic extension defined by u squared = 7.
 * Repository: https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks
 * File: operators/extension-field-vector/src/tool/scala/zk/extension/Emit.scala
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks extension-field vector contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

package zk.extension
import circt.stage.ChiselStage
import java.nio.file.{Files,Paths}
import scala.io.Source
object Emit extends App {
  require(args.length==4,"Emit build/output/rtl maxLength contexts vectors")
  val out=Paths.get(args(0))
  require(!out.isAbsolute && out.startsWith(Paths.get("build")) && out.normalize()==out && !Files.exists(out))
  val vector=Paths.get(args(3)).toAbsolutePath
  val source=Source.fromFile(vector.resolve("plans.tsv").toFile)
  val plans=try source.getLines().filter(_.nonEmpty).map {line=>val v=line.split("\\s+").map(_.toInt);Plan(v(0),v(1),v(2)!=0,v(3),v(4),v(5),v(6),v(7),v(8))}.toSeq finally source.close()
  def lines(name:String):Int={val s=Source.fromFile(vector.resolve(name).toFile);try s.getLines().size finally s.close()}
  Files.createDirectories(out)
  ChiselStage.emitSystemVerilog(new ExtensionSimTop(Config(args(1).toInt,args(2).toInt),plans,lines("input.hex"),lines("expected.hex"),vector.toString),
    firtoolOpts=Array("-O=release","-disable-all-randomization","--lowering-options=disallowLocalVariables","-split-verilog",s"-o=$out"))
}
