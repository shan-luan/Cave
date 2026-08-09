package com.lomekwi.cave.ui.tabs;

import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.actions.TemporalAction;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.Value;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPaneAdapter;

public class AutoHideTabbedPane extends TabbedPane {
    private static final float ANIMATION_DURATION = 0.25f;
    private boolean visible = true;

    public AutoHideTabbedPane() {
        super();
        addListener(new TabbedPaneAdapter() {
            @Override
            public void removedTab(Tab tab) {
                updateVisibility();
            }

            @Override
            public void removedAllTabs() {
                updateVisibility();
            }
        });
    }

    @Override
    public void add(Tab tab) {
        super.add(tab);
        updateVisibility();
    }

    @Override
    public void insert(int index, Tab tab) {
        super.insert(index, tab);
        updateVisibility();
    }

    /** Must be called after the pane's table has been added to its layout. */
    public void refreshVisibility() {
        updateVisibility();
    }

    private void updateVisibility() {
        Table table = getTable();
        Table parent = table.getParent() instanceof Table ? (Table) table.getParent() : null;
        if (parent == null) return;

        Cell<?> cell = parent.getCell(table);
        if (cell == null) return;

        boolean show = getTabs().size > 1;
        if (show == visible) return;
        visible = show;

        table.clearActions();
        if (show) {
            table.setVisible(true);
            animateCellHeight(table, cell, cell.getPrefHeight(), table.getPrefHeight());
        } else {
            animateCellHeight(table, cell, table.getHeight(), 0);
        }
    }

    private void animateCellHeight(final Table table, final Cell<?> cell, final float start, final float end) {
        final boolean show = end != 0;
        final float startAlpha = show ? 0 : table.getColor().a;
        if (show) table.getColor().a = 0;

        table.addAction(new TemporalAction(ANIMATION_DURATION, Interpolation.smooth) {
            @Override
            protected void update(float percent) {
                cell.height(start + (end - start) * percent);
                table.getColor().a = startAlpha + ((show ? 1 : 0) - startAlpha) * percent;
                table.invalidateHierarchy();
            }

            @Override
            protected void end() {
                table.getColor().a = 1;
                if (show) {
                    cell.height(Value.prefHeight);
                } else {
                    cell.height(0);
                    table.setVisible(false);
                }
                table.invalidateHierarchy();
            }
        });
    }
}
