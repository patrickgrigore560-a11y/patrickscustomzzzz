package ro.patrickszcustoms.gui;

import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import ro.patrickszcustoms.config.AimEntities;
import ro.patrickszcustoms.config.ConfigManager;
import ro.patrickszcustoms.config.ModConfig;
import ro.patrickszcustoms.i18n.Tr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pick which entity types Aim Assist may target: every registered entity type,
 * live search (localized name or id), scrolling list, bulk actions on the shown results.
 */
public class EntityPickerScreen extends Screen {
    private static final int ROW_H = 16;

    private final Screen parent;
    private final List<EntityType<?>> all = new ArrayList<>();
    private final Map<EntityType<?>, String> ids = new HashMap<>();
    private final Map<EntityType<?>, String> names = new HashMap<>();
    private final List<FancyButton> rowButtons = new ArrayList<>();
    private List<EntityType<?>> filtered = new ArrayList<>();

    private TextFieldWidget search;
    private String query = "";
    private int scroll;
    private int cx0, cy0, cw, ch, listX, listY, listW, rows, searchY;

    public EntityPickerScreen(Screen parent) {
        super(Tr.t("patrickszcustoms.picker.title"));
        this.parent = parent;
        AimEntities.ensureDefaults(ConfigManager.get());
        for (EntityType<?> t : Registries.ENTITY_TYPE) {
            all.add(t);
            ids.put(t, AimEntities.id(t));
            names.put(t, t.getName().getString());
        }
        all.sort((a, b) -> names.get(a).compareToIgnoreCase(names.get(b)));
    }

    // ================================================================= layout
    @Override
    protected void init() {
        cw = Math.min(width - 16, 400);
        ch = Math.min(height - 16, 250);
        cx0 = (width - cw) / 2;
        cy0 = (height - ch) / 2;
        int pad = 10;
        listX = cx0 + pad;
        listW = cw - pad * 2 - 24;
        searchY = cy0 + 28;

        if (search == null) {
            search = new TextFieldWidget(textRenderer, 0, 0, 10, 16, Text.empty());
            search.setMaxLength(40);
            search.setChangedListener(s -> {
                query = s.toLowerCase(Locale.ROOT).trim();
                scroll = 0;
                applyFilter();
            });
        }
        search.setX(listX);
        search.setY(searchY);
        search.setWidth(listW);
        search.setHeight(16);
        addDrawableChild(search);
        setFocused(search);

        listY = searchY + 22;
        int bottomY = cy0 + ch - 30;
        rows = Math.max(3, (bottomY - listY - 2) / ROW_H);

        rowButtons.clear();
        for (int i = 0; i < rows; i++) {
            int idx = i;
            FancyButton b = new FancyButton(listX, listY + i * ROW_H, listW, ROW_H - 1, Text.empty(),
                    btn -> toggleRow(idx)).leftAlign();
            rowButtons.add(b);
            addDrawableChild(b);
        }
        addDrawableChild(new FancyButton(listX + listW + 4, listY, 18, 16, Text.literal("^"), b -> scrollBy(-3)));
        addDrawableChild(new FancyButton(listX + listW + 4, listY + rows * ROW_H - 16, 18, 16,
                Text.literal("v"), b -> scrollBy(3)));

        // bottom bulk-action row
        int gap = 4, bw = (cw - pad * 2 - gap * 3) / 4, by = cy0 + ch - 26, bx = listX;
        ModConfig c = ConfigManager.get();
        addDrawableChild(new FancyButton(bx, by, bw, 20, Tr.t("patrickszcustoms.picker.select_shown"), b -> {
            for (EntityType<?> t : filtered) AimEntities.add(c, ids.get(t));
            refreshRows();
        }));
        addDrawableChild(new FancyButton(bx + (bw + gap), by, bw, 20, Tr.t("patrickszcustoms.picker.clear_shown"), b -> {
            for (EntityType<?> t : filtered) AimEntities.remove(c, ids.get(t));
            refreshRows();
        }));
        addDrawableChild(new FancyButton(bx + 2 * (bw + gap), by, bw, 20, Tr.t("patrickszcustoms.picker.hostile"), b -> {
            AimEntities.setHostileOnly(c);
            refreshRows();
        }));
        addDrawableChild(new FancyButton(bx + 3 * (bw + gap), by, bw, 20, Tr.t("patrickszcustoms.done"),
                b -> close()).pitch(0.8f));

        applyFilter();
    }

