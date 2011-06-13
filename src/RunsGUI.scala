package org.nlogo.review

import org.nlogo.plot.PlotManager
import org.nlogo.api.{RandomServices,CompilerServices}
import org.nlogo.util.MersenneTwisterFast
import org.nlogo.window.{ButtonWidget,ChooserWidget,InterfacePanelLite}

import javax.swing.JPanel
import java.awt.{BorderLayout,Color,Insets}

class RunsGUI(editorFactory: org.nlogo.window.EditorFactory,runsView: RunsView,
                plotManager: PlotManager,compiler: CompilerServices) extends JPanel {

  private val interfacePanel =
    new InterfacePanelLite(runsView, compiler, new DummyRandomServices(), plotManager, editorFactory, true) {
    // TODO: we don't need this, but really, we dont want sliders to be moved at all.
    // nor any widgets for that matter. so, leaving this in here as a reminder. 
    sliderEventOnReleaseOnly(true)
    // intentionally do nothing.
    // we don't want the user to be able to press buttons on the runs GUI.
    override def getKeyAdapter = new ButtonKeyAdapter {
      override def buttonKeyed(button: ButtonWidget) {}
    }
  }

  locally {
    setBackground(Color.white)
    setLayout(new BorderLayout())
    add(interfacePanel,BorderLayout.CENTER)
  }

  override def getInsets = new Insets(5,5,5,5)
  override def requestFocus() { if (interfacePanel != null) interfacePanel.requestFocus() }
  def getInterfaceComponents = interfacePanel.getComponents

  // TODO: i think we've done this all better in the Hubnet-Teacher-Client branch.
  // better check. 
  def setChoices(chooserChoices: Map[String, org.nlogo.api.LogoList]) {
    def getWidget(name: String): ChooserWidget = {
      getInterfaceComponents.collect{case w:ChooserWidget => w}.find(_.displayName == name) match {
        case Some(w) => w
        case _ => throw new IllegalStateException("couldn't find widget " + name)
      }
    }
    for ((k,v) <- chooserChoices){ getWidget(k).setChoices(v) }
  }

  private class DummyRandomServices extends RandomServices {
    def auxRNG: MersenneTwisterFast = null
    def mainRNG: MersenneTwisterFast = null
  }
}
