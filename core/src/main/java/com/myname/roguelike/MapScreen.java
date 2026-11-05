package com.myname.roguelike;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;

import java.util.List;

/**
 * 节点地图界面（文档《节点地图设计》第 5 节）。
 * <p>
 * 八层全部画出来，从下往上：
 * <ul>
 *   <li>已通过的层：整块变暗，写「已通过」</li>
 *   <li>当前层：彩色描边 + 左侧 [1][2] 序号，可按数字键选择</li>
 *   <li>未探索的层：只留暗色轮廓（类型仍然可见，方便玩家规划路线）</li>
 * </ul>
 * <p>
 * 非战斗节点（篝火 / 宝箱）不切屏，直接在中间弹结算框，按空格推进。
 */
public class MapScreen implements Screen {

    private static final float TOP_BAR_H = 76f;
    private static final float BOTTOM_BAR_H = 66f;

    private static final float NODE_W = 140f;
    private static final float NODE_H = 46f;
    private static final float NODE_STEP = 180f;

    private static final Color BG = new Color(0.05f, 0.05f, 0.06f, 1f);
    private static final Color PANEL_BG = new Color(0.09f, 0.10f, 0.12f, 0.94f);
    private static final Color BAR_BG = new Color(0.22f, 0.22f, 0.26f, 1f);
    private static final Color HP_COLOR = new Color(0.85f, 0.24f, 0.24f, 1f);
    private static final Color MP_COLOR = new Color(0.30f, 0.55f, 0.95f, 1f);
    private static final Color DIM = new Color(0.42f, 0.42f, 0.47f, 1f);
    private static final Color GHOST = new Color(0.26f, 0.26f, 0.30f, 1f);
    private static final Color GREEN = new Color(0.45f, 0.85f, 0.45f, 1f);
    private static final Color BLUE = new Color(0.45f, 0.78f, 0.98f, 1f);
    private static final Color GOLD_COLOR = new Color(0.95f, 0.82f, 0.30f, 1f);

    private final Main game;
    private final RunState run;
    private final Ui ui;

    /** 当前弹出的内容类型 */
    private enum Popup { NONE, RESULT, REST, EVENT }

    private Popup popup = Popup.NONE;

    /** RESULT 弹窗的正文（宝箱结算文字） */
    private String resultText = null;

    /** EVENT 弹窗当前展示的事件 */
    private Event currentEvent = null;

    /** 事件弹窗里的一行提示（如「金币不足」），null 表示无提示 */
    private String eventHint = null;

    /** 是否弹出了「放弃本局」确认框（0.2.5） */
    private boolean confirmAbort = false;

    /** 0.3.2 鼠标点选：当前可选节点的命中区域（渲染时填充，鼠标点击时查表） */
    private final java.util.Map<MapNode, com.badlogic.gdx.math.Rectangle> selectableRects =
            new java.util.HashMap<>();

    public MapScreen(Main game, RunState run) {
        this.game = game;
        this.run = run;
        this.ui = new Ui(game.batch, game.font);
    }

    // ==================== 渲染 ====================

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(BG.r, BG.g, BG.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        handleInput();

        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();

        ui.batch().begin();

        drawTopBar(w, h);
        drawBottomBar(w);

        float mapBottom = BOTTOM_BAR_H + 34f;
        float mapTop = h - TOP_BAR_H - 30f;
        drawMap(w, mapBottom, mapTop);

        if (popup == Popup.RESULT) {
            drawResultBox(w, h);
        } else if (popup == Popup.REST) {
            drawRestBox(w, h);
        } else if (popup == Popup.EVENT) {
            drawEventBox(w, h);
        }

        if (confirmAbort) {
            drawAbortConfirm(w, h);
        }

        ui.batch().end();
    }

