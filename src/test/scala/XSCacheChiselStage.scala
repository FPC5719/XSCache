package xscache

import chisel3.stage.ChiselCircuitAnnotation
import chisel3.stage.phases.Elaborate
import circt.stage.CLI
import firrtl.AnnotationSeq
import firrtl.options.{Dependency, Phase, PhaseManager, Shell}

/** Adds the annotations collected during elaboration to the annotation list.
  *
  * `chisel3.stage.phases.Convert` publishes them as a side effect, but it does so while converting
  * the circuit to the legacy FIRRTL IR, which cannot represent `DefParamInstanceChoice` (the
  * per-hart parameterized instance choice used by `ParamModuleChoice`). Dropping `Convert` without
  * this phase silently drops `dontTouch`, `BlackBoxInlineAnno` (the inline external modules used by
  * `Constantin`/`ChiselDB`) and the decode-table annotations.
  *
  * Same workaround as `xiangshan.XiangShanStage`, kept local so the XSCache test tops do not depend
  * on the XiangShan build.
  */
@scala.annotation.nowarn("msg=All APIs in package firrtl are deprecated")
class AddCircuitAnnotations extends Phase {

  override def prerequisites = Seq(Dependency[Elaborate])
  override def optionalPrerequisiteOf = Seq(
    Dependency[chisel3.stage.phases.AddSerializationAnnotations],
    Dependency[circt.stage.phases.CIRCT]
  )
  override def invalidates(a: Phase) = false

  def transform(annotations: AnnotationSeq): AnnotationSeq = annotations.flatMap {
    case a: ChiselCircuitAnnotation => Some(a) ++ a.elaboratedCircuit.annotations
    case a                          => Some(a)
  }
}

/** `circt.stage.ChiselStage` without the legacy `Convert` phase (see [[AddCircuitAnnotations]]). */
@scala.annotation.nowarn("msg=All APIs in package firrtl are deprecated")
class XSCacheChiselStage extends circt.stage.ChiselStage {

  override val shell = new Shell("circt") with CLI {
    override protected def includeLoggerOptions = false
  }

  override def run(annotations: AnnotationSeq): AnnotationSeq = {
    val pm = new PhaseManager(
      targets = Seq(
        Dependency[chisel3.stage.phases.AddImplicitOutputFile],
        Dependency[chisel3.stage.phases.AddImplicitOutputAnnotationFile],
        Dependency[chisel3.stage.phases.AddSerializationAnnotations],
        Dependency[AddCircuitAnnotations],
        Dependency[chisel3.stage.phases.AddDedupGroupAnnotations],
        Dependency[circt.stage.phases.AddImplicitOutputFile],
        Dependency[circt.stage.phases.CIRCT]
      ),
      currentState = Seq(
        Dependency[firrtl.stage.phases.AddDefaults],
        Dependency[firrtl.stage.phases.Checks]
      )
    )
    pm.transform(annotations)
  }

}
