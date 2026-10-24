package dev.t800.hud;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/** 3D model of your own skin + armor and protection analysis. */
public class ArmorScreen extends Screen {
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    private static final String[] SLOT_NAMES = {"HEAD", "CHEST", "LEGS", "FEET", "MAIN", "OFF"};

    public ArmorScreen() { super(Component.literal("T-800 // ARMOR ANALYSIS")); }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(Component.literal("CLOSE"), b -> this.onClose())
                .bounds(this.width / 2 - 40, this.height - 24, 80, 18).build());
    }

    private static double armorRed(double armor, double tough, double dmg) {
        return Mth.clamp(armor - dmg / (2.0 + tough / 4.0), armor * 0.2, 20.0) / 25.0;
    }

    private static double enchRed(int epf) { return Math.min(20, epf) * 0.04; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        super.render(g, mouseX, mouseY, pt);
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;

        int main = Config.main(), dim = Config.dim(), txt = Config.text();
        int totalW = Math.min(440, this.width - 16);
        int leftW = 150, gap = 14;
        int x0 = (this.width - totalW) / 2;
        int rightX = x0 + leftW + gap;
        int rightW = totalW - leftW - gap;
        int top = 28;

        HudRenderer.skull(g, x0, 8, 12);
        HudRenderer.text(g, this.font, "T-800 // ARMOR ANALYSIS", x0 + 16, 11, 0.9f, main);

        // ---- model
        int mx1 = x0, mx2 = x0 + leftW, my1 = top, my2 = Math.max(top + 100, this.height - 118);
        g.fill(mx1, my1, mx2, my2, 0x77000000);
        HudRenderer.rect(g, mx1, my1, leftW, 0.5, dim);
        HudRenderer.rect(g, mx1, my2 - 0.5, leftW, 0.5, dim);
        HudRenderer.rect(g, mx1, my1, 0.5, my2 - my1, dim);
        HudRenderer.rect(g, mx2 - 0.5, my1, 0.5, my2 - my1, dim);
        boolean inside = mouseX >= mx1 && mouseX <= mx2 && mouseY >= my1 && mouseY <= my2;
        double t = (Util.getMillis() % 6000L) / 6000.0 * Math.PI * 2;
        float lookX = inside ? mouseX : (float) ((mx1 + mx2) / 2.0 + Math.sin(t) * 75);
        float lookY = inside ? mouseY : (float) ((my1 + my2) / 2.0 - 30);
        int size = Math.max(40, (my2 - my1) / 2 - 8);
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, mx1, my1, mx2, my2, size, 0.0625f, lookX, lookY, p);

        // ---- totals below the model
        double armor = p.getArmorValue();
        double tough = p.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        int prot = 0, fireP = 0, blastP = 0, projP = 0, feather = 0;
        for (int i = 0; i < 4; i++) {
            ItemStack st = p.getItemBySlot(SLOTS[i]);
            if (st.isEmpty()) continue;
            for (Object2IntMap.Entry<Holder<Enchantment>> en : st.getEnchantments().entrySet()) {
                Holder<Enchantment> hd = en.getKey();
                int lv = en.getIntValue();
                if (hd.is(Enchantments.PROTECTION)) prot += lv;
                else if (hd.is(Enchantments.FIRE_PROTECTION)) fireP += lv;
                else if (hd.is(Enchantments.BLAST_PROTECTION)) blastP += lv;
                else if (hd.is(Enchantments.PROJECTILE_PROTECTION)) projP += lv;
                else if (hd.is(Enchantments.FEATHER_FALLING)) feather += lv;
            }
        }
        double maxHp = p.getMaxHealth();
        double meleeMult = (1 - armorRed(armor, tough, 8)) * (1 - enchRed(prot));
        double ehp = maxHp / Math.max(0.05, meleeMult);

        double ty = my2 + 6;
        float s = 0.62f;
        HudRenderer.text(g, this.font, String.format("ARMOR      %.0f / 20", armor), x0, ty, s, txt);
        HudRenderer.rect(g, x0, ty + 8, leftW, 1.5, dim);
        HudRenderer.rect(g, x0, ty + 8, leftW * Mth.clamp(armor / 20.0, 0, 1), 1.5, 0xFFC8D4E0);
        HudRenderer.text(g, this.font, String.format("TOUGHNESS  %.1f", tough), x0, ty + 13, s, txt);
        HudRenderer.text(g, this.font, String.format("HEALTH     %.0f  (+%.0f ABS)", maxHp, p.getAbsorptionAmount()), x0, ty + 20, s, txt);
        HudRenderer.text(g, this.font, String.format("PROT %d  FIRE %d  BLAST %d  PROJ %d  FALL %d", prot, fireP, blastP, projP, feather), x0, ty + 27, 0.5f, dim);
        HudRenderer.text(g, this.font, String.format("EFFECTIVE HP VS MELEE:  %.1f", ehp), x0, ty + 34, s, HudRenderer.AMBER);

        // ---- slots
        int rowH = 22;
        for (int i = 0; i < SLOTS.length; i++) {
            int ry = top + i * rowH;
            ItemStack st = p.getItemBySlot(SLOTS[i]);
            g.fill(rightX, ry, rightX + rightW, ry + rowH - 2, 0x66000000);
            HudRenderer.rect(g, rightX, ry, 0.5, rowH - 2, i < 4 ? main : dim);
            HudRenderer.text(g, this.font, SLOT_NAMES[i], rightX + 4, ry + 2, 0.5f, dim);
            if (st.isEmpty()) {
                HudRenderer.text(g, this.font, "-- EMPTY --", rightX + 24, ry + 7, 0.6f, dim);
                continue;
            }
            g.renderItem(st, rightX + 4, ry + 5);
            String name = st.getHoverName().getString();
            HudRenderer.text(g, this.font, name, rightX + 24, ry + 2, 0.65f, txt);
            if (st.isDamageableItem()) {
                int max = st.getMaxDamage();
                int left = max - st.getDamageValue();
                double fr = Mth.clamp(left / (double) max, 0, 1);
                int col = fr > 0.5 ? 0xFF40E060 : fr > 0.2 ? HudRenderer.AMBER : 0xFFFF4040;
                double bw = Math.min(70, rightW - 90);
                HudRenderer.rect(g, rightX + 24, ry + 11, bw, 1.5, dim);
                HudRenderer.rect(g, rightX + 24, ry + 11, bw * fr, 1.5, col);
                HudRenderer.text(g, this.font, String.format("%d/%d  (%.0f%%)", left, max, fr * 100), rightX + 28 + bw, ry + 9, 0.5f, col);
            }
            if (st.isEnchanted()) {
                StringBuilder sb = new StringBuilder();
                for (Object2IntMap.Entry<Holder<Enchantment>> en : st.getEnchantments().entrySet()) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(Enchantment.getFullname(en.getKey(), en.getIntValue()).getString());
                }
                String line = sb.toString();
                int maxPx = rightW - 30;
                while (line.length() > 3 && this.font.width(line) * 0.5f > maxPx) line = line.substring(0, line.length() - 2);
                if (line.length() < sb.length()) line += "..";
                HudRenderer.text(g, this.font, line, rightX + 24, ry + 15.5, 0.5f, 0xFFB080FF);
            }
        }

        // ---- damage table
        double yy = top + SLOTS.length * rowH + 6;
        HudRenderer.text(g, this.font, "DAMAGE TAKEN  (8 DMG HIT)", rightX, yy, 0.6f, main);
        yy += 9;
        String[] kinds = {"MELEE", "ARROW", "EXPLOSION", "FIRE", "FALL"};
        double[] mult = {
                (1 - armorRed(armor, tough, 8)) * (1 - enchRed(prot)),
                (1 - armorRed(armor, tough, 8)) * (1 - enchRed(prot + 2 * projP)),
                (1 - armorRed(armor, tough, 8)) * (1 - enchRed(prot + 2 * blastP)),
                (1 - enchRed(prot + 2 * fireP)),
                (1 - enchRed(prot + 3 * feather))};
        for (int i = 0; i < kinds.length; i++) {
            HudRenderer.text(g, this.font, kinds[i], rightX, yy, 0.55f, txt);
            HudRenderer.text(g, this.font, String.format("-%.0f%%", (1 - mult[i]) * 100), rightX + 70, yy, 0.55f, HudRenderer.AMBER);
            HudRenderer.text(g, this.font, String.format("= %.1f DMG", 8 * mult[i]), rightX + 105, yy, 0.55f, txt);
            double bw = Math.min(80, rightW - 190);
            if (bw > 10) {
                HudRenderer.rect(g, rightX + 165, yy + 2, bw, 1.5, dim);
                HudRenderer.rect(g, rightX + 165, yy + 2, bw * Mth.clamp(1 - mult[i], 0, 1), 1.5, main);
            }
            yy += 8;
        }
        HudRenderer.text(g, this.font, "FIRE + FALL IGNORE ARMOR (VANILLA RULE)", rightX, yy + 1, 0.45f, dim);
    }
}
