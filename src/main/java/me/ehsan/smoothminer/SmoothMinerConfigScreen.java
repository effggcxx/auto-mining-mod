package me.ehsan.smoothminer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * In-game settings screen, opened from Mod Menu. Built from vanilla widgets only, so it needs no extra
 * config library. Changes are applied to a working copy and only written when "Done" is pressed.
 */
public final class SmoothMinerConfigScreen extends Screen {
    private static final int GRAY = 0xFFA0A0A0;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555;

    private static final int LABEL_HEIGHT = 12;
    private static final int WIDGET_HEIGHT = 20;
    private static final int LINE_HEIGHT = 10;
    private static final int ROW_GAP = 12;
    private static final int SCROLL_STEP = 18;

    private final Screen parent;
    private final Settings working;

    private final List<Row> rows = new ArrayList<>();
    private EditBox blockBox;
    private Button doneButton;

    private int viewTop;
    private int viewBottom;
    private int contentHeight;
    private int scroll;

    public SmoothMinerConfigScreen(Screen parent) {
        super(Component.literal("SmoothMiner Settings"));
        this.parent = parent;
        this.working = Settings.get().copy();
    }

    private static final class Row {
        final String label;
        final AbstractWidget widget;
        final List<FormattedCharSequence> description;
        final boolean showsBlockStatus;
        int y;
        boolean visible;

        Row(String label, AbstractWidget widget, List<FormattedCharSequence> description, boolean showsBlockStatus) {
            this.label = label;
            this.widget = widget;
            this.description = description;
            this.showsBlockStatus = showsBlockStatus;
        }

        int height() {
            return LABEL_HEIGHT + WIDGET_HEIGHT + 3 + description.size() * LINE_HEIGHT + ROW_GAP;
        }
    }

    @Override
    protected void init() {
        rows.clear();
        viewTop = 32;
        viewBottom = this.height - 36;

        int widgetWidth = Math.min(260, this.width - 40);
        int x = (this.width - widgetWidth) / 2;

        // --- Target block ---
        blockBox = new EditBox(this.font, x, 0, widgetWidth, WIDGET_HEIGHT, Component.literal("Target block"));
        blockBox.setMaxLength(120);
        blockBox.setValue(working.targetBlock);
        blockBox.setResponder(text -> {
            working.targetBlock = text;
            updateDoneButton();
        });
        blockBox.setTooltip(Tooltip.create(Component.literal(
                "Default: " + Settings.DEFAULT_TARGET + "\nThe name next to the text turns red if the ID doesn't exist.")));
        addRow("Target block", blockBox, widgetWidth,
                "The one block type to mine, written as its ID, e.g. minecraft:iron_ore. "
                        + "Deepslate ores are separate blocks (minecraft:deepslate_iron_ore). "
                        + "Hold F3+H in-game to see IDs in item tooltips.", true);

        // --- Search radius ---
        AbstractWidget radius = new StepSlider(x, widgetWidth, Settings.RADIUS_MIN, Settings.RADIUS_MAX, 1,
                working.searchRadius,
                v -> Component.literal("Search radius: " + v + (v == 1 ? " block" : " blocks")),
                v -> working.searchRadius = v);
        radius.setTooltip(Tooltip.create(Component.literal("Default: 4\nRange: "
                + Settings.RADIUS_MIN + " to " + Settings.RADIUS_MAX + " blocks")));
        addRow("Search radius", radius, widgetWidth,
                "How far around you to look for the target, measured as a cube centred on you. "
                        + "Normal reach is only about 4.5 blocks, so larger values make it turn towards ore "
                        + "it can't mine yet.", false);

        // --- Max turn speed ---
        AbstractWidget speed = new StepSlider(x, widgetWidth, Settings.SPEED_MIN, Settings.SPEED_MAX,
                Settings.SPEED_STEP, Math.round(working.maxTurnSpeed),
                v -> Component.literal("Max turn speed: " + v + "°/s"),
                v -> working.maxTurnSpeed = v);
        speed.setTooltip(Tooltip.create(Component.literal("Default: 60°/s\nRange: "
                + Settings.SPEED_MIN + " to " + Settings.SPEED_MAX + "°/s")));
        addRow("Max turn speed", speed, widgetWidth,
                "The fastest the camera is allowed to rotate while turning towards ore, in degrees per second. "
                        + "Lower is calmer: a half turn (180°) at 60°/s takes about 3 seconds.", false);

        // --- Smoothness ---
        AbstractWidget smooth = new StepSlider(x, widgetWidth, Settings.SMOOTH_MIN, Settings.SMOOTH_MAX, 1,
                working.smoothness,
                v -> Component.literal("Smoothness: " + v + " / " + Settings.SMOOTH_MAX),
                v -> working.smoothness = v);
        smooth.setTooltip(Tooltip.create(Component.literal("Default: 7\nRange: "
                + Settings.SMOOTH_MIN + " to " + Settings.SMOOTH_MAX)));
        addRow("Smoothness", smooth, widgetWidth,
                "How gently the camera speeds up and slows down. 1 is quick and direct, "
                        + "10 is a long, soft ease. Raise it if turning still looks jerky.", false);

        // --- Footer buttons ---
        int total = Math.min(310, this.width - 20);
        int gap = 6;
        int buttonWidth = (total - gap * 2) / 3;
        int bx = (this.width - total) / 2;
        int by = this.height - 28;

        doneButton = addRenderableWidget(Button.builder(Component.literal("Done"), b -> saveAndClose())
                .bounds(bx, by, buttonWidth, WIDGET_HEIGHT)
                .tooltip(Tooltip.create(Component.literal("Save the changes and go back.")))
                .build());
        addRenderableWidget(Button.builder(Component.literal("Reset to defaults"), b -> {
                    working.copyFrom(new Settings());
                    scroll = 0;
                    clearWidgets();
                    init();
                })
                .bounds(bx + buttonWidth + gap, by, buttonWidth, WIDGET_HEIGHT)
                .tooltip(Tooltip.create(Component.literal("Put every option back to its default. "
                        + "Nothing is saved until you press Done.")))
                .build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(bx + (buttonWidth + gap) * 2, by, buttonWidth, WIDGET_HEIGHT)
                .tooltip(Tooltip.create(Component.literal("Discard the changes and go back.")))
                .build());

        updateDoneButton();
        layoutRows();
    }

