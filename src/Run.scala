package org.nlogo.review

import java.io.{ ObjectOutputStream, ObjectInputStream, FileOutputStream, FileInputStream }
import org.nlogo.hubnet.mirroring.ServerWorld
import collection.mutable.{HashMap, ArrayBuffer}
import org.nlogo.hubnet.protocol._
import org.nlogo.window.GUIWorkspace
import org.nlogo.api.{ReporterLogoThunk, WorldPropertiesInterface}
import org.nlogo.api.WidgetIO.{WidgetSpec, MonitorSpec, InterfaceGlobalWidgetSpec}

object Run {
  def save(path: String, runs: Seq[Run]) {
    val out = new ObjectOutputStream(new FileOutputStream(path))
    out.writeObject(Vector(runs: _*))
    out.close()
  }
  def load(path: String): Seq[Run] = {
    val in = new ObjectInputStream(new FileInputStream(path))
    in.readObject().asInstanceOf[Vector[Run]]
  }
}

@SerialVersionUID(0)
case class Frame(tick:Int, diffs: Iterable[Message])

@SerialVersionUID(0)
case class Run(
  var name: String,
  var handshake:HandshakeFromServer,
  var fontSize:Int,
  var frames: ArrayBuffer[Frame] = ArrayBuffer(),
  var frameNumber:Int = 0,
  var annotations:String = "") {

  @transient private val worldBuffer = new ServerWorld(
    new WorldPropertiesInterface { def fontSize = Run.this.fontSize }
  )
  @transient private val interface = handshake.interfaceSpecList.get(0).asInstanceOf[ClientInterface]
  @transient private val monitorThunks = HashMap[String, ReporterLogoThunk]()
  @transient private val widgetValues = new HashMap[String, AnyRef]

  // gather any diffs for this tick including
  // the view, all widgets, and plots.
  def addFrame(workspace: GUIWorkspace) {
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
    frames :+= Frame(workspace.world.ticks.toInt, allDiffs)
  }

  def max = frames.size - 1

  override def toString = name // since JList will display this to the user

  def updateTo(newFrame:Int, view: RunsPanel){
    val oldFrame = frameNumber
    frameNumber = newFrame
    val resetWorld = oldFrame > newFrame
    if (resetWorld) view.viewWidget.world.reset()
    val slice = frames.slice(if (resetWorld) 0 else oldFrame + 1, newFrame + 1)
    for (f <- slice; d <- f.diffs) view.handleProtocolMessage(d)
  }
}
