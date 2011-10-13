package org.nlogo.review

import javax.swing._
import event.{ ListSelectionEvent, ListSelectionListener, ChangeEvent, ChangeListener }

import org.nlogo.awt.UserCancelException
import org.nlogo.awt.EventQueue.invokeLater
import org.nlogo.swing.FileDialog
import org.nlogo.swing.Implicits._
import org.nlogo.swing.PimpedJButton
import org.nlogo.util.Exceptions.ignoring
import org.nlogo.window.GUIWorkspace
import org.nlogo.hubnet.client.ClientAWTEvent
import org.nlogo.api._
import org.nlogo.hubnet.protocol.HandshakeFromServer
import table.{TableCellEditor, TableCellRenderer, DefaultTableCellRenderer, AbstractTableModel}
import java.awt.{Dimension, Font, Component, BorderLayout}

class ReviewTab(workspace: GUIWorkspace) extends JPanel {

  val runList = new RunList()
  var interface = new RunsPanel(new org.nlogo.hubnet.client.EditorFactory(workspace), workspace)
  val scrubber = new Scrubber()
  val notesTable = new NotesTable()
  val listener = new TickListener()

  def currentlyVisibleRun: Option[Run] =
    Option(runList.getSelectedValue).map(_.asInstanceOf[Run])

