package com.example.ipoteka;

import com.example.ipoteka.BankData.PlayerData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Строит здания и дома через ванильные команды (fill/setblock/summon).
 * Так мы почти не зависим от внутренних API Minecraft, которые меняются между версиями.
 */
final class Builder {

    /** Выполнить команду от имени сервера. false только если чанк не загружен. */
    static boolean run(MinecraftServer s, String cmd) {
        try {
            s.getCommands().getDispatcher().execute(cmd, s.createCommandSourceStack().withSuppressedOutput());
            return true;
        } catch (Exception e) {
            String m = e.getMessage();
            return m == null || !m.contains("not loaded");
        }
    }

    /** "ResourceKey[minecraft:dimension / minecraft:overworld]" -> "minecraft:overworld" */
    static String dim(ServerPlayer p) {
        String s = p.level().dimension().toString();
        int i = s.lastIndexOf("/ ");
        if (i >= 0) return s.substring(i + 2).replace("]", "").trim();
        return "minecraft:overworld";
    }

    // ---------- три здания: ипотека, микрозаймы, казино ----------

    static void buildBank(MinecraftServer s, ServerPlayer p) {
        String dim = dim(p);
        int x0 = p.blockPosition().getX() + 2;
        int y = p.blockPosition().getY();
        int z = p.blockPosition().getZ() + 2;

        office(s, dim, x0, y, z,
                "bricks", "stone_bricks", "oak_planks", "barrel",
                IpotekaMod.NAME_MORTGAGE, "minecraft:librarian");
        office(s, dim, x0, y, z + 14,
                "stone_bricks", "smooth_stone", "polished_andesite", "gold_block",
                IpotekaMod.NAME_LOANS, "minecraft:cartographer");
        office(s, dim, x0, y, z + 28,
                "red_concrete", "black_concrete", "polished_blackstone", "gold_block",
                IpotekaMod.NAME_CASINO, "minecraft:cleric");
    }

    private static void office(MinecraftServer s, String dim, int x0, int y, int z0,
                               String wall, String roof, String floor, String counter,
                               String cashierName, String profession) {
        String in = "execute in " + dim + " run ";
        shell(s, in, x0, y, z0, 9, 4, wall, roof, floor);
        // окна
        run(s, in + "fill " + x0 + " " + (y + 1) + " " + (z0 + 3) + " " + x0 + " " + (y + 2) + " " + (z0 + 5) + " glass");
        run(s, in + "fill " + (x0 + 8) + " " + (y + 1) + " " + (z0 + 3) + " " + (x0 + 8) + " " + (y + 2) + " " + (z0 + 5) + " glass");
        // дверь в северной стене (смотрит на игрока)
        door(s, in, x0 + 4, y, z0, "north");
        // прилавок
        run(s, in + "fill " + (x0 + 2) + " " + y + " " + (z0 + 5) + " " + (x0 + 6) + " " + y + " " + (z0 + 5) + " " + counter);
        // свет
        lantern(s, in, x0 + 1, y + 3, z0 + 1);
        lantern(s, in, x0 + 7, y + 3, z0 + 1);
        lantern(s, in, x0 + 1, y + 3, z0 + 7);
        lantern(s, in, x0 + 7, y + 3, z0 + 7);
        // кассир за прилавком, смотрит на дверь
        run(s, in + "summon minecraft:villager " + (x0 + 4.5) + " " + y + " " + (z0 + 6.5)
                + " {NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,Silent:1b,CustomNameVisible:1b,"
                + "CustomName:\"" + cashierName + "\",Rotation:[180.0f,0.0f],"
                + "VillagerData:{profession:\"" + profession + "\",level:5,type:\"minecraft:plains\"}}");
    }

    // ---------- дома игроков ----------

