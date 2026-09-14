/*
 * Cave - CAVE's Another Video Editor
 * Copyright (C) 2026 Cave Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.lomekwi.cave

import com.badlogic.gdx.Application
import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.lomekwi.cave.project.Project
import com.lomekwi.cave.ui.Root
import com.lomekwi.cave.app.App
import com.lomekwi.cave.app.AppAudioOut

import games.spooky.gdx.nativefilechooser.NativeFileChooser


/** 各平台共用的 {@link com.badlogic.gdx.ApplicationListener} 实现。 */
class Main(fileChooser: NativeFileChooser) extends ApplicationAdapter {
  private var ui: Root = null
  private var backgrounded: Boolean = false

  App.fileChooser = fileChooser

  override def create(): Unit = {
    App.audioOut = new AppAudioOut()
    Gdx.app.setLogLevel(Application.LOG_DEBUG)
    ui = new Root()
    ui.create()
  }

  override def render(): Unit = {
    val p: Project = ui.getFrontendProject()
    if (p != null) {
      p.update()
    }
    if (!backgrounded) {
      ui.render()
    }
  }

  override def dispose(): Unit = {
    ui.dispose()
  }
  override def resize(width: Int, height: Int): Unit = {
    ui.resize(width, height)
  }
  override def pause(): Unit = {
    ui.pause()
    backgrounded = true
  }
  override def resume(): Unit = {
    ui.resume()
    backgrounded = false
  }
}
