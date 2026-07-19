package com.lomekwi.cave.task;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.Json.Serializable;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;

public class ExportOptionsSet implements Serializable {
    private static final String PREFS_NAME = "cave-export-options";
    private static final String KEY = "options";

    public ArrayList<ExportOptions> presets = new ArrayList<>();
    public int currentIndex;

    public ExportOptionsSet() {
        presets.add(new ExportOptions());
    }

    public ExportOptions current() {
        return presets.get(currentIndex);
    }

    public boolean hasPrev() {
        return currentIndex > 0;
    }

    public boolean hasNext() {
        return currentIndex < presets.size() - 1;
    }

    public void prev() {
        if (hasPrev()) currentIndex--;
    }

    public void next() {
        if (hasNext()) currentIndex++;
    }

    public void save() {
        Preferences prefs = Gdx.app.getPreferences(PREFS_NAME);
        prefs.putString(KEY, new Json().toJson(this));
        prefs.flush();
    }

    public static ExportOptionsSet load() {
        Preferences prefs = Gdx.app.getPreferences(PREFS_NAME);
        String json = prefs.getString(KEY, null);
        if (json == null || json.isEmpty()) {
            return new ExportOptionsSet();
        }
        return new Json().fromJson(ExportOptionsSet.class, json);
    }

    @Override
    public void write(Json json) {
        json.writeValue("presets", presets);
        json.writeValue("currentIndex", currentIndex);
    }

    @Override
    public void read(Json json, JsonValue jsonData) {
        JsonValue arr = jsonData.get("presets");
        presets.clear();
        for (JsonValue e = arr.child; e != null; e = e.next) {
            presets.add(json.readValue(ExportOptions.class, e));
        }
        currentIndex = jsonData.getInt("currentIndex", 0);
        if (presets.isEmpty()) presets.add(new ExportOptions());
        if (currentIndex >= presets.size()) currentIndex = presets.size() - 1;
    }
}
