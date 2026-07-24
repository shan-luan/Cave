package com.lomekwi.cave.app.copy;

import com.google.common.eventbus.Subscribe;
import com.lomekwi.cave.app.selection.SelectableSelectedEvent;
import org.jspecify.annotations.Nullable;

public class CopyManager {
    private @Nullable Copyable clipboard;
    private @Nullable Copyable latestCopyable;

    @Subscribe
    public void onSelection(SelectableSelectedEvent<?> e) {
        var sel = e.selectable();
        if (sel instanceof Copyable c) {
            latestCopyable = c;
        }
    }

    public void copy() {
        if (latestCopyable != null) {
            clipboard = latestCopyable.copy();
        }
    }

    public void copy(Copyable copyable) {
        clipboard = copyable;
    }

    public @Nullable Copyable getClipboard() {
        return clipboard;
    }

    public void clearClipboard() {
        clipboard = null;
    }

    public void refreshClipboard() {
        if (clipboard != null) {
            clipboard = clipboard.copy();
        }
    }
}
