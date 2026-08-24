package com.lomekwi.cave.pipeline;

import com.lomekwi.cave.ui.editpanel.inspector.ModifierActor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
@Deprecated(forRemoval = true)//TODO:将被Filter替代。
public abstract class Modifier<T> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private final Source<?> source;
    private transient ModifierActor actor;

    public Modifier(Source<?> source) {
        this.source = source;
    }

    public Source<?> getSource() {
        return source;
    }

    public abstract <F extends T> F modify(F frame, long time);

    public abstract String getName();

    /** 暴露所有可显示/可修改的条目，UI 据此自动生成 widget。 */
    public List<Param<?>> getParams() {
        return List.of();
    }

    public ModifierActor getActor() {
        if (actor == null) {
            actor = new ModifierActor(source, this);
        }
        return actor;
    }

    public void invalidateDetailActor() {
        actor = null;
    }
    public interface Val extends Serializable{
        void set(double v);
        double get();
        default float getFloat(){
            return (float) get();
        };
    }
    public static class FixVal implements Val{
        private double v;

        @Override
        public void set(double v) {
            this.v=v;
        }

        @Override
        public double get() {
            return v;
        }
    }
}
