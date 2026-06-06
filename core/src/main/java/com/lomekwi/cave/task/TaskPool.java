package com.lomekwi.cave.task;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.badlogic.gdx.Gdx;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.ui.Root;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class TaskPool implements Iterable<Task>{
    private final Set<Task> tasks = new HashSet<>();
    public void submit(Task task) {
        tasks.add(task);
        App.workerExecutor.submit(() -> {
            try(task) {
                task.run();
            }catch (Exception e){
                // FIXME:ignore
            }
            tasks.remove(task);
            Gdx.app.postRunnable(()-> Root.getInstance().getToastManager().show(i18n("任务")+task.getName()+i18n("已完成")));
        });
    }

    @Override
    public Iterator<Task> iterator() {
        return tasks.iterator();
    }
}