    /** 顶栏：玩家血蓝 + 章节名 + 进度 */
    private void drawTopBar(float w, float h) {
        float top = h - 14f;
        Player p = run.player;

        ui.rect(0, h - TOP_BAR_H, w, TOP_BAR_H, PANEL_BG);

        // 左：名字 + 两条资源
        // 注意条的 y 必须留在文字底部之下：font.draw 的 y 是文字**顶部**，
        // 一行 20px 文字占 [y-24, y]，条从 y-28 起画就会压在字上。
        ui.text(p.name, 20, top, Color.CYAN, 1.2f);

        ui.text("HP " + p.hp + "/" + p.maxHp, 96, top - 4, HP_COLOR, 1.0f);
        ui.bar(96, top - 40, 150, 10, (float) p.hp / p.maxHp, HP_COLOR, BAR_BG);

        ui.text("MP " + p.mp + "/" + p.maxMp, 270, top - 4, MP_COLOR, 1.0f);
        ui.bar(270, top - 40, 110, 10, (float) p.mp / p.maxMp, MP_COLOR, BAR_BG);

        // 金币：本轮唯一的成长资源，地图上必须能看见它在涨，不然打了半天没反馈
        ui.text("金币 " + run.player.gold, 400, top - 4, GOLD_COLOR, 1.0f);

        // 中：章节名 + 进度
        ui.textCentered(run.currentChapter.name, w / 2f, top - 2, Color.WHITE, 1.3f);
        ui.textCentered("第 " + run.floor + " / " + GameMap.FLOORS + " 层",
                w / 2f, top - 34, DIM, 1.0f);

        // 右：提示
        if (popup == Popup.NONE) {
            String tip = "点击可选节点继续";
            ui.text(tip, w - 20 - ui.measure(tip, 1.0f), top - 20, DIM, 1.0f);
        }
    }

    /** 底栏：操作提示 + 种子（方便复现某张地图） */
    private void drawBottomBar(float w) {
        ui.rect(0, 0, w, BOTTOM_BAR_H, PANEL_BG);

        String hint;
        Color color;
        if (popup == Popup.RESULT) {
            hint = "按 [空格] 继续前进      [ESC] 放弃本局";
            color = GREEN;
        } else if (popup == Popup.REST || popup == Popup.EVENT) {
            hint = "按 [1]/[2] 选择      [ESC] 放弃本局";
            color = GREEN;
        } else {
            int count = run.options().size();
            StringBuilder sb = new StringBuilder("点击");
            for (int i = 0; i < count; i++) {
                sb.append(" 节点[").append(i + 1).append("]");
            }
            sb.append(" 继续前进      [ESC] 放弃本局");
            hint = sb.toString();
            color = Color.WHITE;
        }
        ui.textCentered(hint, w / 2f, 26, color, 1.0f);

        // 种子放在左下角：复现某张地图时要能一眼看到，又不想占版面
        ui.text("seed " + run.seed, 20, 26, DIM, 1.0f);
    }

    // ==================== 地图 ====================

    private void drawMap(float w, float mapBottom, float mapTop) {
        int floors = GameMap.FLOORS;
        float rowGap = (mapTop - mapBottom) / (floors - 1);
        // 窗口矮的时候压缩节点高度，避免框和框叠在一起
        float nodeH = Math.min(NODE_H, rowGap * 0.74f);
        float centerX = w / 2f;

        // 每帧重建可选节点的命中区域（先清空，绘制时再填充）
        selectableRects.clear();

        // 0.3.2 路线规划：画所有节点的 connections 连线（上一层节点底部 -> 下一层节点顶部）
        List<MapNode> selectable = run.options();
        for (int floor = 1; floor < floors; floor++) {
            List<MapNode> upper = run.map.nodesOn(floor);
            for (MapNode node : upper) {
                float x1 = centerX + laneOffset(node, upper.size());
                float y1 = nodeY(floor, mapBottom, rowGap) - nodeH / 2f;   // 节点底部
                for (MapNode next : node.connections) {
                    List<MapNode> lower = run.map.nodesOn(next.floor);
                    float x2 = centerX + laneOffset(next, lower.size());
                    float y2 = nodeY(next.floor, mapBottom, rowGap) + nodeH / 2f;   // 节点顶部
                    // 只有「从当前节点出发」的连线才亮，其余暗色（可规划但当前到不了）
                    boolean active = node == run.currentNode;
                    Color link = active
                            ? new Color(0.85f, 0.80f, 0.45f, 0.95f)
                            : new Color(0.45f, 0.42f, 0.30f, 0.35f);
                    ui.line(x1, y1, x2, y2, active ? 2.2f : 1f, link);
                }
            }
        }

        for (int floor = floors; floor >= 1; floor--) {
            List<MapNode> nodes = run.map.nodesOn(floor);
            float cy = nodeY(floor, mapBottom, rowGap);

            for (MapNode node : nodes) {
                float cx = centerX + laneOffset(node, nodes.size());
                drawNode(node, cx - NODE_W / 2f, cy - nodeH / 2f, nodeH, selectable);
            }
        }
    }

    /** 第 floor 层的中心 y */
    private float nodeY(int floor, float mapBottom, float rowGap) {
        return mapBottom + (floor - 1) * rowGap;
    }

