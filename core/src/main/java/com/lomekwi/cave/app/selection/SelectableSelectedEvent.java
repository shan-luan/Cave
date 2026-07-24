package com.lomekwi.cave.app.selection;

import org.jspecify.annotations.Nullable;

public interface SelectableSelectedEvent<T extends Selectable> {
    @Nullable T selectable();
    int selectedCount();
}