    static void house(MinecraftServer s, ServerPlayer p, PlayerData d, int type) {
        String in = "execute in " + d.dim + " run ";
        int px = p.blockPosition().getX(), py = p.blockPosition().getY(), pz = p.blockPosition().getZ();

        int w, h;
        String wall, roof, floor;
        boolean windows;
        switch (type) {
            case 1 -> { w = 5;  h = 3; wall = "dirt";         roof = "grass_block";       floor = "dirt";              windows = false; }
            case 2 -> { w = 7;  h = 4; wall = "oak_planks";   roof = "spruce_planks";     floor = "spruce_planks";     windows = true; }
            default -> { w = 11; h = 5; wall = "stone_bricks"; roof = "dark_oak_planks";   floor = "polished_andesite"; windows = true; }
        }
        int x0 = px + 3;
        int z0 = pz - w / 2;
        int zm = z0 + w / 2;

        d.hx = x0; d.hy = py; d.hz = z0; d.hw = w; d.hh = h;

        shell(s, in, x0, py, z0, w, h, wall, roof, floor);
        door(s, in, x0, py, zm, "west");

        if (windows) {
            int xm = x0 + w / 2;
            run(s, in + "fill " + (x0 + w - 1) + " " + (py + 1) + " " + (zm - 1) + " " + (x0 + w - 1) + " " + (py + 2) + " " + (zm + 1) + " glass");
            run(s, in + "fill " + (xm - 1) + " " + (py + 1) + " " + z0 + " " + (xm + 1) + " " + (py + 2) + " " + z0 + " glass");
            run(s, in + "fill " + (xm - 1) + " " + (py + 1) + " " + (z0 + w - 1) + " " + (xm + 1) + " " + (py + 2) + " " + (z0 + w - 1) + " glass");
        }
        lantern(s, in, x0 + 1, py + h - 1, z0 + 1);
        lantern(s, in, x0 + w - 2, py + h - 1, z0 + w - 2);
        // кровать и верстак
        run(s, in + "setblock " + (x0 + w - 3) + " " + py + " " + (z0 + 1) + " red_bed[facing=east,part=foot]");
        run(s, in + "setblock " + (x0 + w - 2) + " " + py + " " + (z0 + 1) + " red_bed[facing=east,part=head]");
        run(s, in + "setblock " + (x0 + 1) + " " + py + " " + (z0 + w - 2) + " crafting_table");
    }

    // ---------- общие кирпичики ----------

    /** Коробка w x w, внутри высота h. Пол лежит на уровне земли (y-1), потолок на y+h. */
    private static void shell(MinecraftServer s, String in, int x0, int y, int z0, int w, int h,
                              String wall, String roof, String floor) {
        run(s, in + "fill " + (x0 - 1) + " " + y + " " + (z0 - 1) + " " + (x0 + w) + " " + (y + h + 1) + " " + (z0 + w) + " air");
        run(s, in + "fill " + x0 + " " + (y - 1) + " " + z0 + " " + (x0 + w - 1) + " " + (y + h) + " " + (z0 + w - 1) + " " + wall + " hollow");
        run(s, in + "fill " + x0 + " " + (y + h) + " " + z0 + " " + (x0 + w - 1) + " " + (y + h) + " " + (z0 + w - 1) + " " + roof);
        run(s, in + "fill " + (x0 + 1) + " " + (y - 1) + " " + (z0 + 1) + " " + (x0 + w - 2) + " " + (y - 1) + " " + (z0 + w - 2) + " " + floor);
    }

    private static void door(MinecraftServer s, String in, int x, int y, int z, String facing) {
        run(s, in + "setblock " + x + " " + y + " " + z + " oak_door[facing=" + facing + ",half=lower]");
        run(s, in + "setblock " + x + " " + (y + 1) + " " + z + " oak_door[facing=" + facing + ",half=upper]");
    }

    private static void lantern(MinecraftServer s, String in, int x, int y, int z) {
        run(s, in + "setblock " + x + " " + y + " " + z + " lantern[hanging=true]");
    }
}
