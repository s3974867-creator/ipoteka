package com.example.ipoteka;

import com.example.ipoteka.BankData.PlayerData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Вся логика ипотеки и микрозаймов. Настройки - константы ниже. */
final class Economy {
    // Игровой день в 1.5 раза короче обычного: 24000 / 1.5 = 16000 тиков (~13.3 минуты)
    static final long TICKS_PER_DAY = 16000L;

    static final int EVICT_DAYS = 10;     // просрочка ипотеки дольше 10 дней = выселение
    static final int LOAN_DAYS = 3;       // долг дольше 3 дней = коллекторы
    static final int PAYOFF_DAYS = 30;    // сколько дней платить, чтобы дом стал вашим
    static final int LOAN_INTEREST_PCT = 10;

    static final String[] HOUSE_NAMES = {"", "Землянка", "Деревянный дом", "Особняк"};
    static final int[] RATES = {0, 10, 20, 64}; // алмазов в игровой день

    static long dueDays(PlayerData d, long day) {
        return day >= d.paidUntil ? day - d.paidUntil + 1 : 0;
    }

    static String attitude(PlayerData d, long day) {
        if (d.loanDebt > 0 && day - d.loanDay > LOAN_DAYS) return "§4Ненависть: коллекторы уже в пути";
        if (d.loanDebt > 0) return "§6Настороженное: ждут возврата";
        if (d.creditScore >= 5) return "§aVIP-клиент: вам доверяют";
        if (d.creditScore >= 2) return "§2Хорошее";
        if (d.creditScore >= 0) return "§7Нейтральное";
        if (d.creditScore >= -4) return "§cНедоверие";
        return "§4Враждебное";
    }

    /** Вызывается раз в секунду для каждого игрока онлайн. */
    static void check(MinecraftServer s, ServerPlayer p, long day, boolean notify) {
        PlayerData d = IpotekaMod.DATA.get(p.getUUID());

        if (d.house != 0) {
            long due = dueDays(d, day);
            if (due > EVICT_DAYS) {
                evict(p, d);
            } else if (notify && due > 0) {
                Gui.msg(p, "§e[Ипотека] Просрочка: " + due + " дн., к оплате "
                        + (due * RATES[d.house]) + " алмазов. До выселения дней: " + (EVICT_DAYS - due + 1));
            }
        }

        if (d.loanDebt > 0) {
            long since = day - d.loanDay;
            if (since > LOAN_DAYS) {
                if (!d.collectors) {
                    d.collectors = true;
                    d.creditScore = Math.max(-10, d.creditScore - 3);
                    Gui.msg(p, "§4[Микрозаймы] Вы просрочили долг! Коллекторы выехали.");
                }
                if (day > d.lastCollectorDay) {
                    d.lastCollectorDay = day;
                    sendCollectors(s, p);
                }
            } else if (notify) {
                Gui.msg(p, "§e[Микрозаймы] Долг: " + d.loanDebt + " алмазов. Дней до коллекторов: "
                        + (LOAN_DAYS - since + 1));
            }
        }
    }

    static void sendCollectors(MinecraftServer s, ServerPlayer p) {
        String n = p.getName().getString();
        for (int i = 0; i < 3; i++) {
            Builder.run(s, "execute as " + n + " at @s run summon minecraft:vindicator ~" + (i * 2 - 2)
                    + " ~ ~3 {CustomName:\"Коллектор\",CustomNameVisible:1b,PersistenceRequired:1b}");
        }
        Gui.msg(p, "§4Коллекторы нашли вас!");
    }

    static void evict(ServerPlayer p, PlayerData d) {
        String cmd = "execute in " + d.dim + " run fill " + d.hx + " " + d.hy + " " + d.hz + " "
                + (d.hx + d.hw - 1) + " " + (d.hy + d.hh) + " " + (d.hz + d.hw - 1) + " air destroy";
        IpotekaMod.DATA.pending.add(cmd);
        Gui.msg(p, "§4[Ипотека] Вы выселены из дома (" + HOUSE_NAMES[d.house] + ") за неуплату!");
        d.house = 0;
    }

