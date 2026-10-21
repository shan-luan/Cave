package com.lomekwi.cave.util

import java.io.*
import scala.util.Using
@deprecated("先等model完全不可变重构完")
trait Duplicatable[T <: Duplicatable[T]] extends Serializable {

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
