package com.lomekwi.cave.ui;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
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
import com.lomekwi.cave.ui.editpanel.tlarea.TlGroup;
import com.lomekwi.cave.ui.tabs.ProjectTab;
import com.lomekwi.cave.ui.tabs.TopTabbedPane;
import com.lomekwi.cave.ui.topbar.TopBar;
import com.lomekwi.cave.app.App;

import static com.badlogic.gdx.Input.Keys.*;

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

        VisUI.load(injectChineseFont(VisUI.SkinScale.X2));
        stage = new Stage(new ScreenViewport());
        multiplexer.setProcessors(stage);
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

    private void registerDefaultShortcuts() {
        App.shortcutManager.register(TlGroup.Actions.SCROLL_LEFT, A);
        App.shortcutManager.register(TlGroup.Actions.SCROLL_RIGHT, D);
        App.shortcutManager.register(TlGroup.Actions.SCROLL_UP, W);
        App.shortcutManager.register(TlGroup.Actions.SCROLL_DOWN, S);
        App.shortcutManager.register(TlGroup.Actions.SPLIT, Q);
        App.shortcutManager.register(TlGroup.Actions.DELETE, X);
        App.shortcutManager.register(TlGroup.Actions.UNDO, CONTROL_LEFT, Z);
        App.shortcutManager.register(TlGroup.Actions.REDO, CONTROL_LEFT, SHIFT_LEFT, Z);
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
