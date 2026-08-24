package com.lomekwi.cave.timeline;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;

import org.junit.BeforeClass;

/**
 * 为纯模型测试提供 headless 的 libGDX 环境（Gdx.app 等），
 * 避免在构建 Timeline/Track/Segment 时因未初始化 Gdx 而崩溃。
 */
public abstract class GdxTestBase {
    private static boolean initialized;

    @BeforeClass
    public static synchronized void initGdx() {
        if (initialized) return;
        initialized = true;
        var cfg = new HeadlessApplicationConfiguration();
        try {
            new HeadlessApplication(new ApplicationListener() {
                @Override public void create() {}
                @Override public void resize(int width, int height) {}
                @Override public void render() {}
                @Override public void pause() {}
                @Override public void resume() {}
                @Override public void dispose() {}
            }, cfg);
        } catch (Throwable t) {
            // 某些环境下 headless 后端不可用；模型测试大多不真正触碰 Gdx。
            throw new IllegalStateException("无法初始化 headless Gdx", t);
        }
    }
}
