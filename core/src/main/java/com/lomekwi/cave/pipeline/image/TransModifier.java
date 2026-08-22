package com.lomekwi.cave.pipeline.image;

import com.lomekwi.cave.pipeline.Modifier;
import com.lomekwi.cave.pipeline.Param;
import com.lomekwi.cave.pipeline.Source;

import java.util.List;

public class TransModifier extends Modifier<Transformable> {
    public Modifier.Val dx, dy, scaleX, scaleY, dRotation;
    private boolean flipX, flipY;
    public TransModifier(Source<?> source) {
        this(source, 0, 0, 1, 1, 0, false, false);
    }

    public TransModifier(Source<?> source, float dx, float dy, float scaleX, float scaleY, float dRotation, boolean flipX, boolean flipY) {
        super(source);
        this.dx = new Modifier.FixVal(); this.dx.set(dx);
        this.dy = new Modifier.FixVal(); this.dy.set(dy);
        this.scaleX = new Modifier.FixVal(); this.scaleX.set(scaleX);
        this.scaleY = new Modifier.FixVal(); this.scaleY.set(scaleY);
        this.dRotation = new Modifier.FixVal(); this.dRotation.set(dRotation);
        this.flipX = flipX;
        this.flipY = flipY;
    }

    public boolean flipX() { return flipX; }
    public boolean flipY() { return flipY; }
    public void flipX(boolean v) { flipX = v; }
    public void flipY(boolean v) { flipY = v; }

    @Override
    public String getName() { return "变换"; }

    @Override
    public <F extends Transformable> F modify(F frame, long time) {
        Transform t = frame.getTransform();
        t.applyLocal(dx.getFloat(), dy.getFloat(), scaleX.getFloat(), scaleY.getFloat(), dRotation.getFloat(), flipX, flipY);
        return frame;
    }

    @Override
    public List<Param<?>> getParams() {
        return List.of(
            new Param.FloatSpin("位移 X", dx, -9999, 9999, 1, 1).undo(Param.UndoMode.BATCHED),
            new Param.FloatSpin("位移 Y", dy, -9999, 9999, 1, 1).undo(Param.UndoMode.BATCHED),
            new Param.FloatSpin("缩放 X", scaleX, 0.01f, 100, 0.01f, 2).undo(Param.UndoMode.BATCHED),
            new Param.FloatSpin("缩放 Y", scaleY, 0.01f, 100, 0.01f, 2).undo(Param.UndoMode.BATCHED),
            new Param.FloatSpin("旋转", dRotation, -9999, 9999, 1, 1).undo(Param.UndoMode.BATCHED),
            new Param.BoolCheck("水平翻转", this::flipX, this::flipX).undo(Param.UndoMode.BATCHED),
            new Param.BoolCheck("垂直翻转", this::flipY, this::flipY).undo(Param.UndoMode.BATCHED)
        );
    }
}
