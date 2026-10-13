package com.maceladder.hud;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Client side of the Mace Ladder server.
 *  - Z  (changeable) -> sends "/trigger mace.dash"  (Gold mace and up)
 *  - '  (changeable) -> sends "/trigger mace.boom"  (Redstone mace and up)
 *  - Draws an icon + key + cooldown on the left of the screen.
 * The server (datapack) keeps the real cooldown and mirrors it onto two hidden effects
 * (Bad Luck = dash, Luck = explosion) so the HUD can read the time left.
 */
public class MaceHudClient implements ClientModInitializer {
    private static final String[] MODELS = {
        "normal_mace", "wooden_mace", "stone_mace", "copper_mace", "lapis_mace", "redstone_mace",
        "gold_mace", "iron_mace", "diamond_mace", "netherite_mace", "emerald_mace", "god_mace"
    };
    private static final int BOOM_TIER = 5, DASH_TIER = 6;
    private static final int DASH_TOTAL = 400, BOOM_TOTAL = 900; // ticks (20 s, 45 s)
    private static final int ICON = 24;

    private static final Identifier DASH_ICON = Identifier.fromNamespaceAndPath("macehud", "textures/gui/dash.png");
    private static final Identifier BOOM_ICON = Identifier.fromNamespaceAndPath("macehud", "textures/gui/boom.png");

    private static KeyMapping dashKey;
    private static KeyMapping boomKey;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("macehud", "abilities"));

               dashKey = KeyMappingHelper.registerKeyMapping("key.macehud.dash", InputConstants.KEY_Z, category);
        boomKey = KeyMappingHelper.registerKeyMapping("key.macehud.boom", InputConstants.KEY_APOSTROPHE, category);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
                if (mc.player == null) return;
            while (dashKey.consumeClick()) {
                if (tier(mc.player) >= DASH_TIER && remaining(mc.player, MobEffects.UNLUCK) == 0) {
                    mc.player.connection.sendCommand("trigger mace.dash");
                }
            }
            while (boomKey.consumeClick()) {
                if (tier(mc.player) >= BOOM_TIER && remaining(mc.player, MobEffects.LUCK) == 0) {
                    mc.player.connection.sendCommand("trigger mace.boom");
                }
            }
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("macehud", "abilities"), MaceHudClient::extract);
    }

    /** Highest mace tier found in the inventory (-1 if none). Read from the item_model the server gives. */
    private static int tier(Player player) {
        int best = -1;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            Identifier model = stack.get(DataComponents.ITEM_MODEL);
            if (model == null || !"mace".equals(model.getNamespace())) continue;
            for (int t = 0; t < MODELS.length; t++) {
                if (MODELS[t].equals(model.getPath())) best = Math.max(best, t);
            }
        }
        return best;
    }

    private static int remaining(Player player, Holder<MobEffect> effect) {
        MobEffectInstance inst = player.getEffect(effect);
        return inst == null ? 0 : inst.getDuration();
    }

    private static void extract(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        int tier = tier(mc.player);
        int y = mc.getWindow().getGuiScaledHeight() / 2 - ICON - 4;
        int x = 6;
        if (tier >= DASH_TIER) {
            drawAbility(graphics, mc, DASH_ICON, x, y, dashKey, remaining(mc.player, MobEffects.UNLUCK), DASH_TOTAL);
            y += ICON + 8;
        }
        if (tier >= BOOM_TIER) {
            drawAbility(graphics, mc, BOOM_ICON, x, y, boomKey, remaining(mc.player, MobEffects.LUCK), BOOM_TOTAL);
        }
    }

    private static void drawAbility(GuiGraphicsExtractor g, Minecraft mc, Identifier icon, int x, int y,
                                    KeyMapping key, int remainingTicks, int totalTicks) {
        g.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0, 0, ICON, ICON, ICON, ICON);
        if (remainingTicks > 0) {
            // white cooldown bar that drains downwards, like a shield on cooldown
            float frac = Math.min(1f, remainingTicks / (float) totalTicks);
            int top = y + (int) (ICON * (1f - frac));
            g.fill(x, top, x + ICON, y + ICON, 0xB0FFFFFF);
        }
        String label = key.getTranslatedKeyMessage().getString();
        g.text(mc.font, label, x + ICON + 4, y + 4, 0xFFFFFFFF, true);
        if (remainingTicks > 0) {
            g.text(mc.font, ((remainingTicks + 19) / 20) + "s", x + ICON + 4, y + 14, 0xFFFFC0C0, true);
        }
    }
}
