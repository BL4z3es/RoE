package io.github.bl4z3es.riseofempires.core.sim.camp;

import io.github.bl4z3es.riseofempires.core.Balance;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.github.bl4z3es.riseofempires.core.sim.Simulation;

/**
 * A building builders put up block by block (task M1-13). Blueprints are data-pack text files
 * under {@code data/roe/blueprint/}; this class reads them and splits them into steps.
 *
 * <pre>
 * # comment
 * name: farm
 * size: 7 3 7            x (east), y (up), z (south)
 *
 * palette:
 * L minecraft:oak_log cost minecraft:oak_log 1      char, block state, what it costs
 * F minecraft:farmland free                         free: dug or shaped on site
 * _ keep                                            leave whatever is there (ground, plants)
 *
 * layer:                 one per y, bottom first; layer 0 sits in the ground surface
 * LLLLLLL                one row per z, north first; one char per x, west first
 * </pre>
 *
 * <p>Costs are in the raw goods the camp gathers, so planks cost a quarter of a log. A step is a
 * run of blocks in placement order (bottom layer first, then north to south, west to east) that
 * all cost the same item, at most {@link Balance#BLUEPRINT_STEP_BLOCKS} long. The Minecraft
 * layer places exactly the blocks of step {@code i} when {@link Jobs#complete} reports step
 * {@code i} done, so both sides must use {@link #steps()}.
 */
public record Blueprint(String id, String name, int sizeX, int sizeY, int sizeZ, List<Placement> placements) {

    /** One block: where it goes relative to the site's north-west bottom corner, and its cost. */
    public record Placement(int x, int y, int z, String blockState, String costItem, double cost) {
        public boolean free() {
            return costItem.isEmpty();
        }

        public boolean air() {
            return blockState.equals("minecraft:air");
        }

        /** "Leave whatever is there": nothing is placed and nothing is cleared. */
        public boolean keep() {
            return blockState.equals(KEEP);
        }
    }

    /**
     * A group of placements, {@code [from, to)} in {@link #placements()}, and what it costs.
     * {@code blocks} counts the placements that are not air: clearing air takes no time.
     */
    public record Step(ItemCount cost, int from, int to, int blocks) {}

    /** Palette value meaning "leave this block as it is". */
    public static final String KEEP = "keep";

    public Blueprint {
        placements = List.copyOf(placements);
    }

