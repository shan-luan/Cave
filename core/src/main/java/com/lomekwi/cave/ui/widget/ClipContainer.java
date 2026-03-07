package com.lomekwi.cave.ui.widget;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Container;

/**
 * 默认启用裁剪的容器，自动使子Actor填满容器边界。
 *
 * @param <T> 子Actor类型
 */
public class ClipContainer<T extends Actor> extends Container<T> {

    /**
     * 创建包含指定Actor的裁剪容器。
     *
     * @param actor 子Actor
     */
    public ClipContainer(T actor) {
        super(actor);
        setClip(true);
    }

    /**
     * 验证布局后强制子Actor大小位置与容器一致。
     */
    @Override
    public void validate() {
        if(needsLayout()){
            getActor().setBounds(getX(), getY(), getWidth(), getHeight());
        }
        super.validate();
    }
}
