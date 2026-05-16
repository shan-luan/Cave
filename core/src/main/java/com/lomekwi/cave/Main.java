/*
 * Cave - CAVE's Another Video Editor
 * Copyright (C) 2026 Cave Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.lomekwi.cave;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.ui.Root;
import com.lomekwi.cave.app.Vars;

import games.spooky.gdx.nativefilechooser.NativeFileChooser;


/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter {
    private Root ui;

    public Main (NativeFileChooser fileChooser) {
        Vars.fileChooser = fileChooser;
    }
    @Override
    public void create() {
        Gdx.app.setLogLevel(Application.LOG_DEBUG);
        ui = new Root(this);
        ui.create();
    }

    @Override
    public void render() {
        Project p = ui.getFrontendProject();
        if(p!= null) {
            p.update();
        }
        ui.render();
    }

    @Override
    public void dispose() {
        ui.dispose();
    }
    @Override
    public void resize(int width, int height) {
        ui.resize(width, height);
    }
    @Override
    public void pause() {
        ui.pause();
    }
    @Override
    public void resume() {
        ui.resume();
    }
}
