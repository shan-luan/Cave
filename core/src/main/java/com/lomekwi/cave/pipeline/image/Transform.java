package com.lomekwi.cave.pipeline.image;

import com.badlogic.gdx.math.Matrix4;

public class Transform {
    public float width, height;
    public float x, y, rotation;
    public boolean flipX, flipY;
    public Matrix4 matrix;

    public Transform(float x, float y, float width, float height, float rotation, float pivotX, float pivotY) {
        this.width = width;
        this.height = height;
        this.matrix = new Matrix4();
        this.matrix.translate(x, y, 0);
        if (rotation != 0) {
            this.matrix.rotate(0, 0, 1, rotation);
        }
        decompose();
    }

    public Transform(float x, float y, float width, float height, float rotation) {
        this(x, y, width, height, rotation, 0, 0);
    }

    public Transform() {
        this(0, 0, 1, 1, 0, 0, 0);
    }

    public void applyLocal(float dx, float dy, float scaleX, float scaleY, float dRotation,
                           float pivotX, float pivotY, boolean flipX, boolean flipY) {
        if (flipX) this.flipX = !this.flipX;
        if (flipY) this.flipY = !this.flipY;

        float currScaleX = getScaleX();
        float currScaleY = getScaleY();
        float currW = width * currScaleX;
        float currH = height * currScaleY;
        float pox = pivotX * currW / 2;
        float poy = pivotY * currH / 2;

        Matrix4 local = new Matrix4();
        local.translate(dx + pox, dy + poy, 0);
        if (dRotation != 0) {
            local.rotate(0, 0, 1, dRotation);
        }
        local.scale(scaleX, scaleY, 1);
        local.translate(-pox, -poy, 0);

        matrix.mul(local);
        decompose();
    }

    public void reset(float x, float y, float w, float h) {
        width = w;
        height = h;
        matrix.idt();
        matrix.translate(x, y, 0);
        flipX = false;
        flipY = false;
        decompose();
    }

    public float getScaleX() {
        float a = matrix.val[Matrix4.M00];
        float c = matrix.val[Matrix4.M10];
        return (float) Math.sqrt(a * a + c * c);
    }

    public float getScaleY() {
        float b = matrix.val[Matrix4.M01];
        float d = matrix.val[Matrix4.M11];
        return (float) Math.sqrt(b * b + d * d);
    }

    private void decompose() {
        x = matrix.val[Matrix4.M03];
        y = matrix.val[Matrix4.M13];
        float a = matrix.val[Matrix4.M00];
        float c = matrix.val[Matrix4.M10];
        rotation = (float) Math.toDegrees(Math.atan2(c, a));
    }
}
