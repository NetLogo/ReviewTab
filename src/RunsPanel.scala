package org.nlogo.review

import javax.swing.JPanel
import org.nlogo.agent.{ConstantSliderConstraint}
import org.nlogo.plot.{Plot, PlotManager}
import org.nlogo.window.Events.{AfterLoadEvent, LoadSectionEvent}
import org.nlogo.hubnet.mirroring.{OverrideList, HubNetLinkStamp, HubNetPlotPoint, HubNetLine, HubNetTurtleStamp}
import java.awt.AWTEvent
import org.nlogo.hubnet.protocol._
import org.nlogo.awt.Utils.{getFrame, invokeLater}
import org.nlogo.util.JCL._
import org.nlogo.window.{MonitorWidget, InterfaceGlobalWidget, Widget, PlotWidget}
import org.nlogo.api.{ModelSection, PlotInterface, DummyLogoThunkFactory, CompilerServices}
import org.nlogo.swing.Implicits._
import org.nlogo.hubnet.client.{ClientAWTEvent, ClientAWTExceptionEvent}

// Normally we try not to use the org.nlogo.window.Events stuff except in
// the app and window packages.  But currently there's no better
// way to find out when a button was pressed or a slider (etc.)
// moved, so we use events.  - ST 8/24/03
class RunsPanel(editorFactory:org.nlogo.window.EditorFactory, compiler:CompilerServices) extends JPanel with
        org.nlogo.window.Events.AddSliderConstraintEvent.Handler {

  // runsGUI contains an InterfacePanelLite, which contains all the widgets.
  // it is constructed in completeLogin
  var runsGUI:RunsGUI = null
  var viewWidget:RunsView = null
  private val plotManager = new PlotManager(new DummyLogoThunkFactory())
  // not yet used
  private var activityName: String = null

  locally {
    setBackground(java.awt.Color.white)
    setLayout(new java.awt.BorderLayout())
  }

  // we probably don't need this for runs.
  def setDisplayOn(on: Boolean) { if (viewWidget != null) viewWidget.setDisplayOn(on) }

  // TODO: figure out - do we need this for runs?
  def handle(e: org.nlogo.window.Events.AddSliderConstraintEvent) {
    e.slider.setSliderConstraint(
      new ConstantSliderConstraint(e.minSpec.toDouble, e.maxSpec.toDouble, e.incSpec.toDouble){ defaultValue = e.value })
  }

  /// Message Handlers
  private def handleWidgetControlMessage(value: Any, widgetName: String) {
    org.nlogo.awt.Utils.mustBeEventDispatchThread()
    if (widgetName == "VIEW") value match {
      case t: HubNetTurtleStamp => viewWidget.renderer.stamp(t)
      case ls: HubNetLinkStamp => viewWidget.renderer.stamp(ls)
      case l: HubNetLine => viewWidget.renderer.drawLine(l)
      case _ => viewWidget.renderer.clearDrawing()
    }
    // i can't actually fathom who would have thought to do this this way
    // or for that matter, what this actually means...
    // i guess it just clears all plots. why is the widgetName "ALL PLOTS"
    // instead of "CLEAR ALL PLOTS"?
    // this whole thing is ridiculous.
    else if (widgetName=="ALL PLOTS") {
      plotManager.clearAll()
      for (pw <- runsGUI.getInterfaceComponents.collect { case pw: PlotWidget => pw }) {
        pw.makeDirty()
        pw.repaintIfNeeded()
      }
    }
    // finally some code that makes sense.
    // find the widget and update its value.
    else findWidget(widgetName) match {
      case Some(w) => w match {
        case i: InterfaceGlobalWidget => i.valueObject(value)
        case m: MonitorWidget => m.value(value)
        case _ => throw new IllegalStateException(widgetName)
      }
      case _ =>
    }
  }

  private def findWidget(name:String) = {
    runsGUI.getInterfaceComponents.collect {case w: Widget => w}.find(_.displayName == name)
  }


  /**
   * Completes the login process. Called when a handshake message is received
   * from the server.
   */
  def completeLogin(handshake: HandshakeFromServer) {
    // TODO: it doesn't appear that we are using this name anywhere.
    // though it might be put to good use at some point. 
    activityName = handshake.activityName
    if (runsGUI != null) remove(runsGUI)
    plotManager.forgetAll()
    viewWidget = new RunsView()
    runsGUI = new RunsGUI(editorFactory, viewWidget, plotManager, compiler)
    add(runsGUI, java.awt.BorderLayout.CENTER)
    val clientInterface = handshake.interfaceSpecList.first.asInstanceOf[ClientInterface]
    val widgets = clientInterface.widgetDescriptions
    new LoadSectionEvent("HubNet", ModelSection.WIDGETS, widgets.toArray, widgets.mkString("\n")).raise(this)
    // so that constrained widgets can initialize themselves -- CLB
    new AfterLoadEvent().raise(this)
    runsGUI.setChoices(clientInterface.chooserChoices.toMap)
    viewWidget.renderer.replaceTurtleShapes(toJavaList(clientInterface.turtleShapes))
    viewWidget.renderer.replaceLinkShapes(toJavaList(clientInterface.linkShapes))
    invokeLater(() => {
      getFrame(RunsPanel.this).pack()
      // in robo fixture, this generated exceptions now and again
      runsGUI.requestFocus()
    })
  }

  // TODO: all casting here is terrible.
  // TODO: it doesn't seem that we can actually get an ClientAWTExceptionEvent here.
  // in hubnet you can, because the (socket) Listener posts it.
  // there is no Listener in runs. - JC 6/10/11
  override def processEvent(e: AWTEvent) {
    if (e.isInstanceOf[ClientAWTEvent] && e.getSource == this) {
      val clientEvent = e.asInstanceOf[ClientAWTEvent]
      try if (clientEvent.isInstanceOf[ClientAWTExceptionEvent])
        handleEx(clientEvent.info.asInstanceOf[Exception],
          clientEvent.asInstanceOf[ClientAWTExceptionEvent].sendingException)
      else if (clientEvent.receivedData) receiveData(clientEvent.info)
      catch {case ex: RuntimeException => org.nlogo.util.Exceptions.handle(ex)}
    } else super.processEvent(e)
  }


  // TODO: We get in
  private def handleEx(e: Exception, sendingEx: Boolean) {
    org.nlogo.awt.Utils.mustBeEventDispatchThread()
    e.printStackTrace()
  }

  // called from processEvent
  // no reason to think the message wouldnt be a Message object.
  // if it is, we have a coding error. 
  private def receiveData(a: Any): Unit = {
    org.nlogo.awt.Utils.mustBeEventDispatchThread()
    a match {
      case m: Message => handleProtocolMessage(m)
    }
  }

  def handleProtocolMessage(message: org.nlogo.hubnet.protocol.Message) {
    message match {
      case h: HandshakeFromServer => completeLogin(h)
      case WidgetControl(content, tag) => handleWidgetControlMessage(content, tag)
      // TODO: we probably don't have to handle this case for Runs - JC 6/10/11
      case DisableView => setDisplayOn(false)
      case ViewUpdate(worldData) => viewWidget.updateDisplay(worldData)
      // TODO: we should probably take this time to rewrite plot mirroring - JC 6/10/11
      case PlotControl(content, plotName) => handlePlotControlMessage(content, plotName)
      case PlotUpdate(plot) => handlePlotUpdate(plot)
      // TODO: we probably don't have to handle these cases for Runs - JC 6/10/11
      // but i guess its possible that they could be helpful in the future
      case OverrideMessage(data, clear) => viewWidget.handleOverrideList(data.asInstanceOf[OverrideList], clear)
      case ClearOverrideMessage => viewWidget.clearOverrides()
      case AgentPerspectiveMessage(bytes) => viewWidget.handleAgentPerspective(bytes)
    }
  }

  // here be dragons //
  // plot mirroring code is below. its bad news. - JC 6/10/11

  def handlePlotUpdate(msg: PlotInterface) {
    for (pw <- runsGUI.getInterfaceComponents.collect {case pw: PlotWidget => pw}) {
      if (pw.plot.name==msg.name) {
        pw.plot.clear()
        updatePlot(msg.asInstanceOf[org.nlogo.plot.Plot], pw.plot)
        pw.repaintIfNeeded()
      }
    }
  }

  // TODO: couldnt we use case class copy here or something?
  private def updatePlot(plot1: Plot, plot2: Plot) {
    plot2.currentPen = plot2.getPen(plot1.currentPen.get.name)
    plot2.autoPlotOn = plot1.autoPlotOn
    plot2.xMin = plot1.xMin
    plot2.xMax = plot1.xMax
    plot2.yMin = plot1.yMin
    plot2.yMax = plot1.yMax
    for (pen1 <- plot1.pens) {
      val pen2 = if (pen1.temporary) plot2.createPlotPen(pen1.name, true)
      else plot2.getPen(pen1.name).get
      pen2.x = pen1.x
      pen2.color = pen1.color
      pen2.interval = pen1.interval
      pen2.isDown = pen1.isDown
      pen2.mode = pen1.mode
      pen2.points ++= pen1.points
    }
  }

  // this is the master method for handling plot messages. it should probably be redone.
  private def handlePlotControlMessage(value: Any, plotName:String) {
    org.nlogo.awt.Utils.mustBeEventDispatchThread()
    val plotWidget = findWidget(plotName).asInstanceOf[Option[PlotWidget]].get // horrible.
    value match {
      // This instance sets the current-plot-pen
      case s:String =>
        plotWidget.plot.currentPen=plotWidget.plot.getPen(s).getOrElse(plotWidget.plot.createPlotPen(s, true))
      // This instance sets the plot-pen-color
      case i: Int => plotWidget.plot.currentPenOrBust.color=(i)
      // This instance sets plot-pen-up and down
      case b: Boolean =>
        plotWidget.plot.currentPenOrBust.isDown = b
        plotWidget.makeDirty()
        plotWidget.repaintIfNeeded()
      // This instance is a point to plot
      case p: HubNetPlotPoint =>
        // points may or may not contain a specific X coordinate.
        // however, this is only the case in narrowcast plotting
        // plot mirroring always sends both coordinates even if
        // auto-plot is on. ev 8/18/08
        if (p.specifiesXCor) plotWidget.plot.currentPenOrBust.plot(p.xcor, p.ycor)
        // if not, we'll just let the plot use the next one.
        else plotWidget.plot.currentPenOrBust.plot(p.ycor)
        plotWidget.makeDirty()
        plotWidget.repaintIfNeeded()
      // These instances do various plotting commands
      case c: Char => {
        try c match {
          case 'c' =>
            plotWidget.plot.clear
            plotWidget.makeDirty()
            plotWidget.repaintIfNeeded()
          case 'r' =>
            plotWidget.plot.currentPenOrBust.hardReset()
            plotWidget.makeDirty()
            plotWidget.repaintIfNeeded()
          case 'p' =>
            plotWidget.plot.currentPenOrBust.softReset()
            plotWidget.makeDirty()
            plotWidget.repaintIfNeeded()
          case 'n' =>
            plotWidget.plot.autoPlotOn = true
          case 'f' =>
            plotWidget.plot.autoPlotOn = false
          case _ => throw new IllegalStateException()
        } catch {case ex: RuntimeException => org.nlogo.util.Exceptions.handle(ex)}
      }
      // This instance changes the plot-pen-mode
      case s:Short =>
        plotWidget.plot.currentPenOrBust.mode = s.toInt
        plotWidget.makeDirty()
        plotWidget.repaintIfNeeded()
      // This instance changes the plot-pen-interval
      case d:Double => plotWidget.plot.currentPenOrBust.interval = d
      // This instance is used for anything that has a lot of data
      case list: List[_] => list(0) match {
        case 'x' =>
          val min: Double = list(1).asInstanceOf[Double]
          val max: Double = list(2).asInstanceOf[Double]
          plotWidget.plot.xMin = min
          plotWidget.plot.xMax = max
          plotWidget.makeDirty()
          plotWidget.repaintIfNeeded()
        case _ =>
          val min: Double = list(1).asInstanceOf[Double]
          val max: Double = list(2).asInstanceOf[Double]
          plotWidget.plot.yMin = min
          plotWidget.plot.yMax = max
          plotWidget.makeDirty()
          plotWidget.repaintIfNeeded()
      }
    }
  }
}
