package com.lomekwi.cave.ui;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.lomekwi.cave.Main;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.ui.tabs.ProjectTab;
import com.lomekwi.cave.ui.tabs.TopTabbedPane;
import com.lomekwi.cave.ui.topbar.TopBar;

public class Root implements ApplicationListener {
    private static Root INSTANCE;
    private Stage stage;
    private Main main;

    private Stack root;
    private VisTable mainLayout;
    private VisTable overlayLayer;
    private VisTable majorArea;

    private TopBar topBar;
    private TopTabbedPane tabbedPane;

    public Root(Main main) {
        this.main = main;
        INSTANCE = this;
    }

    public static Root getInstance() {
        return INSTANCE;
    }

    @Override
    public void create() {
        VisUI.load(injectChineseFont(VisUI.SkinScale.X1));
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);

        // 初始化UI组件
        root = new Stack();
        root.setFillParent(true);

        mainLayout = new VisTable();
        mainLayout.setBackground("window-bg");
        mainLayout.setFillParent(true);

        overlayLayer = new VisTable();
        mainLayout.setFillParent(true);

        // 添加TopBar
        topBar = new TopBar();
        mainLayout.top();
        mainLayout.add(topBar.getTable()).fillX().expandX().row();

        // 添加TabbedPane
        tabbedPane = new TopTabbedPane();
        mainLayout.add(tabbedPane.getTable()).fillX().expandX().row();
        majorArea=new VisTable();
        mainLayout.add(majorArea).fill().expand().row();

        tabbedPane.add(new ProjectTab());

        root.add(mainLayout);
        root.add(overlayLayer);

        stage.addActor(root);
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
        }
    }

    @Override
    public void dispose() {
        if (stage != null) stage.dispose();
        VisUI.dispose();
    }

    public Project getProject() {
        return main.getProject();
    }
    public Stage getStage() {
        return stage;
    }
    public Stack getRoot() {
        return root;
    }
    public VisTable getMainLayout() {
        return mainLayout;
    }
    public VisTable getMajorArea() {
        return majorArea;
    }
    private Skin injectChineseFont(VisUI.SkinScale scale) {
        Skin skin = new Skin(scale.getSkinFile());

        //TODO:释放generator
        FreeTypeFontGenerator generator =
            new FreeTypeFontGenerator(Gdx.files.internal("font/noto.otf"));

        FreeTypeFontGenerator.FreeTypeFontParameter param =
            new FreeTypeFontGenerator.FreeTypeFontParameter();

        param.size = 18;
        param.incremental = true;

        BitmapFont font = generator.generateFont(param);

        skin.add("default-font", font);

        skin.get("default", Label.LabelStyle.class).font = font;
        skin.get("default", TextButton.TextButtonStyle.class).font = font;
        skin.get("default", TextField.TextFieldStyle.class).font = font;
        skin.get("default", CheckBox.CheckBoxStyle.class).font = font;

       return skin;
    }
}
