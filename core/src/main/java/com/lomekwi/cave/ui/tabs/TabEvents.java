package com.lomekwi.cave.ui.tabs;

@SuppressWarnings("InstantiationOfUtilityClass")
public class TabEvents {
    public static class TabSwitchedEvent{
        public static final TabSwitchedEvent INSTANCE=new TabSwitchedEvent();
        private TabSwitchedEvent(){}
    }
    public static class SettingsOpenedEvent{
        public static final SettingsOpenedEvent INSTANCE=new SettingsOpenedEvent();
        private SettingsOpenedEvent(){};
    }
}
