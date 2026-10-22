package faygolover.fltint.client;

import faygolover.fltint.ShadeColors;
import faygolover.fltint.item.BrushItem;
import faygolover.fltint.net.BrushConfigPacket;
import faygolover.fltint.net.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Map;

/** Окно валика: готовые оттенки, свой hex, ластик, режим, радиус, «только такой же блок». Закрытие = сохранить. */
public final class BrushScreen extends Screen {
    private static final int COLS = 8;
    private static final int CELL = 20;
    private static final int PANEL_W = COLS * CELL;

    private final InteractionHand hand;
    private int color;
    private BrushItem.Mode mode;
    private int radius;
    private boolean same;
    private boolean changed;

    private EditBox hexBox;
    private Button modeButton;
    private Button sameButton;
    private Button minus;
    private Button plus;
    private int left;
    private int top;

    private BrushScreen(InteractionHand hand, ItemStack stack) {
        super(Component.translatable("fltint.screen.title"));
        this.hand = hand;
        this.color = BrushItem.color(stack);
        this.mode = BrushItem.mode(stack);
        this.radius = BrushItem.radius(stack);
        this.same = BrushItem.sameBlock(stack);
    }

    public static void open(InteractionHand hand, ItemStack stack) {
        Minecraft.getInstance().setScreen(new BrushScreen(hand, stack));
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = Math.max(8, height / 2 - 92);

        int i = 0;
        for (Map.Entry<String, Integer> e : ShadeColors.PRESETS.entrySet()) {
            int x = left + (i % COLS) * CELL;
            int y = top + 14 + (i / COLS) * CELL;
            addRenderableWidget(new Swatch(x, y, e.getValue(), Component.translatable("fltint.preset." + e.getKey())));
            i++;
        }
        int rows = (ShadeColors.PRESETS.size() + COLS - 1) / COLS;
        int y = top + 14 + rows * CELL + 6;

        hexBox = new EditBox(font, left + 24, y + 1, 56, 18, Component.translatable("fltint.screen.hex"));
        hexBox.setMaxLength(6);
        hexBox.setFilter(s -> s.chars().allMatch(c -> Character.digit(c, 16) >= 0));
        hexBox.setValue(ShadeColors.hex(color));
        hexBox.setResponder(s -> {
            if (s.length() == 6) setColor(Integer.parseInt(s, 16), false);
        });
        addRenderableWidget(hexBox);
        addRenderableWidget(Button.builder(Component.translatable("fltint.screen.eraser"), b -> setColor(ShadeColors.NONE, true))
                .bounds(left + 86, y, PANEL_W - 86, 20)
                .tooltip(Tooltip.create(Component.translatable("fltint.screen.eraser.tip"))).build());

        y += 26;
        modeButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            mode = BrushItem.Mode.values()[(mode.ordinal() + 1) % BrushItem.Mode.values().length];
            changed = true;
            refresh();
        }).bounds(left, y, PANEL_W, 20).build());

        y += 24;
        minus = addRenderableWidget(Button.builder(Component.literal("-"), b -> setRadius(radius - 1)).bounds(left, y, 20, 20).build());
        plus = addRenderableWidget(Button.builder(Component.literal("+"), b -> setRadius(radius + 1)).bounds(left + 52, y, 20, 20).build());
        sameButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            same = !same;
            changed = true;
            refresh();
        }).bounds(left + 78, y, PANEL_W - 78, 20)
                .tooltip(Tooltip.create(Component.translatable("fltint.screen.same.tip"))).build());

        y += 28;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left, y, PANEL_W, 20).build());
        refresh();
    }

    private void setColor(int c, boolean updateBox) {
        c &= 0xFFFFFF;
        if (c != color) changed = true;
        color = c;
        if (updateBox && hexBox != null && !hexBox.getValue().equalsIgnoreCase(ShadeColors.hex(c))) hexBox.setValue(ShadeColors.hex(c));
    }

    private void setRadius(int r) {
        int clamped = Math.max(1, Math.min(BrushItem.MAX_RADIUS, r));
        if (clamped != radius) changed = true;
        radius = clamped;
        refresh();
    }

    private void refresh() {
        modeButton.setMessage(Component.translatable("fltint.screen.mode",
                Component.translatable("fltint.mode." + mode.name().toLowerCase(Locale.ROOT))));
        boolean area = mode != BrushItem.Mode.BLOCK;
        minus.active = area && radius > 1;
        plus.active = area && radius < BrushItem.MAX_RADIUS;
        sameButton.active = area;
        sameButton.setMessage(Component.translatable(same ? "fltint.screen.same.on" : "fltint.screen.same.off"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(left - 6, top - 6, left + PANEL_W + 6, top + 190, 0xC0101010);
        g.drawCenteredString(font, title, width / 2, top, 0xFFFFFF);
        int rows = (ShadeColors.PRESETS.size() + COLS - 1) / COLS;
        int y = top + 14 + rows * CELL + 6;
        g.fill(left, y, left + 20, y + 20, 0xFFFFFFFF);
        g.fill(left + 1, y + 1, left + 19, y + 19, ClientSetup.previewColor(color));
        if (color == ShadeColors.NONE) g.drawCenteredString(font, "×", left + 10, y + 6, 0xFF808080);
        g.drawCenteredString(font, String.valueOf(radius), left + 36, y + 56, mode == BrushItem.Mode.BLOCK ? 0x808080 : 0xFFFFFF);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void removed() {
        if (changed) Net.CHANNEL.sendToServer(new BrushConfigPacket(hand, color, mode, radius, same));
        // Сразу и в локальной копии предмета — цвет ролика и подсказка обновятся до ответа сервера.
        if (changed && minecraft != null && minecraft.player != null) {
            ItemStack stack = minecraft.player.getItemInHand(hand);
            if (stack.getItem() instanceof BrushItem) BrushItem.configure(stack, color, mode, radius, same);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private final class Swatch extends AbstractButton {
        private final int swatchColor;

        Swatch(int x, int y, int swatchColor, Component name) {
            super(x, y, CELL - 2, CELL - 2, name);
            this.swatchColor = swatchColor;
            setTooltip(Tooltip.create(Component.literal("").append(name).append(" #" + ShadeColors.hex(swatchColor))));
        }

        @Override
        public void onPress() {
            setColor(swatchColor, true);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int border = swatchColor == color ? 0xFFFFFFFF : isHoveredOrFocused() ? 0xFFA0A0A0 : 0xFF303030;
            g.fill(getX(), getY(), getX() + width, getY() + height, border);
            g.fill(getX() + 2, getY() + 2, getX() + width - 2, getY() + height - 2, 0xFF000000 | swatchColor);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
