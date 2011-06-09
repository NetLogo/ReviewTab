package org.nlogo.review

import collection.mutable.ArrayBuffer
import java.awt.image.BufferedImage
import java.io.{ ByteArrayOutputStream, ByteArrayInputStream,
                 ObjectOutputStream, ObjectInputStream,
                 FileOutputStream, FileInputStream }
import javax.imageio.ImageIO
import org.nlogo.hubnet.mirroring.DiffBuffer
import org.nlogo.hubnet.protocol.{ClientInterface, HandshakeFromServer}

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
class Run(var name: String,
  var handshake:HandshakeFromServer,
  var diffs: ArrayBuffer[DiffBuffer] = ArrayBuffer(),
  var frameNumber:Int = 0,
  var annotations:String = "") extends Serializable {

  def interface: ClientInterface = handshake.interfaceSpecList.get(0).asInstanceOf[ClientInterface]
  def addFrame(diff:DiffBuffer) = {
    diffs :+= diff
  }
  def max = diffs.size - 1
  // since JList will display this to the user
  override def toString = name
}
