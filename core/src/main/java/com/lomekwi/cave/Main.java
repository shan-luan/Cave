package com.lomekwi.cave;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.timeline.playback.PlaybackState;
import com.lomekwi.cave.ui.Root;
import com.lomekwi.cave.util.Vars;

import games.spooky.gdx.nativefilechooser.NativeFileChooser;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter {
    private Root ui;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Future<?> updateFuture = null;
    private boolean seekTask;

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
            if(p.playhead.getStates().contains(PlaybackState.SEEKING)){
                if(seekTask){
                    if(updateFuture.isDone()){
                        p.playhead.clearState(PlaybackState.SEEKING);
                        seekTask = false;
                    }
                }else {
                    if(updateFuture != null) {
                        updateFuture.cancel(true);
                    }
                    updateFuture = executorService.submit(p::update);
                    seekTask = true;
                }
            }
            p.playhead.update();
            if (updateFuture == null || updateFuture.isDone()) {
                updateFuture = executorService.submit(p::update);
            }
        }
        ui.render();
    }

    @Override
    public void dispose() {
        // 关闭线程池并等待当前任务完成
        executorService.shutdown();
        try {
            if (updateFuture != null && !updateFuture.isDone()) {
                updateFuture.get(); // 等待当前更新任务完成
            }
        } catch (Exception e) {
            Gdx.app.error("Main", "Error waiting for update task to complete", e);
        }
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