    /** 本层第 index 个节点相对中线的横向偏移 */
    private float laneOffset(MapNode node, int count) {
        return (node.index - (count - 1) / 2f) * NODE_STEP;
    }

    private void drawNode(MapNode node, float x, float y, float h, List<MapNode> selectable) {
        Color accent = typeColor(node.type);
        boolean passed = node.floor < run.floor;
        boolean isCurrentStanding = node == run.currentNode;
        // 0.3.2：只有「当前节点沿连线能去的节点」才可选；其余下一层节点置灰不可点
        boolean selectableNow = selectable.contains(node);

        // 单行文字，垂直居中：文字高 24px，顶部 = 中心 + 12
        float labelY = y + h / 2f + 12f;

        if (isCurrentStanding) {
            // 当前停留节点：彩色描边 + 序号
            ui.rect(x, y, NODE_W, h, new Color(0.14f, 0.15f, 0.18f, 0.98f));
            ui.frame(x, y, NODE_W, h, accent);
            ui.frame(x - 2, y - 2, NODE_W + 4, h + 4, new Color(accent.r, accent.g, accent.b, 0.28f));
            ui.textCentered(node.type.label, x + NODE_W / 2f, labelY, Color.WHITE, 1.0f);
            ui.text("*", x - 30, labelY, Color.YELLOW, 1.0f);   // 标记当前所在
            return;
        }

        if (selectableNow) {
            // 可选的下一层节点：亮色描边 + 数字序号
            int idx = selectable.indexOf(node);
            ui.rect(x, y, NODE_W, h, new Color(0.14f, 0.15f, 0.18f, 0.98f));
            ui.frame(x, y, NODE_W, h, accent);
            ui.frame(x - 2, y - 2, NODE_W + 4, h + 4, new Color(accent.r, accent.g, accent.b, 0.28f));
            ui.text("[" + (idx + 1) + "]", x - 32, labelY, Color.YELLOW, 1.0f);
            ui.textCentered(node.type.label, x + NODE_W / 2f, labelY, Color.WHITE, 1.0f);
            // 0.3.2 鼠标点选：记录命中区域，供 handleInput 点击
            selectableRects.put(node, new com.badlogic.gdx.math.Rectangle(x, y, NODE_W, h));
            return;
        }

        if (passed) {
            // 已通过的层：压暗
            ui.rect(x, y, NODE_W, h, new Color(0.08f, 0.08f, 0.10f, 0.90f));
            ui.frame(x, y, NODE_W, h, dim(accent, 0.35f));
            ui.textCentered(node.type.label, x + NODE_W / 2f, labelY, dim(accent, 0.40f), 1.0f);
            return;
        }

        // 未探索且当前到不了：只留暗色轮廓（类型仍可见，方便规划）
        ui.rect(x, y, NODE_W, h, new Color(0.07f, 0.07f, 0.09f, 0.85f));
        ui.frame(x, y, NODE_W, h, dim(accent, 0.30f));
        ui.textCentered(node.type.label, x + NODE_W / 2f, labelY, dim(accent, 0.38f), 1.0f);
    }

    /** 按比例压暗一个颜色，用来区分同色系的主标题和副标题 */
    private Color dim(Color c, float factor) {
        return new Color(c.r * factor, c.g * factor, c.b * factor, c.a);
    }

    private Color typeColor(MapNode.Type type) {
        return switch (type) {
            case BATTLE -> new Color(0.88f, 0.45f, 0.36f, 1f);
            case ELITE -> new Color(0.70f, 0.52f, 0.95f, 1f);
            case BOSS -> new Color(0.95f, 0.28f, 0.32f, 1f);
            case REST -> new Color(0.45f, 0.82f, 0.52f, 1f);
            case TREASURE -> new Color(0.95f, 0.78f, 0.32f, 1f);
            case EVENT -> new Color(0.55f, 0.70f, 0.92f, 1f);
            case SHOP -> new Color(0.86f, 0.76f, 0.55f, 1f);
        };
    }

    // ==================== 结算框 ====================

    private void drawResultBox(float w, float h) {
        ui.rect(0, 0, w, h, new Color(0.05f, 0.05f, 0.06f, 1f));

        float boxW = 520f;
        float boxH = 260f;
        float bx = w / 2f - boxW / 2f;
        float by = h / 2f - boxH / 2f;

        Color accent = run.pending != null ? typeColor(run.pending.type) : GREEN;
        ui.rect(bx, by, boxW, boxH, PANEL_BG);
        ui.frame(bx, by, boxW, boxH, accent);

        String title = run.pending != null ? run.pending.type.label : "";
        ui.textCentered(title, w / 2f, by + boxH - 20, accent, 1.6f);

        ui.textWrapped(resultText, bx + 28, by + boxH - 64, boxW - 56, Color.WHITE, 1.0f);

        ui.textCentered("按 [空格] 继续", w / 2f, by + 26, GREEN, 1.0f);
    }