    // ================================================================== logic
    private void applyFilter() {
        filtered = new ArrayList<>();
        for (EntityType<?> t : all) {
            if (query.isEmpty()
                    || names.get(t).toLowerCase(Locale.ROOT).contains(query)
                    || ids.get(t).contains(query)) {
                filtered.add(t);
            }
        }
        scroll = MathHelper.clamp(scroll, 0, Math.max(0, filtered.size() - rows));
        refreshRows();
    }

    private void refreshRows() {
        ModConfig c = ConfigManager.get();
        for (int i = 0; i < rowButtons.size(); i++) {
            FancyButton b = rowButtons.get(i);
            int idx = scroll + i;
            if (idx < filtered.size()) {
                EntityType<?> t = filtered.get(idx);
                boolean on = AimEntities.has(c, ids.get(t));
                b.visible = true;
                b.active = true;
                b.selected(on);
                b.setMessage(Text.literal(on ? "[x] " : "[ ] ")
                        .append(Text.literal(names.get(t)))
                        .append(Text.literal("  " + ids.get(t)).formatted(Formatting.GRAY)));
            } else {
                b.visible = false;
                b.active = false;
            }
        }
    }

    private void toggleRow(int row) {
        int idx = scroll + row;
        if (idx >= filtered.size()) return;
        AimEntities.toggle(ConfigManager.get(), ids.get(filtered.get(idx)));
        refreshRows();
    }

    private void scrollBy(int rowsDelta) {
        scroll = MathHelper.clamp(scroll + rowsDelta, 0, Math.max(0, filtered.size() - rows));
        refreshRows();
    }

    /** Mouse wheel (no @Override on purpose: the ^ / v buttons work even if this signature differs). */
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollBy(verticalAmount > 0 ? -2 : 2);
        return true;
    }

    // ================================================================= render
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int acc = Ui.accent() | 0xFF000000;
        Ui.drawBackground(ctx, width, height);
        Ui.rect(ctx, cx0, cy0, cw, ch, 0xD40A0A12);
        ctx.drawStrokedRectangle(cx0, cy0, cw, ch, Ui.alpha(acc, 0.7f));
        ctx.fill(cx0 + 1, cy0 + 1, cx0 + cw - 1, cy0 + 3, Ui.alpha(acc, 0.85f));

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(cx0 + 10, cy0 + 8);
        ctx.getMatrices().scale(1.25f, 1.25f);
        ctx.drawTextWithShadow(textRenderer, title, 0, 0, acc);
        ctx.getMatrices().popMatrix();

        ModConfig c = ConfigManager.get();
        Text counts = Tr.t("patrickszcustoms.picker.selected", c.aimEntities.size())
                .append(Text.literal("  |  "))
                .append(Tr.t("patrickszcustoms.picker.shown", filtered.size()));
        ctx.drawTextWithShadow(textRenderer, counts, cx0 + cw - 10 - textRenderer.getWidth(counts), cy0 + 12, 0xFF9AA0B8);

        for (Element e : children()) {
            if (e instanceof Drawable d) d.render(ctx, mouseX, mouseY, delta);
        }

        if (query.isEmpty() && !search.isFocused()) {
            ctx.drawTextWithShadow(textRenderer, Tr.t("patrickszcustoms.picker.search"), listX + 4, searchY + 4, 0xFF777777);
        }
        if (filtered.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Tr.t("patrickszcustoms.picker.empty"),
                    listX + listW / 2, listY + rows * ROW_H / 2 - 4, 0xFFB8BCD0);
        }

        // scrollbar between the two arrow buttons
        int trackX = listX + listW + 12, trackY0 = listY + 18, trackY1 = listY + rows * ROW_H - 18;
        if (trackY1 > trackY0) {
            ctx.fill(trackX, trackY0, trackX + 2, trackY1, Ui.alpha(acc, 0.2f));
            int total = Math.max(1, filtered.size());
            if (total > rows) {
                int th = Math.max(8, (trackY1 - trackY0) * rows / total);
                int ty = trackY0 + (trackY1 - trackY0 - th) * scroll / Math.max(1, total - rows);
                ctx.fill(trackX, ty, trackX + 2, ty + th, acc);
            }
        }
    }

    // ------------------------------------------------------------------ close
    @Override
    public void removed() { ConfigManager.save(); }

    @Override
    public void close() { client.setScreen(parent); }
}
