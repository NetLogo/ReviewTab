package org.nlogo.review

import collection.mutable.ArrayBuffer
import java.io.{ ObjectOutputStream, ObjectInputStream,
                 FileOutputStream, FileInputStream }
import org.nlogo.hubnet.mirroring.ServerWorld
import org.nlogo.hubnet.protocol.{ViewUpdate, Message, ClientInterface, HandshakeFromServer}
import org.nlogo.api.{World, WorldPropertiesInterface}

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

object Frame {
  def apply(tick:Int, diffs: Message*): Frame = new Frame(tick, diffs.toIterable)
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
    new WorldPropertiesInterface { def fontSize = Run.this.fontSize })

  def interface: ClientInterface = handshake.interfaceSpecList.get(0).asInstanceOf[ClientInterface]
  def addFrame(world:World) = {
    frames :+= Frame(world.ticks.toInt,
      ViewUpdate(worldBuffer.updateWorld(world, false).toByteArray)
      // TODO: include widget changes here
      // TODO: include plot messages here
    )
  }
  def max = frames.size - 1
  // since JList will display this to the user
  override def toString = name
}
