package com.lomekwi.cave.ui.editpanel.mediapool

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.google.common.eventbus.{EventBus, Subscribe}
import com.kotcrab.vis.ui.layout.FlowGroup
import com.kotcrab.vis.ui.widget.{VisImage, VisTable}
import com.lomekwi.cave.app.App
import com.lomekwi.cave.project.{ProjectResourcesChangedEvent, ProjectSession}
import com.lomekwi.cave.resource.media.{MediaCreatedEvent, Showable}
import com.lomekwi.cave.ui.widget.EllipsisLabel

import java.io.File
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*


class MediaPool(private val context: ProjectSession, eventBus: EventBus) extends FlowGroup(false) {
  private var dnd: DragAndDrop = uninitialized

  {
    setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled)

    dnd = App.root.dragAndDrop

    dnd.addTarget(new DragAndDrop.Target(this) {
      override def drag(source: DragAndDrop.Source, payload: DragAndDrop.Payload, x: Float, y: Float, pointer: Int): Boolean = {
        payload.getObject.isInstanceOf[File]
      }

      override def drop(source: DragAndDrop.Source, payload: DragAndDrop.Payload, x: Float, y: Float, pointer: Int): Unit = {
        if (!source.getActor.isDescendantOf(MediaPool.this)) {
          val file = payload.getObject.asInstanceOf[File]
          if (!context.project.resources.contains(file)) {
            addItem(file, findShowable(file))
          }
        }
      }
    })

    for (file <- context.project.resources.keys) {
      addItem(file, findShowable(file))
    }

    eventBus.register(this)
  }

  private def findShowable(file: File): Showable = {
    context.project.resources.get(file).flatMap(_.collectFirst { case showable: Showable => showable }).orNull
  }

  private def isListed(file: File): Boolean = getChildren.asScala.exists {
    case item: MediaPoolItem => item.file.equals(file)
    case _ => false
  }

  private def addItem(file: File, previewable: Showable): Unit = {
    val item = new MediaPoolItem(file, previewable)
    addActor(item)
    item.source = registerDragSegment(item)
  }

  @Subscribe
  def onMediaCreated(event: MediaCreatedEvent): Unit = {
    if (!isListed(event.file)) {
      val previewable: Showable = event.medRes match {
        case showable: Showable => showable
        case _ => null
      }
      addItem(event.file, previewable)
    }
  }

  @Subscribe
  def onResourcesChanged(event: ProjectResourcesChangedEvent): Unit = {
    val items = getChildren.asScala.collect { case item: MediaPoolItem => item }.toVector
    for (item <- items if event.removed.contains(item.file)) {
      dnd.removeSource(item.source)
      item.remove()
    }
    for (file <- event.added if !isListed(file)) {
      addItem(file, findShowable(file))
    }
  }

  private def registerDragSegment(item: MediaPoolItem): DragAndDrop.Source = {
    val source = new DragAndDrop.Source(item) {
      override def dragStart(event: InputEvent, x: Float, y: Float, pointer: Int): DragAndDrop.Payload = {
        val payload = new DragAndDrop.Payload()
        payload.setObject(item.file)
        payload.setDragActor(new MediaPoolItem(item.file, item.previewable))
        payload
      }
    }
    dnd.addSource(source)
    source
  }

  private class MediaPoolItem(val file: File, private[mediapool] val previewable: Showable) extends VisTable() {
    private[mediapool] var source: DragAndDrop.Source = uninitialized
    private final val image: VisImage = new VisImage(new Texture("libgdx.png"))
    private var requested: Boolean = false

    image.setScaling(com.badlogic.gdx.utils.Scaling.fit)
    add(image).size(128, 72).pad(4).row()
    add(new EllipsisLabel(file.getName, 10)).padBottom(4)

    if (previewable != null) {
      requested = true
    }

    override def act(delta: Float): Unit = {
      super.act(delta)
      if (requested) {
        val tex = previewable.getPreview
        if (tex != null) {
          image.setDrawable(null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable])
          image.setDrawable(new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(tex))
          requested = false
        }
      }
    }

  }

  override def getMinWidth: Float = {
    0
  }

  override def getMinHeight: Float = {
    0
  }
}