  locally {
    setPreferredSize(new Dimension(1000, 800))
    setLayout(new BorderLayout)
    add(new JSplitPane(
      JSplitPane.VERTICAL_SPLIT,
      true, // continuous layout as the user drags
      new JPanel() {
        setPreferredSize(new Dimension(800, 500))
        setLayout(new BorderLayout)
        add(scroller(interface), BorderLayout.CENTER)
        add(scrubber, BorderLayout.SOUTH)
      },
      new JPanel{
        setLayout(new BorderLayout)
        add(new JPanel {
          setLayout(new BorderLayout)
          add(new JLabel("Notes"), BorderLayout.NORTH)
          add(scroller(notesTable), BorderLayout.CENTER)
          add(new JPanel(){
            add(PimpedJButton("Add Note For Current Tick"){
              notesTable.newNote(scrubber.getValue)
            })
            add(PimpedJButton("Add Note For Entire Run"){
              notesTable.newNote(-1)
            })
          }, BorderLayout.SOUTH)
        }, BorderLayout.CENTER)
      }) {
      setResizeWeight(.8)
      setOneTouchExpandable(true)
    }, BorderLayout.CENTER)

    add(
      new JPanel{
        setLayout(new BorderLayout)
        add(new JLabel("Runs"), BorderLayout.NORTH)
        add(scroller(runList), BorderLayout.CENTER)
        add(new JPanel{ pane =>
          setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS))
          val saveButton = PimpedJButton("Save") { save() }
          val loadButton = PimpedJButton("Load") { load() }
          val discardButton = PimpedJButton("Discard") { discard() }
          val discardAllButton = PimpedJButton("Discard All") { discardAll() }
          for(b <- List(saveButton, loadButton, discardButton, discardAllButton)){
            b.setAlignmentX(Component.CENTER_ALIGNMENT)
            b.setPreferredSize(discardAllButton.getPreferredSize)
            add(b)
          }
        }, BorderLayout.SOUTH)
      },
      BorderLayout.WEST)
    workspace.listenerManager.addListener(listener)
  }

  // TODO: widgets also get added to the Interface tab!
  private def load() {
    ignoring(classOf[UserCancelException]) {
      val path = FileDialog.show(this, "Open Runs", java.awt.FileDialog.LOAD, null)
      runList.model.clear()
      for (run <- Run.load(path))
        runList.model.addElement(run)
      if(runList.model.size > 0) runList.setSelectedIndex(0)
    }
  }

  private def save() {
    ignoring(classOf[UserCancelException]) {
      val path = FileDialog.show(this, "Save Runs", java.awt.FileDialog.SAVE, "runs.dat")
      Run.save(path, (0 until runList.model.size).map(n => runList.model.get(n).asInstanceOf[Run]))
    }
  }

  // TODO: BROKEN
  private def discard() {
    if(! runList.isSelectionEmpty){
      runList.model.remove(runList.getSelectedIndex)
    }
  }

  // TODO: Discards all, but doesn't clear the view. 
  private def discardAll() {
    runList.model.clear()
  }

  private def scroller(c: java.awt.Component) =
    new JScrollPane(c,
                    ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED)


  class Scrubber extends JSlider{ slider =>
    slider.setBorder(BorderFactory.createTitledBorder("Tick: N/A"))
    addChangeListener(new ChangeListener{
      def stateChanged(p1: ChangeEvent) {
        currentlyVisibleRun.foreach{ r =>
          invokeLater{() =>
            slider.setToolTipText(slider.getValue.toString)
            slider.setBorder(BorderFactory.createTitledBorder("Tick: " + slider.getValue))
            r.updateTo(slider.getValue, interface)
            notesTable.scrollTo(tick = slider.getValue)
            interface.repaint()
          }
        }
      }
    })
  }

  class TickListener extends NetLogoAdapter {
    val count = Iterator.from(0)
    override def tickCounterChanged(ticks: Double) {
      if (ticks == 0) {
        val newRun = new ActiveRun("run " + count.next(), workspace)
        runList.model.addElement(newRun)
        //runList.selectLastMaybe()
      }

      // TODO: this somehow doesn't work if you remove the last element.
      val lastRun = runList.model.lastElement().asInstanceOf[ActiveRun]
      lastRun.addFrame()

      for (r <- currentlyVisibleRun)
        if (r eq lastRun)
          scrubber.setMaximum(r.max)
    }
  }

  class RunList extends JList { list =>
    val model = new DefaultListModel()
    setModel(model)
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    setPreferredSize(new Dimension(100, 100))

    private var currentRun: Option[Run] = None
    def selectLastMaybe() { if(getSelectedIndex == -1) setSelectedIndex(model.size - 1) }

    this.getSelectionModel.addListSelectionListener(
      new ListSelectionListener {
        def valueChanged(p1: ListSelectionEvent) {
          if(list.getSelectedIndex != -1) {
            // save the notesTable on the previously selected run
            currentRun.foreach{ r =>
              // TODO: set notes on run here!
              r.notes = notesTable.notes
            }
            // change to the newly selected run.
            currentRun = Some(model.get(list.getSelectedIndex).asInstanceOf[Run])
            val handshake = HandshakeFromServer(currentRun.get.modelName, LogoList(currentRun.get.interface))
            getToolkit.getSystemEventQueue.postEvent(new ClientAWTEvent(interface, handshake, true))
            scrubber.setMaximum(currentRun.get.max)
            scrubber.setValue(currentRun.get.frameNumber)
            notesTable.setTo(currentRun.get.notes)
            currentRun.get.updateTo(currentRun.get.frameNumber, interface)
            invokeLater { () =>
              scrubber.repaint()
              interface.repaint()
            }
          }}})
  }

  class NotesTable extends JTable { table =>

    val NotesColumnName = "Notes"
    val TickColumnName = "Tick"
    val ButtonsColumnName = "Buttons"
    def notesColumn = getColumn(NotesColumnName)
    def tickColumn = getColumn(TickColumnName)
    def buttonsColumn = getColumn(ButtonsColumnName)

    val model = new NotesTableModel()

    def setTo(notes:List[Note]) {
      model.notes.clear();
      model.notes ++= notes
    }

    def scrollTo(tick:Int){
      val rowIndex = notes.indexWhere(_.tick == tick)
      if(rowIndex != -1)
        table.scrollRectToVisible(table.getCellRect(rowIndex, 0, false))
    }

    locally{
      setModel(model)

      setRowHeight(getRowHeight + 10)
      setGridColor(java.awt.Color.BLACK)
      setShowGrid(true)
      setRowSelectionAllowed(false)

      tickColumn.setMaxWidth(50)
      notesColumn.setMinWidth(200)
      buttonsColumn.setMaxWidth(80)
      buttonsColumn.setMinWidth(40)
      buttonsColumn.setCellRenderer(new ButtonCellEditor)
      buttonsColumn.setCellEditor(new ButtonCellEditor)
      buttonsColumn.setHeaderValue("")
    }

    def notes: List[Note] = model.notes.toList
    
    // add a dummy note to the list so that the user can then modify it.
    def newNote(tick: Int) {
      model.addNote(Note(tick = tick))
    }

    // someone pressed the delete button in the notes row.
    def removeNote(index: Int) { model.removeNote(index) }

    def openAdvancedNoteEditor(editingNote: Note) {
//      val p = new NoteEditorAdvanced(editingNote)
//      new org.nlogo.swing.Popup(frame, I18N.gui("editing") + " " + editingNote.name, p, (), {
//        p.getResult match {
//          case Some(p) =>
//            model.notes(getSelectedRow) = p
//            table.removeEditor()
//            table.repaint()
//            true
//          case _ => false
//        }
//      }, I18N.gui.get _).show()
    }

    // renders the delete and edit buttons for each column
    class ButtonCellEditor extends AbstractCellEditor with TableCellRenderer with TableCellEditor {
      val editButton = PimpedJButton(new javax.swing.ImageIcon(getClass.getResource("/images/edit.gif"))) {
        openAdvancedNoteEditor(model.notes(getSelectedRow))
      }
      val deleteButton = PimpedJButton(new javax.swing.ImageIcon(getClass.getResource("/images/delete.gif"))) {
        val index = getSelectedRow
        removeEditor()
        clearSelection()
        removeNote(index)
      }
      editButton.putClientProperty("JComponent.sizeVariant", "small")
      deleteButton.putClientProperty("JComponent.sizeVariant", "small")
      val buttonPanel = new JPanel {add(editButton); add(deleteButton)}
      def getTableCellRendererComponent(table: JTable, value: Object,
                                        isSelected: Boolean, hasFocus: Boolean,
                                        row: Int, col: Int) = buttonPanel
      def getTableCellEditorComponent(table: JTable, value: Object,
                                      isSelected: Boolean, row: Int, col: Int) = buttonPanel
      def getCellEditorValue = ""
    }

    class NotesTableModel extends AbstractTableModel {
      val columnNames = scala.List(TickColumnName, NotesColumnName, ButtonsColumnName)
      var notes = scala.collection.mutable.ListBuffer[Note]()

      override def getColumnCount = columnNames.length
      override def getRowCount = notes.length
      override def getColumnName(col: Int) = columnNames(col)
      override def isCellEditable(row: Int, col: Int) = {
        if(col == columnNames.indexOf(TickColumnName)) false
        else true
      }

      override def getValueAt(row: Int, col: Int) = {
        val n = notes(row)
        columnNames(col) match {
          case TickColumnName => n.tick.asInstanceOf[AnyRef]
          case NotesColumnName => n.text
          case _ => None
        }
      }
      override def getColumnClass(c: Int) = {
        columnNames(c) match {
          case TickColumnName => classOf[java.lang.Integer]
          case _ => classOf[String]
        }
      }
      override def setValueAt(value: Object, row: Int, col: Int) {
        if (row < notes.size) {
          val p = notes(row)
          columnNames(col) match {
            case TickColumnName => notes(row) = p.copy(tick = value.asInstanceOf[String].toInt)
            case NotesColumnName => notes(row) = p.copy(text = value.asInstanceOf[String])
            case _ =>
          }
          fireTableCellUpdated(row, col)
        }
      }

      def addNote(n: Note) {
        notes += n;
        notes = notes.sorted
        fireTableDataChanged()
      }

      def removeNote(index: Int) {
        if (index != -1) {
          notes.remove(index)
          fireTableRowsDeleted(index, index)
          removeEditor()
          revalidate()
          repaint()
        }
      }
    }
  }
}
