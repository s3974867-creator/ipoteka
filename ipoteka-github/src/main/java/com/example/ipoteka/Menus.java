package com.example.ipoteka;

import com.example.ipoteka.BankData.PlayerData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Три меню на базе обычного сундука. Ставить мод на клиент не обязательно. */
public final class Menus {
    private Menus() {}

    // ===================== Ипотека =====================
    public static class MortgageMenu extends BankMenu {
        public MortgageMenu(int id, Inventory inv, ServerPlayer p) {
            super(MenuType.GENERIC_9x3, id, inv, new SimpleContainer(27), 3, p);
            refresh();
        }

        private void refresh() {
            Gui.fill(box);
            Item[] icons = {Items.AIR, Items.DIRT, Items.OAK_PLANKS, Items.STONE_BRICKS};
            int[] slots = {0, 10, 12, 14};
            for (int t = 1; t <= 3; t++) {
                box.setItem(slots[t], Gui.icon(icons[t], 1, "§e" + Economy.HOUSE_NAMES[t],
                        "§7Платёж: §b" + Economy.RATES[t] + " алмазов §7в игровой день",
                        "§7Дом ваш после " + Economy.PAYOFF_DAYS + " оплаченных дней",
                        "§7Просрочка больше " + Economy.EVICT_DAYS + " дней = выселение",
                        "",
                        "§aНажмите, чтобы взять (первый день сразу)"));
            }
            PlayerData d = IpotekaMod.DATA.get(player.getUUID());
            long day = IpotekaMod.DATA.day();
            List<String> info = new ArrayList<>();
            info.add("§7У вас в алмазах: §b" + Gui.diamonds(player));
            if (d.house != 0) {
                long due = Economy.dueDays(d, day);
                info.add("§7Дом: §e" + Economy.HOUSE_NAMES[d.house]);
                info.add("§7Оплачено дней: §a" + d.paidDays + "/" + Economy.PAYOFF_DAYS);
                info.add("§7Просрочка: " + (due > 0 ? "§c" : "§a") + due + " дн.");
                info.add("§7К оплате: §b" + (due * Economy.RATES[d.house]) + " алмазов");
                if (due > 0) info.add("§cДо выселения дней: " + (Economy.EVICT_DAYS - due + 1));
            } else {
                info.add("§7Ипотеки нет.");
            }
            info.add("§7Выплачено домов: §6" + d.ownedHouses);
            box.setItem(16, Gui.icon(Items.PAPER, 1, "§fВаш статус", info.toArray(new String[0])));
            box.setItem(22, Gui.icon(Items.EMERALD, 1, "§aОплатить долг по ипотеке",
                    "§7Спишет алмазы за просроченные дни"));
        }

        @Override
        void onSlot(int slot) {
            switch (slot) {
                case 10 -> Economy.buyHouse(player, 1);
                case 12 -> Economy.buyHouse(player, 2);
                case 14 -> Economy.buyHouse(player, 3);
                case 22 -> Economy.payMortgage(player);
                default -> { return; }
            }
            refresh();
        }
    }

    // ===================== Микрозаймы =====================
    public static class LoanMenu extends BankMenu {
        private static final int[] SLOTS = {10, 11, 12, 13, 14, 19, 20, 21, 22, 23};

        public LoanMenu(int id, Inventory inv, ServerPlayer p) {
            super(MenuType.GENERIC_9x4, id, inv, new SimpleContainer(36), 4, p);
            refresh();
        }

        private void refresh() {
            Gui.fill(box);
            for (int i = 0; i < SLOTS.length; i++) {
                int amount = (i + 1) * 10;
                int debt = (amount * (100 + Economy.LOAN_INTEREST_PCT) + 99) / 100;
                box.setItem(SLOTS[i], Gui.icon(Items.DIAMOND, Math.min(64, amount), "§bВзять " + amount + " алмазов",
                        "§7Вернуть: §c" + debt + " §7(+" + Economy.LOAN_INTEREST_PCT + "%)",
                        "§7Срок: " + Economy.LOAN_DAYS + " игровых дня",
                        "§7Потом приедут коллекторы"));
            }
            PlayerData d = IpotekaMod.DATA.get(player.getUUID());
            long day = IpotekaMod.DATA.day();
            List<String> info = new ArrayList<>();
            info.add("§7Вы должны: " + (d.loanDebt > 0 ? "§c" : "§a") + d.loanDebt + " алмазов");
            if (d.loanDebt > 0) {
                long since = day - d.loanDay;
                info.add("§7Дней с займа: §e" + since);
                if (since > Economy.LOAN_DAYS) info.add("§4Коллекторы активны!");
            }
            info.add("§7Отношение к вам: " + Economy.attitude(d, day));
            info.add("§7Репутация: §f" + d.creditScore);
            box.setItem(30, Gui.icon(Items.BOOK, 1, "§fВаше досье", info.toArray(new String[0])));
            box.setItem(32, Gui.icon(Items.EMERALD, 1, "§aПогасить долг",
                    "§7У вас алмазов: §b" + Gui.diamonds(player),
                    "§7Нажмите, чтобы отдать сколько есть"));
        }

        @Override
        void onSlot(int slot) {
            boolean handled = false;
            for (int i = 0; i < SLOTS.length; i++) {
                if (SLOTS[i] == slot) {
                    Economy.takeLoan(player, (i + 1) * 10);
                    handled = true;
                }
            }
            if (slot == 32) {
                Economy.repayLoan(player);
                handled = true;
            }
            if (handled) refresh();
        }
    }