    // ---------- действия из меню ----------

    static void buyHouse(ServerPlayer p, int type) {
        PlayerData d = IpotekaMod.DATA.get(p.getUUID());
        long day = IpotekaMod.DATA.day();
        if (d.house != 0) {
            Gui.msg(p, "§cУ вас уже есть дом в ипотеке: " + HOUSE_NAMES[d.house]);
            return;
        }
        int rate = RATES[type];
        if (Gui.diamonds(p) < rate) {
            Gui.msg(p, "§cНужно " + rate + " алмазов за первый игровой день.");
            return;
        }
        Gui.take(p, rate);
        d.house = type;
        d.paidUntil = day + 1;
        d.paidDays = 1;
        d.dim = Builder.dim(p);
        Builder.house(IpotekaMod.SERVER, p, d, type);
        Gui.msg(p, "§aВы взяли в ипотеку: " + HOUSE_NAMES[type] + ". Дом построен рядом с вами. Платёж: "
                + rate + " алмазов в игровой день.");
    }

    static void payMortgage(ServerPlayer p) {
        PlayerData d = IpotekaMod.DATA.get(p.getUUID());
        if (d.house == 0) {
            Gui.msg(p, "§cУ вас нет ипотеки.");
            return;
        }
        long due = dueDays(d, IpotekaMod.DATA.day());
        if (due == 0) {
            Gui.msg(p, "§aНа сегодня всё оплачено.");
            return;
        }
        int rate = RATES[d.house];
        int canPay = Math.min((int) due, Gui.diamonds(p) / rate);
        if (canPay == 0) {
            Gui.msg(p, "§cНе хватает алмазов. Один день стоит " + rate + ".");
            return;
        }
        Gui.take(p, canPay * rate);
        d.paidUntil += canPay;
        d.paidDays += canPay;
        Gui.msg(p, "§aОплачено дней: " + canPay + " (" + (canPay * rate) + " алмазов).");
        if (d.paidDays >= PAYOFF_DAYS) {
            Gui.msg(p, "§6Поздравляем! Ипотека выплачена, дом теперь ваш навсегда.");
            d.house = 0;
            d.ownedHouses++;
        }
    }

    static void takeLoan(ServerPlayer p, int amount) {
        PlayerData d = IpotekaMod.DATA.get(p.getUUID());
        if (d.loanDebt > 0) {
            Gui.msg(p, "§cСначала погасите текущий долг: " + d.loanDebt);
            return;
        }
        d.loanDebt = (amount * (100 + LOAN_INTEREST_PCT) + 99) / 100;
        d.loanDay = IpotekaMod.DATA.day();
        d.collectors = false;
        d.lastCollectorDay = -1;
        Gui.give(p, amount);
        Gui.msg(p, "§aВы взяли " + amount + " алмазов. Вернуть нужно " + d.loanDebt + " за "
                + LOAN_DAYS + " игровых дня.");
    }

    static void repayLoan(ServerPlayer p) {
        PlayerData d = IpotekaMod.DATA.get(p.getUUID());
        if (d.loanDebt <= 0) {
            Gui.msg(p, "§aУ вас нет долга.");
            return;
        }
        int pay = Math.min(Gui.diamonds(p), d.loanDebt);
        if (pay == 0) {
            Gui.msg(p, "§cУ вас нет алмазов.");
            return;
        }
        Gui.take(p, pay);
        d.loanDebt -= pay;
        if (d.loanDebt == 0) {
            boolean onTime = !d.collectors && (IpotekaMod.DATA.day() - d.loanDay) <= LOAN_DAYS;
            if (onTime) d.creditScore = Math.min(10, d.creditScore + 1);
            d.collectors = false;
            Gui.msg(p, "§aДолг полностью погашен." + (onTime ? " Отношение улучшилось." : ""));
        } else {
            Gui.msg(p, "§eВнесено " + pay + ". Остаток долга: " + d.loanDebt);
        }
    }
}
