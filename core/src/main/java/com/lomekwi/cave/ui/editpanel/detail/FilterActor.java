package com.lomekwi.cave.ui.editpanel.detail;

import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.widget.VisWindow;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.pipeline.Filter;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.timeline.playback.RefreshRequestEvent;

public abstract class FilterActor extends VisWindow {
    protected Filter<?> filter;
    protected Source<?> source;

    public FilterActor(String title, Filter<?> filter) {
        super(title);
        this.filter = filter;
        align(Align.top | Align.left);
        defaults().left();
        addCloseButton();
    }

    public void setSource(Source<?> source) {
        this.source = source;
    }

    @Override
    public void close() {
        source.getFilters().remove(filter);
        Project p = App.root.getFrontendProject();
        if (p != null) p.projEventBus.post(RefreshRequestEvent.INSTANCE);
        remove();
    }
}
