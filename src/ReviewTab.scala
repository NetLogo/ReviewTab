package org.nlogo.review

import java.awt.{ Dimension, BorderLayout, Graphics }
import javax.swing._
import event.{ ListSelectionEvent, ListSelectionListener, ChangeEvent, ChangeListener }

import org.nlogo.awt.UserCancelException
import org.nlogo.awt.Utils.invokeLater
import org.nlogo.swing.FileDialog
import org.nlogo.swing.Implicits._
import org.nlogo.swing.PimpedJButton
import org.nlogo.util.Exceptions.ignoring
import org.nlogo.window.{Events, GUIWorkspace}
import org.nlogo.window.Events.LoadSectionEvent
import org.nlogo.hubnet.client.ClientAWTEvent
import scala.collection.JavaConverters._
import org.nlogo.hubnet.mirroring.ServerWorld
import org.nlogo.api._
import org.nlogo.hubnet.protocol.{ViewUpdate, HandshakeFromServer, ClientInterface}

class ReviewTab(workspace: GUIWorkspace) extends JPanel with Events.LoadSectionEvent.Handler {

  val interface =
    workspace.getWidgetContainer.asInstanceOf[java.awt.Component]

  def currentlyVisibleRun: Option[Run] =
    Option(runList.getSelectedValue).map(_.asInstanceOf[Run])

  val view = new RunsPanel(new org.nlogo.hubnet.client.EditorFactory(workspace), workspace)

  val scrubber = new JSlider{ slider =>
    addChangeListener(new ChangeListener{
      def stateChanged(p1: ChangeEvent) {
        currentlyVisibleRun.foreach{ r =>
          invokeLater{() =>
            r.frameNumber = slider.getValue
            view.viewWidget.newWorld()
            //for(d<-r.diffs.take(r.frameNumber))
            getToolkit.getSystemEventQueue.postEvent(new ClientAWTEvent(view, new ViewUpdate(r.diffs(r.frameNumber).toByteArray), true))
            view.repaint()
          }
        }
      }
    })
  }

  var widgetDescriptions: Array[String] = Array()

  private val world = workspace.world()
  private var worldBuffer = new ServerWorld(
    if(workspace.getPropertiesInterface != null) workspace.getPropertiesInterface
    else new WorldPropertiesInterface { def fontSize = 10 } // TODO BAD HACK! JC 12/28/10
  )

  private def createClientInterfaceSpec: ClientInterface = {
    val widgets = ModelReader.parseWidgets(widgetDescriptions).asScala.map(_.asScala)
    val clientInterfaceSpec = new ClientInterface(widgets, widgetDescriptions.toList,
      workspace.world.turtleShapeList.getShapes.asScala,
      workspace.world.linkShapeList.getShapes.asScala, workspace)
    clientInterfaceSpec
  }

  def handle(e:LoadSectionEvent){
    if(e.section == ModelSection.WIDGETS){
      widgetDescriptions = e.lines
    }
  }

  val listener = new NetLogoAdapter {
    val count = Iterator.from(0)
    override def tickCounterChanged(ticks: Double) {
      if(ticks == 0) {
        val handshake = new HandshakeFromServer(workspace.modelNameForDisplay, LogoList(createClientInterfaceSpec))
        val newRun = new Run("run " + count.next(), handshake)
        runListModel.addElement(newRun)
        //runList.selectLastMaybe()
      }
      workspace.updateUI()

      // TRUE = reset entire world. this is bad. just hacking for now.
      val diff = worldBuffer.updateWorld(world, true)
      lastRun.addFrame(diff)

      for(r <- currentlyVisibleRun)
        if(r eq lastRun)
          scrubber.setMaximum(r.max)
    }
  }

  def lastRun =
    runListModel.get(runListModel.size - 1).asInstanceOf[Run]

  val annotations = new JTextArea(5, 50)

  val runListModel = new DefaultListModel()
  val runList = new JList { list =>
    setModel(runListModel)
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    setPreferredSize(new Dimension(100, 100))

    private var currentRun: Option[Run] = None
    def selectLastMaybe() =
      if(getSelectedIndex == -1) setSelectedIndex(runListModel.size - 1)

    this.getSelectionModel.addListSelectionListener(
      new ListSelectionListener {
        def valueChanged(p1: ListSelectionEvent) {
          if(list.getSelectedIndex != -1) {
            // save the annotations on the previously selected run
            currentRun.foreach{ r =>
              r.annotations = annotations.getText
            }
            // change to the newly selected run.
            currentRun = Some(runListModel.get(list.getSelectedIndex).asInstanceOf[Run])
            getToolkit.getSystemEventQueue.postEvent(new ClientAWTEvent(view, currentRun.get.handshake, true))
            scrubber.setMaximum(currentRun.get.max)
            scrubber.setValue(currentRun.get.frameNumber)
            annotations.setText(currentRun.get.annotations)
            invokeLater { () =>
              scrubber.repaint()
              view.repaint()
            }
          }}})
  }

  locally {
    setLayout(new BorderLayout)
    add(new JSplitPane(
      JSplitPane.VERTICAL_SPLIT,
      true, // continuous layout as the user drags
      new JPanel() {
        setLayout(new BorderLayout)
        add(scroller(view), BorderLayout.CENTER)
        add(scrubber, BorderLayout.SOUTH)
      }, new JPanel{
        setLayout(new BorderLayout)
        add(scroller(annotations), BorderLayout.CENTER)
        add(new JPanel{
          add(PimpedJButton("Save") { save() })
          add(PimpedJButton("Load") { load() })
          add(PimpedJButton("Discard") { discard() })
          add(PimpedJButton("Discard All") { discardAll() })
        }, BorderLayout.SOUTH)
      }) {
      setOneTouchExpandable(true)
      setResizeWeight(1) // give the InterfacePanel all
    }, BorderLayout.CENTER)

    add(scroller(runList), BorderLayout.WEST)
    workspace.listenerManager.addListener(listener)
  }

  private def load() {
    ignoring(classOf[UserCancelException]) {
      val path = FileDialog.show(this, "Open Runs", java.awt.FileDialog.LOAD, null)
      runListModel.clear()
      for (run <- Run.load(path))
        runListModel.addElement(run)
      if(runListModel.size > 0) runList.setSelectedIndex(0)
    }
  }

  private def save() {
    ignoring(classOf[UserCancelException]) {
      val path = FileDialog.show(this, "Save Runs", java.awt.FileDialog.SAVE, "runs.txt")
      Run.save(path, (0 until runListModel.size).map(n => runListModel.get(n).asInstanceOf[Run]))
    }
  }

  private def discard() {
    if(! runList.isSelectionEmpty)
      runList.remove(runList.getSelectedIndex)
  }

  private def discardAll() {
    runListModel.clear()
  }



  private def scroller(c: java.awt.Component) =
    new JScrollPane(c,
                    ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED)

}
