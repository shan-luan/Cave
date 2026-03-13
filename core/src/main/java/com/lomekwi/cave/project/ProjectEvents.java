package com.lomekwi.cave.project;

public class ProjectEvents {
    public static class ProjectLoadedEvent {
        private final Project newProject;

        public ProjectLoadedEvent(Project newProject) {
            this.newProject = newProject;
        }

        public Project getNewProject() {
            return newProject;
        }
    }
    public static class ProjectFrontedEvent{}
}