    /** 篝火二选一（休息 / 冥想） */
    private void drawRestBox(float w, float h) {
        ui.rect(0, 0, w, h, new Color(0.05f, 0.05f, 0.06f, 1f));

        float boxW = 520f;
        float boxH = 280f;
        float bx = w / 2f - boxW / 2f;
        float by = h / 2f - boxH / 2f;

        ui.rect(bx, by, boxW, boxH, PANEL_BG);
        ui.frame(bx, by, boxW, boxH, typeColor(MapNode.Type.REST));

        ui.textCentered("篝火", w / 2f, by + boxH - 20, typeColor(MapNode.Type.REST), 1.6f);
        ui.textWrapped("火堆噼啪作响，你终于可以喘口气了。", bx + 28, by + boxH - 64, boxW - 56, Color.WHITE, 1.0f);

        ui.text("[1] 休息：恢复 30% 生命", bx + 28, by + boxH - 130, GREEN, 1.0f);
        ui.text("[2] 冥想：+1 点自由属性", bx + 28, by + boxH - 168, BLUE, 1.0f);

        ui.textCentered("按 [1]/[2] 选择", w / 2f, by + 26, GREEN, 1.0f);
    }

    /** 事件二选一 */
    private void drawEventBox(float w, float h) {
        ui.rect(0, 0, w, h, new Color(0.05f, 0.05f, 0.06f, 1f));

        float boxW = 560f;
        float boxH = 320f;
        float bx = w / 2f - boxW / 2f;
        float by = h / 2f - boxH / 2f;

        ui.rect(bx, by, boxW, boxH, PANEL_BG);
        ui.frame(bx, by, boxW, boxH, typeColor(MapNode.Type.EVENT));

        if (currentEvent == null) {
            ui.textCentered("（无事件）", w / 2f, by + boxH - 20, DIM, 1.6f);
            return;
        }

        ui.textCentered(currentEvent.title, w / 2f, by + boxH - 20, typeColor(MapNode.Type.EVENT), 1.6f);
        ui.textWrapped(currentEvent.text, bx + 28, by + boxH - 66, boxW - 56, Color.WHITE, 1.0f);

        ui.text("[1] " + currentEvent.option1Text, bx + 28, by + boxH - 150, GREEN, 1.0f);
        ui.text("[2] " + currentEvent.option2Text, bx + 28, by + boxH - 188, BLUE, 1.0f);

        if (eventHint != null) {
            ui.textCentered(eventHint, w / 2f, by + boxH - 220, new Color(0.98f, 0.62f, 0.30f, 1f), 0.9f);
        }

        ui.textCentered("按 [1]/[2] 选择", w / 2f, by + 26, GREEN, 1.0f);
    }

    // ==================== 输入 ====================

    /** 放弃本局确认框（0.2.5） */
    private void drawAbortConfirm(float w, float h) {
        ui.rect(0, 0, w, h, new Color(0.05f, 0.05f, 0.06f, 1f));

        float boxW = 520f;
        float boxH = 180f;
        float bx = w / 2f - boxW / 2f;
        float by = h / 2f - boxH / 2f;

        ui.rect(bx, by, boxW, boxH, PANEL_BG);
        ui.frame(bx, by, boxW, boxH, new Color(0.98f, 0.62f, 0.30f, 1f));

        ui.textCentered("确定要放弃本次游戏并重新开始吗？", w / 2f, by + boxH - 34, Color.WHITE, 1.3f);
        ui.textCentered("[Y] 确定    [N] 取消", w / 2f, by + boxH - 92, new Color(0.98f, 0.62f, 0.30f, 1f), 1.1f);
    }

