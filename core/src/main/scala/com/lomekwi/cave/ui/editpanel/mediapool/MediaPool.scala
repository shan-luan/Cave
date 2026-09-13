package com.lomekwi.cave.ui.editpanel.mediapool

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.graphics.Texture
import com.google.common.eventbus.EventBus
import com.google.common.eventbus.Subscribe
import com.kotcrab.vis.ui.layout.FlowGroup
import com.kotcrab.vis.ui.widget.VisImage
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisTable
import com.google.common.collect.Multimap
import com.google.common.collect.Multimap
import com.lomekwi.cave.resource.Resource
import com.lomekwi.cave.resource.media.MediaCreatedEvent
import com.lomekwi.cave.resource.media.Showable
import com.lomekwi.cave.ui.widget.EllipsisLabel

import com.lomekwi.cave.app.App

import java.io.File

import scala.jdk.CollectionConverters.*


class MediaPool(private val resources: Multimap[File, Resource], eventBus: EventBus) extends FlowGroup(false) {
  private var dnd: DragAndDrop = null

  {
    setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled)

    dnd = App.root.getDragAndDrop()

    dnd.addTarget(new DragAndDrop.Target(this) {
      override def drag(source: DragAndDrop.Source, payload: DragAndDrop.Payload, x: Float, y: Float, pointer: Int): Boolean = {
        payload.getObject().isInstanceOf[File]
      }

      override def drop(source: DragAndDrop.Source, payload: DragAndDrop.Payload, x: Float, y: Float, pointer: Int): Unit = {
        if (source.getActor().isDescendantOf(MediaPool.this)) return

        val file = payload.getObject().asInstanceOf[File]
        if (!resources.containsKey(file)) {
          val item = new MediaPoolItem(file, findShowable(file))
          addActor(item)
          registerDragSource(item)
        }
      }
    })

    for (file <- resources.keySet().asScala) {
      val item = new MediaPoolItem(file, findShowable(file))
      addActor(item)
      registerDragSource(item)
    }

    eventBus.register(this)
  }

  private def findShowable(file: File): Showable = {
    val it = resources.get(file).iterator()
    while (it.hasNext) {
      val r = it.next()
      if (r.isInstanceOf[Showable]) return r.asInstanceOf[Showable]
    }
    null
  }

  @Subscribe
  def onMediaCreated(event: MediaCreatedEvent): Unit = {
    val file = event.file
    val it = getChildren().iterator()
    while (it.hasNext) {
      val actor = it.next()
      actor match {
        case item: MediaPoolItem if item.getFile().equals(file) =>
          return
        case _ =>
      }
    }
    val pv: Showable = if (event.medRes.isInstanceOf[Showable])
      event.medRes.asInstanceOf[Showable] else null
    val item = new MediaPoolItem(file, pv)
    addActor(item)
    registerDragSource(item)
  }

  private def registerDragSource(item: MediaPoolItem): Unit = {
    dnd.addSource(new DragAndDrop.Source(item) {
      override def dragStart(event: InputEvent, x: Float, y: Float, pointer: Int): DragAndDrop.Payload = {
        val payload = new DragAndDrop.Payload()
        payload.setObject(item.getFile())
        payload.setDragActor(new MediaPoolItem(item.getFile(), item.previewable))
        payload
      }
    })
  }

  class MediaPoolItem(private val file: File, private[mediapool] val previewable: Showable) extends VisTable() {
    private final val image: VisImage = new VisImage(new Texture("libgdx.png"))
    private var requested: Boolean = false

    image.setScaling(com.badlogic.gdx.utils.Scaling.fit)
    add(image).size(128, 72).pad(4).row()
    add(new EllipsisLabel(file.getName(), 10)).padBottom(4)

    if (previewable != null) {
      requested = true
    }

    override def act(delta: Float): Unit = {
      super.act(delta)
      if (requested) {
        val tex = previewable.getPreview()
        if (tex != null) {
          image.setDrawable(null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable])
          image.setDrawable(new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(tex))
          requested = false
        }
      }
    }

    def getFile(): File = {
      file
    }
  }

  override def getMinWidth(): Float = {
    0
  }

  override def getMinHeight(): Float = {
    0
  }
}
