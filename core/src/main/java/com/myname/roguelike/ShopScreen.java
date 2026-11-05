package com.myname.roguelike;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Rectangle;

import java.util.ArrayList;
import java.util.List;

/**
 * 商店界面（0.3.0 经济系统，0.3.1 扩充到 6 槽）。
 * <p>
 * 纯鼠标操作：点击商品购买，金币不足置灰。六个商品槽：
 * <ol>
 *   <li>属性点：15 / 30 / 50（递增）</li>
 *   <li>清除诅咒：50 起，每次 +15</li>
 *   <li>随机药水：10~15（购买时随机定价）</li>
 *   <li>随机战斗技巧：40（未拥有的通用/专属技巧）</li>
 *   <li>生命上限 +5：35</li>
 *   <li>随机药水：10~15（第二槽）</li>
 * </ol>
 * 购买后点「离开」回地图。
 */
public class ShopScreen implements Screen {

    private static final Color BG = new Color(0.05f, 0.05f, 0.06f, 1f);
    private static final Color PANEL_BG = new Color(0.09f, 0.10f, 0.12f, 0.96f);
    private static final Color GOLD = new Color(0.95f, 0.82f, 0.30f, 1f);
    private static final Color GREEN = new Color(0.45f, 0.85f, 0.45f, 1f);
    private static final Color DIM = new Color(0.45f, 0.45f, 0.50f, 1f);
    private static final Color WARN = new Color(0.98f, 0.62f, 0.30f, 1f);
    private static final Color BLUE = new Color(0.45f, 0.78f, 0.98f, 1f);

    private final Main game;
    private final RunState run;
    private final Ui ui;

    /** 商品按钮的命中区域（0.3.1 扩充到 6 个，渲染时填充） */
    private final Rectangle[] buyRects = new Rectangle[6];
    /** 离开按钮命中区域 */
    private Rectangle leaveRect = new Rectangle();

    /** 商品名 + 价格 */
    private final String[] itemNames = new String[6];
    private final int[] itemPrices = new int[6];

    /** 购买反馈提示（如「金币不足」） */
    private String hint = null;

    public ShopScreen(Main game, RunState run) {
        this.game = game;
        this.run = run;
        this.ui = new Ui(game.batch, game.font);
        refreshPrices();
    }