    /** Reads a blueprint. Throws {@link IllegalArgumentException} with the line at fault. */
    public static Blueprint parse(String id, String text) {
        String name = id;
        int[] size = null;
        Map<Character, Placement> palette = new LinkedHashMap<>();
        List<List<String>> layers = new ArrayList<>();
        String section = "";
        String[] lines = text.split("\\R");
        for (int n = 0; n < lines.length; n++) {
            String line = lines[n].strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            try {
                if (line.startsWith("name:")) name = line.substring(5).strip();
                else if (line.startsWith("size:")) {
                    String[] p = line.substring(5).strip().split("\\s+");
                    size = new int[] {Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])};
                } else if (line.equals("palette:")) section = "palette";
                else if (line.equals("layer:")) { section = "layer"; layers.add(new ArrayList<>()); }
                else if (section.equals("palette")) {
                    String[] p = line.split("\\s+");
                    if (p[0].length() != 1) throw new IllegalArgumentException("palette key must be one character");
                    if (p[1].equals(KEEP)) {
                        palette.put(p[0].charAt(0), new Placement(0, 0, 0, KEEP, "", 0));
                        continue;
                    }
                    Placement entry = p[2].equals("free") ? new Placement(0, 0, 0, p[1], "", 0)
                            : new Placement(0, 0, 0, p[1], p[3], Double.parseDouble(p[4]));
                    if (!p[2].equals("free") && !p[2].equals("cost")) throw new IllegalArgumentException("expected cost or free");
                    palette.put(p[0].charAt(0), entry);
                } else if (section.equals("layer")) layers.get(layers.size() - 1).add(line);
                else throw new IllegalArgumentException("unexpected line");
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Blueprint " + id + " line " + (n + 1) + ": " + e.getMessage(), e);
            }
        }
        if (size == null) throw new IllegalArgumentException("Blueprint " + id + ": no size");
        if (layers.size() != size[1]) {
            throw new IllegalArgumentException("Blueprint " + id + ": size says " + size[1] + " layers, found " + layers.size());
        }
        List<Placement> out = new ArrayList<>();
        for (int y = 0; y < layers.size(); y++) {
            List<String> rows = layers.get(y);
            if (rows.size() != size[2]) {
                throw new IllegalArgumentException("Blueprint " + id + " layer " + y + ": expected " + size[2] + " rows");
            }
            for (int z = 0; z < rows.size(); z++) {
                String row = rows.get(z);
                if (row.length() != size[0]) {
                    throw new IllegalArgumentException("Blueprint " + id + " layer " + y + " row " + z + ": expected "
                            + size[0] + " columns");
                }
                for (int x = 0; x < row.length(); x++) {
                    Placement p = palette.get(row.charAt(x));
                    if (p == null) {
                        throw new IllegalArgumentException("Blueprint " + id + " layer " + y + " row " + z
                                + ": no palette entry for '" + row.charAt(x) + "'");
                    }
                    out.add(new Placement(x, y, z, p.blockState(), p.costItem(), p.cost()));
                }
            }
        }
        return new Blueprint(id, name, size[0], size[1], size[2], out);
    }

    /** The building split into steps. Air is cleared as part of the step it falls in. */
    public List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        int from = 0;
        String item = "";
        double cost = 0;
        for (int i = 0; i < placements.size(); i++) {
            Placement p = placements.get(i);
            boolean switchItem = !p.free() && !item.isEmpty() && !p.costItem().equals(item);
            boolean full = i - from >= Balance.BLUEPRINT_STEP_BLOCKS;
            if ((switchItem || full) && !item.isEmpty()) {
                steps.add(step(item, cost, from, i));
                from = i;
                item = "";
                cost = 0;
            }
            if (!p.free()) { item = p.costItem(); cost += p.cost(); }
        }
        if (from < placements.size()) {
            if (item.isEmpty() && !steps.isEmpty()) {
                Step last = steps.remove(steps.size() - 1);
                steps.add(new Step(last.cost(), last.from(), placements.size(), solid(last.from(), placements.size())));
            } else if (!item.isEmpty()) {
                steps.add(step(item, cost, from, placements.size()));
            }
        }
        return steps;
    }

    private Step step(String item, double cost, int from, int to) {
        return new Step(new ItemCount(item, (int) Math.ceil(cost - 1e-9)), from, to, solid(from, to));
    }

    private int solid(int from, int to) {
        int n = 0;
        for (int i = from; i < to; i++) if (!placements.get(i).air() && !placements.get(i).keep()) n++;
        return n;
    }

    /** Everything the building costs, summed by item. */
    public List<ItemCount> totalCost() {
        Map<String, Integer> total = new LinkedHashMap<>();
        for (Step s : steps()) total.merge(s.cost().item(), s.cost().count(), Integer::sum);
        List<ItemCount> out = new ArrayList<>();
        total.forEach((k, v) -> out.add(new ItemCount(k, v)));
        return out;
    }

    /** Opens a build site for this blueprint. The site's steps are this blueprint's steps. */
    public io.github.bl4z3es.riseofempires.core.ledger.Event open(Simulation sim, String settlementId, String siteId, long cause) {
        List<ItemCount> costs = new ArrayList<>();
        List<Integer> blocks = new ArrayList<>();
        for (Step s : steps()) { costs.add(s.cost()); blocks.add(s.blocks()); }
        return BuildSites.open(sim, settlementId, siteId, name, costs, blocks, id, cause);
    }
}
