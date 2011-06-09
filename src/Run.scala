package org.nlogo.review

import collection.mutable.ArrayBuffer
import java.awt.image.BufferedImage
import java.io.{ ByteArrayOutputStream, ByteArrayInputStream,
                 ObjectOutputStream, ObjectInputStream,
                 FileOutputStream, FileInputStream }
import javax.imageio.ImageIO
import org.nlogo.hubnet.protocol.HandshakeFromServer

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
  var images: ArrayBuffer[Array[Byte]] = ArrayBuffer(),
  var frameNumber:Int = 0,
  var annotations:String = "") extends Serializable {

  def addFrame(b:BufferedImage) = {
    val out = new ByteArrayOutputStream()
    ImageIO.write(b, "PNG", out)
    images += out.toByteArray
  }
  def currentImage: BufferedImage =
    ImageIO.read(new ByteArrayInputStream(images(frameNumber)))
  def max = images.size - 1
  // since JList will display this to the user
  override def toString = name
}
