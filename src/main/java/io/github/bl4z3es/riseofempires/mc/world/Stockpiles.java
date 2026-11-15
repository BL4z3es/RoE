package io.github.bl4z3es.riseofempires.mc.world;

import io.github.bl4z3es.riseofempires.core.Wire;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.model.Stockpile;
import io.github.bl4z3es.riseofempires.core.sim.Effects;
import io.github.bl4z3es.riseofempires.core.sim.Founding;
import io.github.bl4z3es.riseofempires.core.sim.camp.Food;
import io.github.bl4z3es.riseofempires.mc.McTuning;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * Keeps each settlement's stockpile record and the real chests around its Granary Seals in
 * agreement (task M1-12, rule E12). The record changes through core events (meals, harvests,
 * raids, ledger-tier work); the chests change when players move items by hand. Every few seconds,
 * while the granary is loaded:
 *
 * <ol>
 *   <li>what players changed in the chests since the last look becomes a
 *       {@link #COUNTED} event on the record;</li>
 *   <li>what the rules changed in the record is then carried out in the chests. Goods that do
 *       not fit are piled beside the Seal, which is itself an event.</li>
 * </ol>
 *
 * <p>When a granary has not been seen since it loaded (server start, a player returning), its
 * chests are taken as they stand and the record's changes made meanwhile are carried into them.
 * A settlement with no Seal keeps its goods on the record only.
 */
public final class Stockpiles {
    private Stockpiles() {}

    public static final String COUNTED = "stockpile_counted";
    public static final String SPILLED = "stockpile_spilled";
    public static final String SEALED = "granary_sealed";
    public static final String UNSEALED = "granary_unsealed";
    /** Settlement tag: Seal positions as a list of "dimension@x,y,z". */
    public static final String SEALS = "granary.seals";

    /** What the chests held at the last look, per settlement. Not saved: rebuilt on first look. */
    private static final Map<String, Map<String, Integer>> SEEN = new HashMap<>();

    public static void register() {
        RoeWorld.onTick(w -> {
            if (w.ticks() % McTuning.STOCKPILE_SYNC_TICKS == 0) syncAll(w);
        });
    }

    /** A Seal was placed. Inside a claim it starts counting the chests around it. */
    public static void sealPlaced(ServerLevel level, BlockPos pos) {
        RoeWorld w = RoeWorld.get(level.getServer());
        if (w == null) return;
        Settlement s = Founding.settlementAt(w.state(), RoeWorld.place(level, pos));
        if (s == null) return;
        List<String> seals = new ArrayList<>(seals(w, s));
        String key = key(level, pos);
        if (seals.contains(key)) return;
        seals.add(key);
        w.sim().emit(EventDraft.of(SEALED).at(RoeWorld.place(level, pos)).actor(Ref.settlement(s.id()))
                .cause(Founding.foundingEvent(w.sim().ledger(), s.id()))
                .effect(Effects.setTag(Ref.settlement(s.id()), SEALS, Wire.list(seals)))
                .summary("A Granary Seal was set at " + s.name() + ": the chests around it now hold the camp's stores."));
    }

    /** A Seal was removed. Its chests stop counting. */
    public static void sealRemoved(ServerLevel level, BlockPos pos) {
        RoeWorld w = RoeWorld.get(level.getServer());
        if (w == null) return;
        String key = key(level, pos);
        for (Settlement s : w.state().settlements()) {
            List<String> seals = new ArrayList<>(seals(w, s));
            if (!seals.remove(key)) continue;
            SEEN.remove(s.id());
            w.sim().emit(EventDraft.of(UNSEALED).at(RoeWorld.place(level, pos)).actor(Ref.settlement(s.id()))
                    .effect(Effects.setTag(Ref.settlement(s.id()), SEALS, Wire.list(seals)))
                    .summary("A Granary Seal at " + s.name() + " was broken."));
        }
    }

    private static List<String> seals(RoeWorld w, Settlement s) {
        return Wire.unlist(w.state().tag(Ref.settlement(s.id()), SEALS));
    }

    private static String key(ServerLevel level, BlockPos pos) {
        return level.dimension().identifier() + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static void syncAll(RoeWorld w) {
        for (Settlement s : List.copyOf(w.state().settlements())) {
            List<Container> chests = containers(w, s);
            if (chests == null) { SEEN.remove(s.id()); continue; }
            sync(w, s, chests);
        }
    }

    /** The chests of a settlement's granary, or null if it has no Seal or a Seal is not loaded. */
    static List<Container> containers(RoeWorld w, Settlement s) {
        List<String> seals = seals(w, s);
        if (seals.isEmpty()) return null;
        List<Container> out = new ArrayList<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        int r = McTuning.GRANARY_RADIUS;
        for (String seal : seals) {
            int at = seal.lastIndexOf('@');
            String[] c = seal.substring(at + 1).split(",");
            BlockPos centre = new BlockPos(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2]));
            ServerLevel level = w.level(new io.github.bl4z3es.riseofempires.core.ledger.Place(seal.substring(0, at), 0, 0, 0));
            if (level == null || !level.hasChunkAt(centre)) return null;
            for (BlockPos p : BlockPos.betweenClosed(centre.offset(-r, -r, -r), centre.offset(r, r, r))) {
                if (!level.hasChunkAt(p)) continue;
                BlockEntity be = level.getBlockEntity(p);
                if ((be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) && seen.add(p.immutable())) {
                    out.add((Container) be);
                }
            }
        }
        return out;
    }

    private static void sync(RoeWorld w, Settlement s, List<Container> chests) {
        Map<String, Integer> real = count(chests);
        Map<String, Integer> seen = SEEN.get(s.id());
        if (seen != null) {
            Map<String, Integer> byHand = diff(real, seen);
            if (!byHand.isEmpty()) {
                EventDraft d = EventDraft.of(COUNTED).actor(Ref.settlement(s.id())).at(s.centre())
                        .summary("The stores at " + s.name() + " were counted after hands went into the chests: "
                                + describe(byHand) + ".");
                byHand.forEach((item, n) -> d.effect(Effects.addStock(s.stockpileId(), item, n)));
                w.sim().emit(d);
            }
        }
        Stockpile record = w.state().stockpile(s.stockpileId());
        Map<String, Integer> owed = diff(record.items(), real);
        if (!owed.isEmpty()) {
            Map<String, Integer> spilled = new TreeMap<>();
            for (Map.Entry<String, Integer> e : owed.entrySet()) {
                Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(e.getKey()));
                if (e.getValue() > 0) {
                    int left = insert(chests, item, e.getValue());
                    if (left > 0) spilled.put(e.getKey(), left);
                } else {
                    remove(chests, item, -e.getValue());
                }
            }
            if (!spilled.isEmpty()) spill(w, s, spilled);
            real = count(chests);
        }
        SEEN.put(s.id(), real);
    }

    private static void spill(RoeWorld w, Settlement s, Map<String, Integer> spilled) {
        String seal = seals(w, s).get(0);
        int at = seal.lastIndexOf('@');
        String[] c = seal.substring(at + 1).split(",");
        ServerLevel level = w.level(new io.github.bl4z3es.riseofempires.core.ledger.Place(seal.substring(0, at), 0, 0, 0));
        BlockPos pos = new BlockPos(Integer.parseInt(c[0]), Integer.parseInt(c[1]) + 1, Integer.parseInt(c[2]));
        EventDraft d = EventDraft.of(SPILLED).actor(Ref.settlement(s.id())).at(s.centre())
                .summary("The granary at " + s.name() + " is full: " + describe(spilled) + " were piled beside the Seal.");
        for (Map.Entry<String, Integer> e : spilled.entrySet()) {
            d.effect(Effects.addStock(s.stockpileId(), e.getKey(), -e.getValue()));
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(e.getKey()));
            int left = e.getValue();
            while (left > 0) {
                int n = Math.min(left, item.getDefaultMaxStackSize());
                net.minecraft.world.level.block.Block.popResource(level, pos, new ItemStack(item, n));
                left -= n;
            }
        }
        w.sim().emit(d);
    }

    private static Map<String, Integer> count(List<Container> chests) {
        Map<String, Integer> out = new TreeMap<>();
        for (Container c : chests) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack st = c.getItem(i);
                if (!st.isEmpty()) out.merge(BuiltInRegistries.ITEM.getKey(st.getItem()).toString(), st.getCount(), Integer::sum);
            }
        }
        return out;
    }

    /** a - b, item by item, leaving out zeros. */
    private static Map<String, Integer> diff(Map<String, Integer> a, Map<String, Integer> b) {
        Map<String, Integer> out = new TreeMap<>();
        a.forEach((k, v) -> { int d = v - b.getOrDefault(k, 0); if (d != 0) out.put(k, d); });
        b.forEach((k, v) -> { if (!a.containsKey(k)) out.put(k, -v); });
        return out;
    }

    /** Puts items into the chests, filling existing stacks first. Returns how many did not fit. */
    static int insert(List<Container> chests, Item item, int count) {
        int left = count;
        for (int pass = 0; pass < 2 && left > 0; pass++) {
            for (Container c : chests) {
                for (int i = 0; i < c.getContainerSize() && left > 0; i++) {
                    ItemStack st = c.getItem(i);
                    if (pass == 0 && !st.isEmpty() && st.is(item) && st.getComponentsPatch().isEmpty()) {
                        int add = Math.min(left, st.getMaxStackSize() - st.getCount());
                        if (add > 0) { st.grow(add); left -= add; c.setChanged(); }
                    } else if (pass == 1 && st.isEmpty()) {
                        int add = Math.min(left, item.getDefaultMaxStackSize());
                        c.setItem(i, new ItemStack(item, add));
                        left -= add;
                    }
                }
            }
        }
        return left;
    }

    /** Takes items out of the chests. Returns how many were taken. */
    static int remove(List<Container> chests, Item item, int count) {
        int left = count;
        for (Container c : chests) {
            for (int i = c.getContainerSize() - 1; i >= 0 && left > 0; i--) {
                ItemStack st = c.getItem(i);
                if (st.is(item)) {
                    int take = Math.min(left, st.getCount());
                    c.removeItem(i, take);
                    left -= take;
                }
            }
        }
        return count - left;
    }

    private static String describe(Map<String, Integer> items) {
        List<String> parts = new ArrayList<>();
        items.forEach((k, v) -> parts.add((v > 0 ? "+" : "") + v + " " + Food.itemName(k)));
        return String.join(", ", parts);
    }

    /** Test hook: forget what was last seen, as after a reload. */
    public static void forget(String settlementId) {
        SEEN.remove(settlementId);
    }
}
