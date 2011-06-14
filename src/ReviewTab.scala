package org.nlogo.review

import java.awt.{ Dimension, BorderLayout }
import javax.swing._
import event.{ ListSelectionEvent, ListSelectionListener, ChangeEvent, ChangeListener }

import org.nlogo.awt.UserCancelException
import org.nlogo.awt.Utils.invokeLater
import org.nlogo.swing.FileDialog
import org.nlogo.swing.Implicits._
import org.nlogo.swing.PimpedJButton
import org.nlogo.util.Exceptions.ignoring
import org.nlogo.window.{Events, GUIWorkspace}
import org.nlogo.hubnet.client.ClientAWTEvent
import scala.collection.JavaConverters._
import org.nlogo.api._
import org.nlogo.hubnet.protocol.{HandshakeFromServer, ClientInterface}

class ReviewTab(workspace: GUIWorkspace) extends JPanel {

  var view = new RunsPanel(new org.nlogo.hubnet.client.EditorFactory(workspace), workspace)

  val scrubber = new JSlider{ slider =>
    addChangeListener(new ChangeListener{
      def stateChanged(p1: ChangeEvent) {
        currentlyVisibleRun.foreach{ r =>
          invokeLater{() =>
            r.updateTo(slider.getValue, view)
            view.repaint()
          }
        }
      }
    })
  }

  // this needs to get out of here.
  // we create this when a new run is created.
  // we need to go back to the teacher client branch and refresh our memories of
  // how we handled this there.
  def clientInterfaceSpec: ClientInterface = new ClientInterface(
    workspace.serverWidgetSpecs,
    workspace.world.turtleShapeList.getShapes.asScala,
    workspace.world.linkShapeList.getShapes.asScala)

  val listener = new NetLogoAdapter {
    val count = Iterator.from(0)
    override def tickCounterChanged(ticks: Double) {
      if(ticks == 0) {
        val handshake = new HandshakeFromServer(workspace.modelNameForDisplay, LogoList(clientInterfaceSpec))
        val newRun = new Run("run " + count.next(), handshake, viewProps.fontSize)
        runListModel.addElement(newRun)
        //runList.selectLastMaybe()
      }

      lastRun.addFrame(workspace)

      for(r <- currentlyVisibleRun)
        if(r eq lastRun)
          scrubber.setMaximum(r.max)
    }
  }

  def viewProps =
    if(workspace.getPropertiesInterface != null) workspace.getPropertiesInterface
    else new WorldPropertiesInterface { def fontSize = 10 }

  def currentlyVisibleRun: Option[Run] =
    Option(runList.getSelectedValue).map(_.asInstanceOf[Run])

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
            currentRun.get.updateTo(currentRun.get.frameNumber, view)
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

  // TODO: widgets also get added to the Interface tab!
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
      val path = FileDialog.show(this, "Save Runs", java.awt.FileDialog.SAVE, "runs.dat")
      Run.save(path, (0 until runListModel.size).map(n => runListModel.get(n).asInstanceOf[Run]))
    }
  }

  // TODO: BROKEN
  private def discard() {
    if(! runList.isSelectionEmpty)
      runList.remove(runList.getSelectedIndex)
  }

  // TODO: Discards all, but doesn't clear the view. 
  private def discardAll() {
    runListModel.clear()
  }

  private def scroller(c: java.awt.Component) =
    new JScrollPane(c,
                    ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED)

}
