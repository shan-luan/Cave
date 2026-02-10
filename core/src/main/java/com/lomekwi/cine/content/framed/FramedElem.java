package com.lomekwi.cine.content.framed;

import com.lomekwi.cine.content.Element;
import com.lomekwi.cine.pipeline.Processor;

/**
 * 能显示，有xy坐标，有wh尺寸的元素
 */
public class FramedElem extends Element {
    private final Framable content;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    public FramedElem(Framable content,int x,int y,int width,int height) {
        this.content = content;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
    @Override
    public Processor getNextProcessor() {
        return content.getNextProcessor();
    }

    public int getX() {
        return x;
    }
    public int getY() {
        return y;
    }
    public int getWidth() {
        return width;
    }
    public int getHeight() {
        return height;
    }
    public Framable getContent() {
        return content;
    }
}
