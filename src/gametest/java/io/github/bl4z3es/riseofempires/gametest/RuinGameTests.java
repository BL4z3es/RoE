package io.github.bl4z3es.riseofempires.gametest;

import io.github.bl4z3es.riseofempires.mc.block.ModItems;
import io.github.bl4z3es.riseofempires.mc.world.HollowRuin;
import io.github.bl4z3es.riseofempires.mc.world.RoeWorld;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

public class RuinGameTests {
    /** M1-09: the ruin stands, with a lectern to take and the Charter in its chest. */
    @GameTest(padding = 8)
    public void theHollowRuinHoldsTheCharterAndALectern(GameTestHelper h) {
        RoeWorld w = RoeWorld.get(h.getLevel().getServer());
        BlockPos corner = h.absolutePos(new BlockPos(0, 1, 0));
        HollowRuin.buildAt(w, h.getLevel(), corner);
        boolean lectern = false, charter = false;
        for (BlockPos p : BlockPos.betweenClosed(corner, corner.offset(10, 3, 10))) {
            if (h.getLevel().getBlockState(p).is(Blocks.LECTERN)) lectern = true;
            if (h.getLevel().getBlockEntity(p) instanceof ChestBlockEntity chest) {
                for (int i = 0; i < chest.getContainerSize(); i++) if (chest.getItem(i).is(ModItems.CHARTER)) charter = true;
            }
        }
        h.assertTrue(lectern, "a lectern stands in the ruin");
        h.assertTrue(charter, "the Charter lies in the ruin's chest");
        h.assertTrue(w.sim().ledger().latest(HollowRuin.STANDS) != null, "the ruin is in the Ledger");
        h.succeed();
    }
}