    private void addRow(String label, AbstractWidget widget, int widgetWidth, String description, boolean blockStatus) {
        addRenderableWidget(widget);
        List<FormattedCharSequence> lines = this.font.split(Component.literal(description), widgetWidth);
        rows.add(new Row(label, widget, lines, blockStatus));
    }

    private void layoutRows() {
        int y = viewTop + 6 - scroll;
        int total = 6;
        int xCenter = this.width / 2;
        for (Row row : rows) {
            row.y = y;
            int widgetY = y + LABEL_HEIGHT;
            row.widget.setX(xCenter - row.widget.getWidth() / 2);
            row.widget.setY(widgetY);
            // Rows are shown or hidden whole, so nothing ever draws over the header or footer.
            row.visible = y >= viewTop && y + row.height() - ROW_GAP <= viewBottom;
            row.widget.visible = row.visible;
            row.widget.active = row.visible;
            y += row.height();
            total += row.height();
        }
        contentHeight = total;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - (viewBottom - viewTop));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll() > 0) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY) * SCROLL_STEP));
            layoutRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean blockIsValid() {
        return Settings.resolveBlock(working.targetBlock) != null;
    }

    private void updateDoneButton() {
        if (doneButton != null) doneButton.active = blockIsValid();
    }

    private void saveAndClose() {
        if (!blockIsValid()) return;
        Settings live = Settings.get();
        live.copyFrom(working);
        live.sanitize();
        live.save();
        SmoothMinerClient.applySettings();
        onClose();
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Header and footer bands go first so the footer buttons are drawn on top of them.
        graphics.fill(0, 0, this.width, viewTop, 0xC0000000);
        graphics.fill(0, viewBottom, this.width, this.height, 0xC0000000);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.centeredText(this.font, this.title, this.width / 2, 12, WHITE);

        for (Row row : rows) {
            if (!row.visible) continue;
            int left = row.widget.getX();
            graphics.text(this.font, row.label, left, row.y + 2, WHITE);

            if (row.showsBlockStatus) {
                Component status;
                int color;
                Block block = Settings.resolveBlock(working.targetBlock);
                if (block != null) {
                    status = block.getName();
                    color = GREEN;
                } else {
                    status = Component.literal(Identifier.tryParse(working.targetBlock.trim()) == null
                            ? "Not a valid ID" : "No such block");
                    color = RED;
                }
                int statusX = left + row.widget.getWidth() - this.font.width(status);
                graphics.text(this.font, status, statusX, row.y + 2, color);
            }

            int textY = row.y + LABEL_HEIGHT + WIDGET_HEIGHT + 3;
            for (FormattedCharSequence line : row.description) {
                graphics.text(this.font, line, left, textY, GRAY);
                textY += LINE_HEIGHT;
            }
        }

        if (maxScroll() > 0) {
            graphics.centeredText(this.font, Component.literal("Scroll for more"),
                    this.width / 2, viewBottom + 4, GRAY);
        }
    }

    /** Integer slider that snaps to {@code step} and shows a custom label. */
    private static final class StepSlider extends AbstractSliderButton {
        private final int min;
        private final int max;
        private final int step;
        private final IntFunction<Component> label;
        private final Consumer<Integer> onChange;

        StepSlider(int x, int width, int min, int max, int step, int initial,
                   IntFunction<Component> label, Consumer<Integer> onChange) {
            super(x, 0, width, WIDGET_HEIGHT, Component.empty(), toNormalized(initial, min, max));
            this.min = min;
            this.max = max;
            this.step = step;
            this.label = label;
            this.onChange = onChange;
            updateMessage();
        }

        private static double toNormalized(int v, int min, int max) {
            return (Math.max(min, Math.min(max, v)) - min) / (double) (max - min);
        }

        private int currentValue() {
            double raw = min + value * (max - min);
            int snapped = min + (int) Math.round((raw - min) / step) * step;
            return Math.max(min, Math.min(max, snapped));
        }

        @Override
        protected void updateMessage() {
            setMessage(label.apply(currentValue()));
        }

        @Override
        protected void applyValue() {
            int v = currentValue();
            this.value = toNormalized(v, min, max);
            onChange.accept(v);
        }
    }
}
