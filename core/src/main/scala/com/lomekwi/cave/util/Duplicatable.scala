package com.lomekwi.cave.util

import java.io.*
import scala.util.Using

trait Duplicatable[T <: Duplicatable[T]] extends Serializable {

  /*
   * 创建一个深拷贝
   */
  def duplicate(): T = {
    try {
      Using.resource(new ByteArrayOutputStream()) { baos =>
        Using.resource(new ObjectOutputStream(baos)) { oos =>
          oos.writeObject(this)
          Using.resource(new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray))) { ois =>
            ois.readObject().asInstanceOf[T]
          }
        }
      }
    } catch {
      case e: IOException => throw new RuntimeException("Duplicate failed", e)
      case e: ClassNotFoundException => throw new RuntimeException("Duplicate failed", e)
    }
  }
}