    /** 计算六个商品的当前价格 */
    private void refreshPrices() {
        Player p = run.player;
        // 槽 0：属性点，15/30/50 递增
        int attrPrice = p.attributePurchaseCount == 0 ? 15
                : (p.attributePurchaseCount == 1 ? 30 : 50);
        itemNames[0] = "属性点 +1（" + attrPrice + " 金币）";
        itemPrices[0] = attrPrice;

        // 槽 1：清除诅咒，50 起每次 +15
        int cleansePrice = 50 + p.curseCleanseCount * 15;
        itemNames[1] = "清除一个诅咒（" + cleansePrice + " 金币）";
        itemPrices[1] = cleansePrice;

        // 槽 2：随机药水（10~15）
        int potionPrice1 = 10 + run.rng.nextInt(6);
        itemNames[2] = "随机药水（" + potionPrice1 + " 金币）";
        itemPrices[2] = potionPrice1;

        // 槽 3：随机战斗技巧（40 金币，未拥有的通用/专属技巧；无则置灰）
        CombatSkill skill = run.rollUnownedCombatSkill();
        itemNames[3] = "随机战斗技巧（40 金币）";
        itemPrices[3] = 40;
        // 技巧置灰条件：金币不足或没有可学的技巧，见 buy() 里的判断

        // 槽 4：生命上限 +5（35 金币）
        itemNames[4] = "生命上限 +5（35 金币）";
        itemPrices[4] = 35;

        // 槽 5：随机药水（第二槽，10~15）
        int potionPrice2 = 10 + run.rng.nextInt(6);
        itemNames[5] = "随机药水（" + potionPrice2 + " 金币）";
        itemPrices[5] = potionPrice2;
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(BG.r, BG.g, BG.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();

        ui.batch().begin();

        float boxW = 560f;
        float boxH = 560f;
        float bx = w / 2f - boxW / 2f;
        float by = h / 2f - boxH / 2f;

        ui.rect(bx, by, boxW, boxH, PANEL_BG);
        ui.frame(bx, by, boxW, boxH, GOLD);

        ui.textCentered("商店", w / 2f, by + boxH - 24, GOLD, 1.8f);
        ui.textCentered("金币 " + run.player.gold, w / 2f, by + boxH - 62, GOLD, 1.0f);

        // 六个商品，每个一行 + 一个「购买」按钮
        float y = by + boxH - 104;
        for (int i = 0; i < 6; i++) {
            boolean affordable = run.player.gold >= itemPrices[i];
            // 槽 3（随机战斗技巧）额外要求有可学的技巧，否则置灰
            boolean available = affordable && (i != 3 || run.rollUnownedCombatSkill() != null);
            Color nameColor = available ? Color.WHITE : DIM;
            ui.text(itemNames[i], bx + 28, y, nameColor, 1.0f);

            // 购买按钮（右上角）
            float btnW = 90f;
            float btnH = 34f;
            float btnX = bx + boxW - btnW - 28;
            float btnY = y - btnH;
            buyRects[i] = new Rectangle(btnX, btnY, btnW, btnH);
            Color btnColor = available ? GREEN : DIM;
            ui.rect(btnX, btnY, btnW, btnH, PANEL_BG);
            ui.frame(btnX, btnY, btnW, btnH, btnColor);
            ui.textCentered("购买", btnX + btnW / 2f, btnY + btnH - 6, btnColor, 1.0f);

            y -= 72;
        }

        // 提示行
        if (hint != null) {
            ui.textCentered(hint, w / 2f, by + 44, WARN, 0.9f);
        }

        // 离开按钮
        float lw = 200f;
        float lh = 44f;
        float lx = w / 2f - lw / 2f;
        float ly = by + 8;
        leaveRect = new Rectangle(lx, ly, lw, lh);
        ui.rect(lx, ly, lw, lh, PANEL_BG);
        ui.frame(lx, ly, lw, lh, BLUE);
        ui.textCentered("离开商店", w / 2f, ly + lh - 8, BLUE, 1.1f);

        ui.batch().end();

        handleInput();
    }

    private void handleInput() {
        if (!Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            return;
        }
        int mx = Gdx.input.getX();
        int my = Gdx.graphics.getHeight() - Gdx.input.getY();

        // 离开
        if (leaveRect.contains(mx, my)) {
            leaveShop();
            return;
        }

        // 六个购买按钮
        for (int i = 0; i < 6; i++) {
            if (buyRects[i] != null && buyRects[i].contains(mx, my)) {
                buy(i);
                return;
            }
        }
    }

    private void buy(int slot) {
        Player p = run.player;
        if (p.gold < itemPrices[slot]) {
            hint = "金币不足，无法购买。";
            return;
        }
        p.gold -= itemPrices[slot];
        hint = null;

        switch (slot) {
            case 0 -> {   // 属性点
                p.attributePurchaseCount++;
                run.pendingAttributePoints += 1;
                hint = "获得 1 点自由属性。";
            }
            case 1 -> {   // 清除诅咒
                if (!p.curses.isEmpty()) {
                    p.curses.remove(0);
                    p.curseCleanseCount++;
                    hint = "清除了一个诅咒。";
                } else {
                    // 没有诅咒可清，退款
                    p.gold += itemPrices[slot];
                    hint = "你没有诅咒可清除。";
                }
            }
            case 2, 5 -> {   // 随机药水（两槽共用）
                Item rolled = Item.ALL[run.rng.nextInt(Item.ALL.length)];
                p.items.add(rolled);
                hint = "获得了【" + rolled.name + "】。";
            }
            case 3 -> {   // 随机战斗技巧
                CombatSkill skill = run.rollUnownedCombatSkill();
                if (skill == null) {
                    // 没有可学的技巧，退款
                    p.gold += itemPrices[slot];
                    hint = "没有可学的战斗技巧了。";
                } else {
                    p.combatSkills.add(skill);
                    hint = "学会了【" + skill.name + "】。";
                }
            }
            case 4 -> {   // 生命上限 +5
                p.maxHp += 5;
                p.hp += 5;
                hint = "生命上限 +5（HP " + p.hp + "/" + p.maxHp + "）。";
            }
        }
        refreshPrices();
    }

    private void leaveShop() {
        // 商店节点标记已访问 + 推进层数
        run.advance();
        if (run.pendingAttributePoints > 0) {
            game.setScreen(new ResultScreen(game, run, true));
            dispose();
            return;
        }
        game.setScreen(new MapScreen(game, run));
        dispose();
    }

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
