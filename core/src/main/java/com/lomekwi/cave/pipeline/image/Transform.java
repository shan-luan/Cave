package com.lomekwi.cave.pipeline.image;

import com.badlogic.gdx.math.Matrix4;

public class Transform {
    private final Matrix4 matrix;
    private boolean flipX, flipY;

    public Transform(float x, float y, float rotation) {
        this.matrix = new Matrix4();
        this.matrix.translate(x, y, 0);
        if (rotation != 0) {
            this.matrix.rotate(0, 0, 1, rotation);
        }
    }

    public Transform() {
        this(0, 0, 0);
    }

    public void applyLocal(float dx, float dy, float scaleX, float scaleY, float dRotation,
                           boolean flipX, boolean flipY) {
        if (flipX) this.flipX = !this.flipX;
        if (flipY) this.flipY = !this.flipY;

        Matrix4 local = new Matrix4();
        local.translate(dx, dy, 0);
        if (dRotation != 0) {
            local.rotate(0, 0, 1, dRotation);
        }
        local.scale(scaleX, scaleY, 1);

        matrix.mul(local);
    }

    public void reset(float x, float y) {
        matrix.idt();
        matrix.translate(x, y, 0);
        flipX = false;
        flipY = false;
    }

    public float getX() {
        return matrix.val[Matrix4.M03];
    }

    public float getY() {
        return matrix.val[Matrix4.M13];
    }

    public float getRotation() {
        float a = matrix.val[Matrix4.M00];
        float b = matrix.val[Matrix4.M01];
        float c = matrix.val[Matrix4.M10];
        float d = matrix.val[Matrix4.M11];
        return (float) Math.toDegrees(Math.atan2(c - b, a + d));
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

    public float getRotationRadians() {
        float a = matrix.val[Matrix4.M00];
        float b = matrix.val[Matrix4.M01];
        float c = matrix.val[Matrix4.M10];
        float d = matrix.val[Matrix4.M11];
        return (float) Math.atan2(c - b, a + d);
    }

    public Matrix4 getMatrix() {
        return matrix;
    }

    public boolean isFlipX() {
        return flipX;
    }

    public boolean isFlipY() {
        return flipY;
    }
}
