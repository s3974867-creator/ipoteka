package com.example.ipoteka;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;

public class IpotekaMod implements ModInitializer {
    public static final String NAME_MORTGAGE = "Кассир Ипотеки";
    public static final String NAME_LOANS = "Кассир Микрозаймов";
    public static final String NAME_CASINO = "Крупье Казино";

    public static BankData DATA = new BankData();
    public static MinecraftServer SERVER;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            SERVER = s;
            DATA = BankData.load(s);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> DATA.save(s));
        ServerTickEvents.END_SERVER_TICK.register(this::tick);

        // Клик по кассиру
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            Component custom = entity.getCustomName();
            if (custom == null) return InteractionResult.PASS;
            String name = custom.getString();
            boolean m = name.equals(NAME_MORTGAGE), l = name.equals(NAME_LOANS), c = name.equals(NAME_CASINO);
            if (!m && !l && !c) return InteractionResult.PASS;
            if (hand == InteractionHand.MAIN_HAND && !world.isClientSide() && player instanceof ServerPlayer sp) {
                if (m) sp.openMenu(new SimpleMenuProvider(
                        (id, inv, pl) -> new Menus.MortgageMenu(id, inv, sp), Component.literal("Ипотека")));
                if (l) sp.openMenu(new SimpleMenuProvider(
                        (id, inv, pl) -> new Menus.LoanMenu(id, inv, sp), Component.literal("Микрозаймы")));
                if (c) sp.openMenu(new SimpleMenuProvider(
                        (id, inv, pl) -> new Menus.CasinoMenu(id, inv, sp), Component.literal("Казино")));
            }
            return InteractionResult.SUCCESS; // отменяем обычную торговлю жителя
        });

        // Команды: /bank build  и  /bank day
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("bank")
                        .then(Commands.literal("build").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            Builder.buildBank(ctx.getSource().getServer(), p);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Здания построены: ипотека, за ними микрозаймы, за ними казино (на юг)."), false);
                            return 1;
                        }))
                        .then(Commands.literal("day").executes(ctx -> {
                            long day = DATA.day();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Игровой день банка: " + day + " (день = " + Economy.TICKS_PER_DAY + " тиков)"), false);
                            return 1;
                        }))));
    }

    private void tick(MinecraftServer s) {
        DATA.totalTicks++;
        if (DATA.totalTicks % 20 != 0) return;

        long day = DATA.day();
        boolean newDay = day != DATA.lastNotifiedDay;
        DATA.lastNotifiedDay = day;

        // отложенные сносы домов (если чанк был не загружен)
        DATA.pending.removeIf(cmd -> Builder.run(s, cmd));

        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            Economy.check(s, p, day, newDay);
        }
        if (DATA.totalTicks % 1200 == 0) DATA.save(s);
    }
}
