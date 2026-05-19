package com.lomekwi.cave.project;

public class ProjectLoadedEvent {
    private final Project newProject;

    public ProjectLoadedEvent(Project newProject) {
        this.newProject = newProject;
    }

    public Project getNewProject() {
        return newProject;
    }
}
