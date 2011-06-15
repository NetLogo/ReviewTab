package org.nlogo.review

import java.io.{ ObjectOutputStream, ObjectInputStream, FileOutputStream, FileInputStream }
import collection.mutable.{HashMap, ArrayBuffer}
import org.nlogo.hubnet.protocol._
import org.nlogo.api.{ReporterLogoThunk, WorldPropertiesInterface}
import org.nlogo.api.WidgetIO.{WidgetSpec, MonitorSpec, InterfaceGlobalWidgetSpec}
import org.nlogo.workspace.AbstractWorkspaceScala
import scala.collection.JavaConverters._
import org.nlogo.plot.{Plot, PlotListener}
import org.nlogo.hubnet.mirroring.{HubNetPlotPoint, ServerWorld}

object Run {
  def finish(runs: Seq[Run]): Seq[FinishedRun] = for(r<-runs) yield r match {
    case f:FinishedRun => f
    case a:ActiveRun => a.finish
  }
  def save(path: String, runs: Seq[Run]) {
    val out = new ObjectOutputStream(new FileOutputStream(path))
    out.writeObject(Vector(finish(runs): _*))
    out.close()
  }
  def load(path: String): Seq[FinishedRun] = {
    val in = new ObjectInputStream(new FileInputStream(path))
    in.readObject().asInstanceOf[Vector[FinishedRun]]
  }
}

@SerialVersionUID(0)
case class Frame(tick:Int, diffs: Iterable[Message])

trait Replayer {
  def reset()
  def advance(message: Message)
}

trait Run extends Serializable {
  var name: String
  var frameNumber:Int
  val modelName: String
  val interface: ClientInterface
  var annotations:String

  protected val frames: ArrayBuffer[Frame]

  override def toString = name // since JList will display this to the user
  def max = frames.size - 1
  def updateTo(newFrame:Int, replayer:Replayer){
    val oldFrame = frameNumber
    frameNumber = newFrame
    val resetWorld = oldFrame > newFrame
    if (resetWorld) { replayer.reset() }
    val slice = frames.slice(if (resetWorld) 0 else oldFrame + 1, newFrame + 1)
    for (f <- slice; d <- f.diffs) replayer.advance(d)
  }
}

@SerialVersionUID(0)
case class FinishedRun(
  var name: String,
  modelName: String,
  interface: ClientInterface,
  frames: ArrayBuffer[Frame],
  var frameNumber:Int,
  var annotations:String) extends Run

class ActiveRun(var name: String, workspace: AbstractWorkspaceScala) extends Run {

  val frames = ArrayBuffer[Frame]()
  var frameNumber:Int = 0
  var annotations:String = ""

  val modelName = workspace.modelNameForDisplay
  val interface = new ClientInterface(
    workspace.serverWidgetSpecs,
    workspace.world.turtleShapeList.getShapes.asScala,
    workspace.world.linkShapeList.getShapes.asScala)

  private val monitorThunks = HashMap[String, ReporterLogoThunk]()
  private val widgetValues = new HashMap[String, AnyRef]
  private val worldBuffer = new ServerWorld(
    if(workspace.getPropertiesInterface != null) workspace.getPropertiesInterface
    else new WorldPropertiesInterface { def fontSize = 10 }
  )
  private val plotListeners = for(p<-workspace.plotManager.plots) yield MyPlotListener(p)


  // gather any diffs for this tick including
  // the view, all widgets, and plots.
  def addFrame() {
    val viewDiffs: Iterable[Message] = viewDiffMaybe.toIterable
    val widgetDiffs: Iterable[Message] = interface.widgets flatMap widgetDiffMaybe
    val plotDiffs: Iterable[Message] = plotListeners flatMap{ p:MyPlotListener => p.drain() }
    val allDiffs = viewDiffs.toList ++ widgetDiffs.toList ++ plotDiffs.toList
    val newFrame = Frame(workspace.world.ticks.toInt, allDiffs)
    //println(newFrame)
    frames += newFrame
  }

  // only include a ViewUpdate if changes have happened in the view.
  def viewDiffMaybe = {
    val viewDiffBuffer = worldBuffer.updateWorld(workspace.world, false)
    if (!viewDiffBuffer.isEmpty) Some(ViewUpdate(viewDiffBuffer.toByteArray))
    else None
  }

  // get the current value of the widget
  def widgetDiffMaybe(widget: WidgetSpec): Option[WidgetControl] = {
    // only include the newValue in the diff if it is different
    // from the widgets previous value.
    def maybeNewVal(name: String, newValue: AnyRef): Option[WidgetControl] =
      if (!widgetValues.contains(name) || widgetValues(name) != newValue) {
        widgetValues.put(name, newValue)
        Some(WidgetControl(newValue, name))
      }
      else None
    widget match {
      case i: InterfaceGlobalWidgetSpec =>
        maybeNewVal(i.name, workspace.world.getObserverVariableByName(i.name))
      case m: MonitorSpec => {
        def compile(source: String) = workspace.makeReporterThunk(source, "ReviewTab")
        val thunk = monitorThunks.getOrElseUpdate(m.source.get, compile(m.source.get))
        maybeNewVal(m.displayName.getOrElse(m.source.get), thunk.call)
      }
      case _ => None
    }
  }

  def finish = FinishedRun(name, modelName, interface, frames, frameNumber, annotations)

  case class MyPlotListener(plot:Plot) extends PlotListener {
    plot.plotListener = Some(this)
    var messages = ArrayBuffer[Message]()
    def clearAll() { messages += new PlotControl('a'.asInstanceOf[AnyRef], "ALL PLOTS") }
    def clear() { addPlotControl('c') }
    def defaultXMin(defaultXMin: Double) { xMin(defaultXMin) }
    def defaultYMin(defaultYMin: Double) { yMin(defaultYMin) }
    def defaultXMax(defaultXMax: Double) { xMax(defaultXMax) }
    def defaultYMax(defaultYMax: Double) { yMax(defaultYMax) }
    def defaultAutoPlotOn(defaultAutoPlotOn: Boolean) { autoPlotOn(defaultAutoPlotOn) }
    def autoPlotOn(flag: Boolean) {  addPlotControl(if (flag) 'n' else 'f') }
    def plotPenMode(plotPenMode: Int) { addPlotControl(plotPenMode.toShort) }
    def plot(x: Double, y: Double){ addPlotControl(new HubNetPlotPoint(x, y)) }
    def resetPen(hardReset: Boolean) { addPlotControl(if (hardReset) 'r' else 'p') }
    def penDown(flag: Boolean) { addPlotControl(flag) }
    def setHistogramNumBars(num: Int){ sys.error("implement me") }
    def currentPen(penName: String) { addPlotControl(penName) }
    def setPenColor(color: Int) { addPlotControl(color) }
    def setInterval(interval: Double) { addPlotControl(interval) }
    def xRange(min: Double, max: Double) { addPlotControl(List('x', min, max)) }
    def yRange(min: Double, max: Double) { addPlotControl(List('y', min, max)) }
    def xMin(min: Double) { xRange(min, plot.xMax) }
    def xMax(max: Double) { xRange(plot.xMin, max) }
    def yMin(min: Double) { yRange(min, plot.yMax) }
    def yMax(max: Double) { yRange(plot.yMin, max) }

    private def addPlotControl(a:Any) {
      messages :+= new PlotControl(a.asInstanceOf[AnyRef], plot.name)
    }

    def drain() = {
      val diffs = messages.toArray
      messages = ArrayBuffer[Message]()
      diffs
    }
  }
}
