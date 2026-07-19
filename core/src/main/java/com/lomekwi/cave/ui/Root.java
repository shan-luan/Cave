package com.lomekwi.cave.ui;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.util.ToastManager;
import com.kotcrab.vis.ui.widget.LinkLabel;
import com.kotcrab.vis.ui.widget.Menu;
import com.kotcrab.vis.ui.widget.MenuItem;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisTextField;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane;
import com.lomekwi.cave.Main;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.ui.editpanel.EditPanel;
import com.lomekwi.cave.ui.editpanel.tlarea.TlGroup;
import com.lomekwi.cave.ui.tabs.ProjectTab;
import com.lomekwi.cave.ui.tabs.TopTabbedPane;
import com.lomekwi.cave.ui.topbar.TopBar;
import com.lomekwi.cave.app.App;

import space.earlygrey.shapedrawer.ShapeDrawer;

public class Root implements ApplicationListener {
    private final Main main;

    private Stage stage;

    private ToastManager toastManager;

    private VisTable mainLayout;
    private VisTable majorArea;

    private TopBar topBar;
    private TopTabbedPane tabbedPane;

    private DragAndDrop dragAndDrop;

    private ShapeDrawer shapeDrawer;

    private FreeTypeFontGenerator generator;


    public Root(Main main) {
        this.main = main;
        App.root = this;
    }


    @Override
    public void create() {
        dragAndDrop=new DragAndDrop();

        InputMultiplexer multiplexer = new InputMultiplexer();

        multiplexer.addProcessor(0, new InputProcessor() {
            @Override
            public boolean keyDown(int keycode) {
                // TopActions that don't need a project
                if (App.shortcutManager.isActive(TopBar.TopActions.NEW)) {
                    topBar.performNew();
                    return true;
                }
                if (App.shortcutManager.isActive(TopBar.TopActions.OPEN)) {
                    topBar.performOpen();
                    return true;
                }
                if (App.shortcutManager.isActive(TopBar.TopActions.CLOSE)) {
                    topBar.performClose();
                    return true;
                }

                Project project = getFrontendProject();
                if (project == null || project.undoManager == null) return false;
                EditPanel ep = getFrontendEditPanel();
                // TopActions that need a project
                if (App.shortcutManager.isActive(TopBar.TopActions.SAVE)) {
                    topBar.performSave();
                    return true;
                }
                if (App.shortcutManager.isActive(TopBar.TopActions.SAVE_AS)) {
                    topBar.performSaveAs();
                    return true;
                }

                if (isTextInputFocused()) return false;

                // Undo / Redo
                if (App.shortcutManager.isActive(TlGroup.Actions.UNDO)) {
                    project.undoManager.undo();
                    if (ep != null) ep.getTlGroup().markTimelineDirty();
                    return true;
                }
                if (App.shortcutManager.isActive(TlGroup.Actions.REDO)) {
                    project.undoManager.redo();
                    if (ep != null) ep.getTlGroup().markTimelineDirty();
                    return true;
                }

                // TlGroup actions
                TlGroup tlGroup = (ep != null) ? ep.getTlGroup() : null;
                if (tlGroup != null) {
                    if (App.shortcutManager.isActive(TlGroup.Actions.PLAY_PAUSE)) {
                        project.playhead.setPlaying(!project.playhead.isPlaying());
                        return true;
                    }
                    if (App.shortcutManager.isActive(TlGroup.Actions.SPLIT)) {
                        tlGroup.performSplit();
                        return true;
                    }
                    if (App.shortcutManager.isActive(TlGroup.Actions.DELETE)) {
                        tlGroup.performDelete();
                        return true;
                    }
                    if (App.shortcutManager.isActive(TlGroup.Actions.GROUP)) {
                        tlGroup.performGroup();
                        return true;
                    }
                }

                return false;
            }

            @Override
            public boolean keyUp(int keycode) { return false; }

            @Override
            public boolean keyTyped(char character) { return false; }

            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) { return false; }

            @Override
            public boolean touchUp(int screenX, int screenY, int pointer, int button) { return false; }

            @Override
            public boolean touchDragged(int screenX, int screenY, int pointer) { return false; }

            @Override
            public boolean mouseMoved(int screenX, int screenY) { return false; }

            @Override
            public boolean scrolled(float amountX, float amountY) { return false; }

            @Override
            public boolean touchCancelled(int screenX, int screenY, int pointer, int button) { return false; }
        });

