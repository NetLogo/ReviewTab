package org.nlogo.review

import java.io.{ ObjectOutputStream, ObjectInputStream, FileOutputStream, FileInputStream }
import org.nlogo.hubnet.mirroring.ServerWorld
import collection.mutable.{HashMap, ArrayBuffer}
import org.nlogo.hubnet.protocol._
import org.nlogo.api.{ReporterLogoThunk, WorldPropertiesInterface}
import org.nlogo.api.WidgetIO.{WidgetSpec, MonitorSpec, InterfaceGlobalWidgetSpec}
import org.nlogo.workspace.AbstractWorkspaceScala
import org.nlogo.plot.PlotListener
import scala.collection.JavaConverters._

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

trait Run extends Serializable {
  var name: String
  var frameNumber:Int
  val modelName: String
  val interface: ClientInterface
  var annotations:String

  protected val frames: ArrayBuffer[Frame]

  override def toString = name // since JList will display this to the user
  def max = frames.size - 1
  def updateTo(newFrame:Int, view: RunsPanel){
    val oldFrame = frameNumber
    frameNumber = newFrame
    val resetWorld = oldFrame > newFrame
    if (resetWorld) view.viewWidget.world.reset()
    val slice = frames.slice(if (resetWorld) 0 else oldFrame + 1, newFrame + 1)
    for (f <- slice; d <- f.diffs) view.handleProtocolMessage(d)
  }
}

@SerialVersionUID(0)
case class FinishedRun(
  var name: String,
  modelName: String,
  interface: ClientInterface,
  frames: ArrayBuffer[Frame],
  var frameNumber:Int,
  var annotations:String) extends Run {
}

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

  // gather any diffs for this tick including
  // the view, all widgets, and plots.
  def addFrame() {
    // only include the newValue in the diff if it is different
    // from the widgets previous value.
    def maybeNewVal(name:String, newValue:AnyRef): Option[WidgetControl] =
      if (!widgetValues.contains(name) || widgetValues(name) != newValue) {
        widgetValues.put(name, newValue)
        Some(WidgetControl(newValue, name))
      }
      else None
    // get the current value of the widget
    def evaluateWidget(widget: WidgetSpec): Option[WidgetControl] = widget match {
      case i: InterfaceGlobalWidgetSpec =>
        maybeNewVal(i.name, workspace.world.getObserverVariableByName(i.name))
      case m: MonitorSpec => {
        def compile(source:String) = workspace.makeReporterThunk(source, "ReviewTab")
        val thunk = monitorThunks.getOrElseUpdate(m.source.get, compile(m.source.get))
        maybeNewVal(m.displayName.getOrElse(m.source.get), thunk.call)
      }
      case _ => None
    }
    // only include a ViewUpdate if changes have happened in the view.
    val viewDiffMaybe = {
      val viewDiffBuffer = worldBuffer.updateWorld(workspace.world, false)
      if(! viewDiffBuffer.isEmpty) Some(ViewUpdate(viewDiffBuffer.toByteArray))
      else None
    }
    val widgetDiffs = interface.widgets map evaluateWidget
    val allDiffs = (viewDiffMaybe :: widgetDiffs.toList).flatten
    frames += Frame(workspace.world.ticks.toInt, allDiffs)
  }

  def finish = FinishedRun(name, modelName, interface, frames, frameNumber, annotations)
}

//class MyPlotManager extends PlotListener {
//  val messages = ArrayBuffer[Message]()
//  def clearAll()
//  def clear()
//  def defaultXMin(defaultXMin: Double)
//  def defaultYMin(defaultYMin: Double)
//  def defaultXMax(defaultXMax: Double)
//  def defaultYMax(defaultYMax: Double)
//  def defaultAutoPlotOn(defaultAutoPlotOn: Boolean)
//  def autoPlotOn(flag: Boolean)
//  def plotPenMode(plotPenMode: Int)
//  def plot(x: Double, y: Double)
//  def resetPen(hardReset: Boolean)
//  def penDown(flag: Boolean)
//  def setHistogramNumBars(num: Int)
//  def currentPen(penName: String)
//  def setPenColor(color: Int)
//  def setInterval(interval: Double)
//  def xRange(min: Double, max: Double)
//  def yRange(min: Double, max: Double)
//  def xMin(min: Double)
//  def xMax(max: Double)
//  def yMin(min: Double)
//  def yMax(max: Double)
//}
