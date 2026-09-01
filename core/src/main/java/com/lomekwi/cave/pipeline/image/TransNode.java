package com.lomekwi.cave.pipeline.image;

import com.lomekwi.cave.pipeline.Filter;
import com.lomekwi.cave.pipeline.Node;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.pipeline.num.NumFrame;

public class TransNode extends Filter<Transformable> {

    private final Node.InPort<NumFrame> dx = addInPort(
        new Node.InPort<NumFrame>("位移 X", new NumFrame(null), NumFrame.class) {});
    private final Node.InPort<NumFrame> dy = addInPort(
        new Node.InPort<NumFrame>("位移 Y", new NumFrame(null), NumFrame.class) {});
    private final Node.InPort<NumFrame> scaleX = addInPort(
        new Node.InPort<NumFrame>("缩放 X", frame(1), NumFrame.class) {});
    private final Node.InPort<NumFrame> scaleY = addInPort(
        new Node.InPort<NumFrame>("缩放 Y", frame(1), NumFrame.class) {});
    private final Node.InPort<NumFrame> dRotation = addInPort(
        new Node.InPort<NumFrame>("旋转", new NumFrame(null), NumFrame.class) {});

    private boolean flipX, flipY;

    private final FilterIn in = addInPort(new FilterIn("输入") {
    });

    private final FilterOut out = addOutPort(new FilterOut("输出") {
        @Override
        public Transformable getData() {
            Transformable frame = getFilterIn().getData();
            if (frame != null) {
                Transform t = frame.getTransform();
                if (t == null) {
                    t = new Transform();
                    frame.setTransform(t);
                }
                t.applyLocal((float) val(dx), (float) val(dy), (float) val(scaleX), (float) val(scaleY),
                        (float) val(dRotation), flipX, flipY);
            }
            return frame;
        }
    });

    public TransNode() {
    }

    public TransNode(double dx, double dy, double scaleX, double scaleY, double dRotation) {
        setDx(dx);
        setDy(dy);
        setScaleX(scaleX);
        setScaleY(scaleY);
        setDRotation(dRotation);
    }

    private static NumFrame frame(double v) {
        NumFrame f = new NumFrame(null);
        f.setVal(v);
        return f;
    }

    private static double val(Node.InPort<NumFrame> p) {
        return p.getData().getVal();
    }

    public double getDx() {
        return val(dx);
    }

    public void setDx(double v) {
        dx.getDefaultData().setVal(v);
    }

    public double getDy() {
        return val(dy);
    }

    public void setDy(double v) {
        dy.getDefaultData().setVal(v);
    }

    public double getScaleX() {
        return val(scaleX);
    }

    public void setScaleX(double v) {
        scaleX.getDefaultData().setVal(v);
    }

    public double getScaleY() {
        return val(scaleY);
    }

    public void setScaleY(double v) {
        scaleY.getDefaultData().setVal(v);
    }

    public double getDRotation() {
        return val(dRotation);
    }

    public void setDRotation(double v) {
        dRotation.getDefaultData().setVal(v);
    }

    public boolean flipX() {
        return flipX;
    }

    public void flipX(boolean v) {
        flipX = v;
    }

    public boolean flipY() {
        return flipY;
    }

    public void flipY(boolean v) {
        flipY = v;
    }

    @Override
    public Class<Transformable> getType() {
        return Transformable.class;
    }

    @Override
    public String getName() {
        return "变换";
    }
}
