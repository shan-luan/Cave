package com.lomekwi.cine;

import com.lomekwi.cine.project.Project;

public class GlobalVars {
    private static Project project;
    public static Project getProject() {
        return project;
    }
    protected static void setProject(Project project) {
        GlobalVars.project = project;
    }
}
