package io.github.bl4z3es.riseofempires.core.sim.camp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.model.Professions;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class BlueprintTest {
    static final List<String> STARTER = List.of("farm", "stable", "bunkhouse");

    static Blueprint load(String id) throws IOException {
        try (InputStream in = BlueprintTest.class.getResourceAsStream("/data/roe/blueprint/" + id + ".txt")) {
            return Blueprint.parse(id, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void theThreeStarterBuildingsReadAndSplitIntoSteps() throws IOException {
        for (String id : STARTER) {
            Blueprint b = load(id);
            assertEquals(b.sizeX() * b.sizeY() * b.sizeZ(), b.placements().size(), id);
            List<Blueprint.Step> steps = b.steps();
            assertEquals(0, steps.get(0).from(), id);
            assertEquals(b.placements().size(), steps.get(steps.size() - 1).to(), id + " covers every block");
            for (int i = 0; i < steps.size(); i++) {
                Blueprint.Step s = steps.get(i);
                if (i > 0) assertEquals(steps.get(i - 1).to(), s.from(), id + " steps are contiguous");
                assertTrue(s.cost().count() > 0, id + " step " + i + " costs something");
                for (int p = s.from(); p < s.to(); p++) {
                    Blueprint.Placement pl = b.placements().get(p);
                    assertTrue(pl.free() || pl.costItem().equals(s.cost().item()), id + " one item per step");
                }
            }
        }
    }

    @Test
    void theBunkhouseHasFourBedsAndAFarmPlotMatchesAFarmersCrops() throws IOException {
        Blueprint bunk = load("bunkhouse");
        assertEquals(4, bunk.placements().stream().filter(p -> p.blockState().contains("bed[") && p.blockState().contains("part=head")).count());
        Blueprint farm = load("farm");
        assertEquals(Balance.FARM_PLOT_CROPS, farm.placements().stream().filter(p -> p.blockState().equals("minecraft:farmland")).count());
    }

    @Test
    void mistakesInABlueprintNameTheLine() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Blueprint.parse("bad",
                "name: bad\nsize: 2 1 1\npalette:\nA minecraft:stone cost minecraft:cobblestone 1\nlayer:\nAB\n"));
        assertTrue(e.getMessage().contains("'B'"), e.getMessage());
    }

    @Test
    void buildersPutUpTheFarmFromTheGranary() throws IOException {
        Camp camp = new Camp(8);
        camp.beds(3);
        camp.addStock(Food.BREAD, 200);
        Blueprint farm = load("farm");
        for (ItemCount c : farm.totalCost()) camp.addStock(c.item(), c.count());
        camp.assign(2, Professions.BUILDER);
        farm.open(camp.sim, camp.id, "farm-1", 0);
        camp.runDays(2);
        Event finished = camp.events(BuildSites.FINISHED).get(0);
        assertTrue(finished.summary().contains("farm"));
        for (ItemCount c : farm.totalCost()) assertEquals(0, camp.stock(c.item()), c.item());
    }
}
