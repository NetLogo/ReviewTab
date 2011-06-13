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
class Run(var name: String,
  var handshake:HandshakeFromServer,
  var fontSize:Int,
  var frames: ArrayBuffer[Frame] = ArrayBuffer(),
  var frameNumber:Int = 0,
  var annotations:String = "") extends Serializable {

  private val worldBuffer = new ServerWorld(
    new WorldPropertiesInterface { def fontSize = Run.this.fontSize }
  )

  val interface: ClientInterface = handshake.interfaceSpecList.get(0).asInstanceOf[ClientInterface]
  val monitorThunks = HashMap[String, ReporterLogoThunk]()

  def addFrame(workspace: GUIWorkspace) {
    def evaluateWidget(widget: WidgetSpec): Option[WidgetControl] = widget match {
      case i: InterfaceGlobalWidgetSpec =>
        Some(WidgetControl(workspace.world.getObserverVariableByName(i.name), i.name))
      case m: MonitorSpec => {
        val thunk =
          monitorThunks.getOrElseUpdate(m.source.get, workspace.makeReporterThunk(m.source.get, "ReviewTab"))
        Some(WidgetControl(thunk.call, m.displayName.getOrElse(m.source.get)))
      }
      case _ => None
    }
    val viewDiff = ViewUpdate(worldBuffer.updateWorld(workspace.world, false).toByteArray)
    val widgetDiffs = interface.widgets flatMap evaluateWidget
    frames :+= Frame(workspace.world.ticks.toInt, viewDiff :: widgetDiffs.toList)
  }

  def max = frames.size - 1
  // since JList will display this to the user
  override def toString = name

  def updateTo(newFrame:Int, view: RunsPanel){
    val oldFrame = frameNumber
    frameNumber = newFrame
    val resetWorld = oldFrame > newFrame
    if (resetWorld) view.viewWidget.world.reset()
    val slice = frames.slice(if (resetWorld) 0 else oldFrame + 1, newFrame + 1)
    for (f <- slice; d <- f.diffs) view.handleProtocolMessage(d)
  }
}