    // ===================== Казино =====================
    public static class CasinoMenu extends BankMenu {
        private static final int[] BETS = {1, 2, 5, 10};
        // множители и шансы (сумма шансов = 1.0, возврат игроку ~96.5%)
        private static final int[] MULT = {0, 2, 5, 10, 20, 50, 100, 1000};
        private static final double[] PROB = {0.76795, 0.17, 0.04, 0.015, 0.005, 0.0015, 0.0005, 0.00005};

        private int sel = 0;
        private ItemStack result = ItemStack.EMPTY;

        public CasinoMenu(int id, Inventory inv, ServerPlayer p) {
            super(MenuType.GENERIC_9x3, id, inv, new SimpleContainer(27), 3, p);
            refresh();
        }

        private void refresh() {
            Gui.fill(box);
            box.setItem(4, Gui.icon(Items.PAPER, 1, "§fВыигрыши",
                    "§7x0 §8- проигрыш", "§7x2 §8- 17%", "§7x5 §8- 4%", "§7x10 §8- 1.5%",
                    "§7x20 §8- 0.5%", "§6x50 §8- 0.15%", "§6x100 §8- 0.05%", "§dx1000 §8- 0.005%"));
            for (int i = 0; i < BETS.length; i++) {
                ItemStack st = Gui.icon(Items.DIAMOND, BETS[i], (i == sel ? "§a▶ " : "§b") + "Ставка " + BETS[i],
                        "§7Нажмите, чтобы выбрать");
                if (i == sel) st.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                box.setItem(10 + i, st);
            }
            box.setItem(15, Gui.icon(Items.GOLD_INGOT, 1, "§6Крутить!",
                    "§7Ставка: §b" + BETS[sel] + " алмазов",
                    "§7У вас: §b" + Gui.diamonds(player)));
            if (!result.isEmpty()) box.setItem(22, result);
        }

        private void play() {
            int bet = BETS[sel];
            if (Gui.diamonds(player) < bet) {
                Gui.msg(player, "§cНе хватает алмазов для ставки " + bet + ".");
                return;
            }
            Gui.take(player, bet);
            double r = ThreadLocalRandom.current().nextDouble(), acc = 0;
            int mult = 0;
            for (int i = 0; i < PROB.length; i++) {
                acc += PROB[i];
                if (r < acc) { mult = MULT[i]; break; }
            }
            int win = bet * mult;
            if (win > 0) Gui.give(player, win);

            if (mult == 0) {
                result = Gui.icon(Items.COAL, 1, "§c0x - проигрыш", "§7Ставка " + bet + " сгорела");
                Gui.msg(player, "§cКазино: x0. Вы проиграли " + bet + " алмазов.");
            } else {
                result = Gui.icon(Items.DIAMOND, Math.min(64, win), "§a" + mult + "x - выигрыш " + win + " алмазов",
                        "§7Ставка: " + bet);
                Gui.msg(player, "§aКазино: x" + mult + "! Вы выиграли " + win + " алмазов.");
                if (mult >= 50 && IpotekaMod.SERVER != null) {
                    IpotekaMod.SERVER.getPlayerList().broadcastSystemMessage(Component.literal(
                            "§6[Казино] " + player.getName().getString() + " сорвал x" + mult + " (" + win + " алмазов)!"), false);
                }
            }
        }

        @Override
        void onSlot(int slot) {
            if (slot >= 10 && slot < 10 + BETS.length) sel = slot - 10;
            else if (slot == 15) play();
            else return;
            refresh();
        }
    }
}

/** Базовое меню-сундук: предметы нельзя забрать, клики обрабатываем сами. */
abstract class BankMenu extends ChestMenu {
    protected final SimpleContainer box;
    protected final ServerPlayer player;

    protected BankMenu(MenuType<?> type, int id, Inventory inv, SimpleContainer box, int rows, ServerPlayer player) {
        super(type, id, inv, box, rows);
        this.box = box;
        this.player = player;
    }

    abstract void onSlot(int slot);

    @Override
    public void clicked(int slotId, int button, ClickType type, Player p) {
        if (slotId >= 0 && slotId < box.getContainerSize() && type == ClickType.PICKUP) {
            onSlot(slotId);
        }
        sendAllDataToRemote(); // вернуть клиенту настоящее состояние
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        return ItemStack.EMPTY;
    }
}

/** Мелкие помощники: иконки, алмазы, сообщения. */
final class Gui {
    static Component text(String s) {
        return Component.literal(s).withStyle(st -> st.withItalic(false));
    }

    static ItemStack icon(Item item, int count, String name, String... lore) {
        ItemStack s = new ItemStack(item, count);
        s.set(DataComponents.CUSTOM_NAME, text(name));
        List<Component> l = new ArrayList<>();
        for (String x : lore) l.add(text(x));
        s.set(DataComponents.LORE, new ItemLore(l));
        return s;
    }

    static void fill(SimpleContainer c) {
        ItemStack f = icon(Items.GRAY_STAINED_GLASS_PANE, 1, " ");
        for (int i = 0; i < c.getContainerSize(); i++) c.setItem(i, f.copy());
    }

    static int diamonds(Player p) {
        int c = 0;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.DIAMOND)) c += s.getCount();
        }
        return c;
    }

    static void take(Player p, int n) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.DIAMOND)) {
                int t = Math.min(n, s.getCount());
                s.shrink(t);
                n -= t;
            }
        }
    }

    static void give(Player p, int n) {
        while (n > 0) {
            int t = Math.min(64, n);
            p.getInventory().placeItemBackInInventory(new ItemStack(Items.DIAMOND, t));
            n -= t;
        }
    }

    static void msg(ServerPlayer p, String s) {
        p.sendSystemMessage(Component.literal(s));
    }
}