    private void handleInput() {
        // 放弃本局确认框：Y 确定重开，N 取消（优先级最高，弹着框时不吃其他按键）
        if (confirmAbort) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.Y)) {
                gotoMenu();
            } else if (Gdx.input.isKeyJustPressed(Input.Keys.N)
                    || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
                confirmAbort = false;
            }
            return;
        }

        // 结果弹窗（宝箱 / 事件 / 篝火结算）：按空格推进前先检查有没有待分配属性点
        if (popup == Popup.RESULT) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
                // 0.2.3 Bug 修复：事件/篝火可能给了属性点，分配完才允许离开。
                // 先推进节点（标记已访问 + 上一层），再跳属性分配界面，
                // 否则分配完回来这个节点还悬着，玩家会再点一次。
                run.advance();
                if (run.pendingAttributePoints > 0) {
                    game.setScreen(new ResultScreen(game, run, true));
                    dispose();
                    return;
                }
                popup = Popup.NONE;
                game.setScreen(new MapScreen(game, run));
                dispose();
            } else if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
                confirmAbort = true;
            }
            return;
        }

        // 篝火选择：1 休息 / 2 冥想
        if (popup == Popup.REST) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) {
                resultText = run.resolveRest(0);
                popup = Popup.RESULT;
            } else if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) {
                resultText = run.resolveRest(1);
                popup = Popup.RESULT;
            } else if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
                confirmAbort = true;
            }
            return;
        }

        // 事件选择：1 / 2
        if (popup == Popup.EVENT) {
            if (currentEvent != null) {
                if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) {
                    if (currentEvent.apply(run, 0)) {
                        if (run.eventCombat) {
                            // 0.2.8 帐篷主人：事件触发了隐藏战斗，直接进战斗
                            game.setScreen(new GameScreen(game, run));
                            dispose();
                            return;
                        }
                        resultText = currentEvent.title + "：你选择了「" + currentEvent.option1Text + "」。";
                        popup = Popup.RESULT;
                    } else {
                        eventHint = "金币不足，无法离开。";
                    }
                } else if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) {
                    if (currentEvent.apply(run, 1)) {
                        if (run.eventCombat) {
                            game.setScreen(new GameScreen(game, run));
                            dispose();
                            return;
                        }
                        resultText = currentEvent.title + "：你选择了「" + currentEvent.option2Text + "」。";
                        popup = Popup.RESULT;
                    }
                } else if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
                    confirmAbort = true;
                }
            }
            return;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            confirmAbort = true;
            return;
        }

        // 0.3.2 鼠标点选：点击可选节点直接进入（优先于键盘，和角色选择/章节选择一致）
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            int mx = Gdx.input.getX();
            int my = Gdx.graphics.getHeight() - Gdx.input.getY();
            for (MapNode node : run.options()) {
                com.badlogic.gdx.math.Rectangle rect = selectableRects.get(node);
                if (rect != null && rect.contains(mx, my)) {
                    enterNode(node);
                    return;
                }
            }
        }

        int[] keyCodes = {Input.Keys.NUM_1, Input.Keys.NUM_2, Input.Keys.NUM_3};
        for (int i = 0; i < keyCodes.length; i++) {
            if (!Gdx.input.isKeyJustPressed(keyCodes[i])) {
                continue;
            }
            // 用数组而不是 NUM_1 + i：枚举常量不保证连续，索引也得自己兜住越界
            if (i >= run.options().size()) {
                return;
            }
            MapNode node = run.choose(i);
            if (node == null) {
                return;
            }
            enterNode(node);
            return;
        }
    }

    /** 0.3.2 进入一个已选中的节点：按类型分发到战斗 / 宝箱 / 篝火 / 事件 / 商店 */
    private void enterNode(MapNode node) {
        if (node.type.combat) {
            game.setScreen(new GameScreen(game, run));
            dispose();
        } else if (node.type == MapNode.Type.TREASURE) {
            // 0.2.8 宝箱怪：10% 概率变成宝箱怪战斗，否则正常开箱
            if (run.rollTreasureMimic()) {
                game.setScreen(new GameScreen(game, run));
                dispose();
            } else {
                resultText = run.resolveTreasure();
                popup = Popup.RESULT;
            }
        } else if (node.type == MapNode.Type.REST) {
            popup = Popup.REST;
        } else if (node.type == MapNode.Type.EVENT) {
            currentEvent = run.rollEvent();
            eventHint = null;
            if (currentEvent == null) {
                // 0.2.8 事件去重：所有事件都遇过了，降级成宝箱奖励
                resultText = run.resolveTreasure();
                popup = Popup.RESULT;
            } else {
                popup = Popup.EVENT;
            }
        } else if (node.type == MapNode.Type.SHOP) {
            // 0.3.0 商店：切到商店界面
            game.setScreen(new ShopScreen(game, run));
            dispose();
        }
    }

    private void gotoMenu() {
        game.setScreen(new StartScreen(game));
        dispose();
    }

    // ==================== Screen 生命周期 ====================

    @Override
    public void show() {
    }

    @Override
    public void resize(int width, int height) {
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void dispose() {
        ui.dispose();
    }
}
