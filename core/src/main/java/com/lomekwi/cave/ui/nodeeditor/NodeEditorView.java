package com.lomekwi.cave.ui.nodeeditor;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.ui.Focusable;
import com.lomekwi.cave.ui.widget.PanZoomCanvas;

public class NodeEditorView extends VisTable implements Focusable {
    private final PanZoomCanvas panZoom = new PanZoomCanvas(0.1f, 4f, 1000f);
    private final Group canvas = panZoom.getCanvas();

    public NodeEditorView() {
        setFillParent(true);
        add(panZoom).grow();
        setupListener();
        addTestLabels();
    }

    private void addTestLabels() {
        var table = new VisTable();
        table.setFillParent(true);
        table.add(new VisLabel("节点编辑器")).pad(20).row();
        table.add(new VisLabel("滚轮缩放 / WASD 移动")).row();
        canvas.addActor(table);
    }

    private void setupListener() {
        addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                return false;
            }

            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
                panZoom.zoomAt(event.getStageX(), event.getStageY(), amountY);
                return true;
            }
        });
    }

    private static final Color BG = new Color(0.15f, 0.15f, 0.15f, 1f);
    private static final Color GRID = new Color(0.28f, 0.28f, 0.28f, 1f);
    private static final float GRID_SPACING = 50f;
    private static final float MIN_GRID_PIXEL = 8f;

    @Override
    public void draw(Batch batch, float parentAlpha) {
        var drawer = App.root.getShapeDrawer();
        drawer.filledRectangle(getX(), getY(), getWidth(), getHeight(), BG);
        drawGrid(drawer);
        super.draw(batch, parentAlpha);
    }

    private void drawGrid(space.earlygrey.shapedrawer.ShapeDrawer drawer) {
        float ox = getX() + canvas.getX();
        float oy = getY() + canvas.getY();
        float s = canvas.getScaleX();
        float spacing = GRID_SPACING * s;
        if (spacing < MIN_GRID_PIXEL) return;

        float x0 = getX(), x1 = getX() + getWidth();
        float y0 = getY(), y1 = getY() + getHeight();

        float startX = (float) Math.ceil((x0 - ox) / spacing) * spacing;
        for (float x = ox + startX; x <= x1; x += spacing) {
            drawer.line(x, y0, x, y1, GRID, 1);
        }

        float startY = (float) Math.ceil((y0 - oy) / spacing) * spacing;
        for (float y = oy + startY; y <= y1; y += spacing) {
            drawer.line(x0, y, x1, y, GRID, 1);
        }
    }
}
