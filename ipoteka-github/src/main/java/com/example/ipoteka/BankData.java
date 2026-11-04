package com.example.ipoteka;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Все данные мода. Хранятся в папке мира: ipoteka_data.json */
public class BankData {
    public static class PlayerData {
        // ипотека
        public int house;                 // 0 = нет, 1 = землянка, 2 = дом, 3 = особняк
        public long paidUntil;            // оплачено до этого игрового дня (не включая)
        public int paidDays;              // сколько дней всего оплачено
        public int ownedHouses;           // выплаченные дома
        public String dim = "minecraft:overworld";
        public int hx, hy, hz, hw, hh;    // где стоит дом
        // микрозаймы
        public int loanDebt;              // сколько должен (с процентами)
        public long loanDay;              // день взятия займа
        public boolean collectors;        // коллекторы уже вызваны
        public long lastCollectorDay = -1;
        public int creditScore;           // репутация: от -10 до +10
    }

    public long totalTicks = 0;
    public long lastNotifiedDay = -1;
    public Map<String, PlayerData> players = new HashMap<>();
    public List<String> pending = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public long day() {
        return totalTicks / Economy.TICKS_PER_DAY;
    }

    public PlayerData get(UUID id) {
        return players.computeIfAbsent(id.toString(), k -> new PlayerData());
    }

    private static Path file(MinecraftServer s) {
        return s.getWorldPath(LevelResource.ROOT).resolve("ipoteka_data.json");
    }

    public static BankData load(MinecraftServer s) {
        try {
            Path p = file(s);
            if (Files.exists(p)) {
                BankData d = GSON.fromJson(Files.readString(p), BankData.class);
                if (d != null) return d;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new BankData();
    }

    public void save(MinecraftServer s) {
        try {
            Files.writeString(file(s), GSON.toJson(this));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