        VisUI.load(injectChineseFont(VisUI.SkinScale.X2));
        stage = new Stage(new ScreenViewport());
        stage.addCaptureListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (button != Input.Buttons.LEFT) return false;
                Actor target = event.getTarget();
                while (target != null) {
                    if (target instanceof Focusable) {
                        Actor focusTarget = target;
                        Gdx.app.postRunnable(() -> {
                            stage.setKeyboardFocus(focusTarget);
                            stage.setScrollFocus(focusTarget);
                        });
                        return false;
                    }
                    target = target.getParent();
                }
                return false;
            }
        });
        multiplexer.addProcessor(stage);
        Gdx.input.setInputProcessor(multiplexer);

        toastManager=new ToastManager(stage);

        Pixmap white = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        white.setColor(Color.WHITE);
        white.fill();
        TextureRegion region = new TextureRegion(new Texture(white));
        this.shapeDrawer = new ShapeDrawer(stage.getBatch(), region);
        white.dispose();

        // 初始化UI组件
        mainLayout = new VisTable();
        mainLayout.setBackground("window-bg");
        mainLayout.setFillParent(true);

        topBar = new TopBar();
        mainLayout.top();
        mainLayout.add(topBar.getTable()).fillX().top().row();

        tabbedPane = new TopTabbedPane();
        mainLayout.add(tabbedPane.getTable()).fillX().top().row();
        majorArea=new VisTable();
        mainLayout.add(majorArea).fill().expand().row();

        stage.addActor(mainLayout);

        registerDefaultShortcuts();

        toastManager.toFront();

        stage.setDebugAll(false);
    }

    @Override
    public void render() {
        ScreenUtils.clear(Color.BLACK);
        stage.act(Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f));
        stage.draw();
    }

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public void resize(int width, int height) {
        if (stage != null) {
            stage.getViewport().update(width, height, true);
            toastManager.resize();
        }
    }

    @Override
    public void dispose() {
        if (stage != null) stage.dispose();
        VisUI.dispose();
        generator.dispose();
    }
    public Stage getStage() {
        return stage;
    }

    public boolean isTextInputFocused() {
        if (stage == null) return false;
        var focus = stage.getKeyboardFocus();
        return focus instanceof TextField || focus instanceof VisTextField;
    }

    public VisTable getMainLayout() {
        return mainLayout;
    }
    public VisTable getMajorArea() {
        return majorArea;
    }
    private Skin injectChineseFont(VisUI.SkinScale scale) {
        Skin skin = new Skin(scale.getSkinFile());

        generator = new FreeTypeFontGenerator(Gdx.files.internal("font/noto.otf"));

        FreeTypeFontGenerator.FreeTypeFontParameter param =
            new FreeTypeFontGenerator.FreeTypeFontParameter();

        param.size = 22;
        param.incremental = true;

        BitmapFont font = generator.generateFont(param);

        skin.add("default-font", font);

        skin.get("default", Label.LabelStyle.class).font = font;
        skin.get("default", TextButton.TextButtonStyle.class).font = font;
        skin.get("default", TextField.TextFieldStyle.class).font = font;
        skin.get("default", CheckBox.CheckBoxStyle.class).font = font;
        skin.get("default", MenuItem.MenuItemStyle.class).font = font;
        skin.get("default", Menu.MenuStyle.class).openButtonStyle.font = font;
        skin.get("default", TabbedPane.TabbedPaneStyle.class).buttonStyle.font = font;
        skin.get("default", Window.WindowStyle.class).titleFont = font;
        skin.get("default", LinkLabel.LinkLabelStyle.class).font = font;
        skin.get("default", VisTextButton.VisTextButtonStyle.class).font = font;
        skin.get("default", VisTextField.VisTextFieldStyle.class).font = font;
       return skin;
    }

    public Project getFrontendProject() {
        if (tabbedPane.getActiveTab() instanceof ProjectTab) {
            return ((ProjectTab) tabbedPane.getActiveTab()).getProject();
        }else{
            return null;
        }
    }

    public EditPanel getFrontendEditPanel() {
        if (tabbedPane.getActiveTab() instanceof ProjectTab) {
            return ((ProjectTab) tabbedPane.getActiveTab()).getEditPanel();
        }else{
            return null;
        }
    }

    private void registerDefaultShortcuts() {
        for (TlGroup.Actions action : TlGroup.Actions.values()) {
            App.shortcutManager.register(action, action.defaultKeys());
        }
        for (TopBar.TopActions action : TopBar.TopActions.values()) {
            App.shortcutManager.register(action, action.defaultKeys());
        }
        App.shortcutManager.load();
        topBar.applyCustomShortcuts();
    }

    public DragAndDrop getDragAndDrop() {
        return dragAndDrop;
    }

    public ShapeDrawer getShapeDrawer() {
        return shapeDrawer;
    }

    public ToastManager getToastManager() {
        return toastManager;
    }
}
