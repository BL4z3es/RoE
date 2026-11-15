package io.github.bl4z3es.riseofempires.gametest;

import com.mojang.serialization.DataResult;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.Ledger;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.sim.Effects;
import io.github.bl4z3es.riseofempires.core.sim.Founding;
import io.github.bl4z3es.riseofempires.core.sim.WorldState;
import io.github.bl4z3es.riseofempires.mc.block.ModItems;
import io.github.bl4z3es.riseofempires.mc.world.LedgerLog;
import io.github.bl4z3es.riseofempires.mc.world.RoeSavedData;
import io.github.bl4z3es.riseofempires.mc.world.RoeWorld;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class WorldGameTests {
    private static RoeWorld world(GameTestHelper h) {
        RoeWorld w = RoeWorld.get(h.getLevel().getServer());
        h.assertTrue(w != null, "Rise of Empires is running");
        return w;
    }

    /** M1-02: the saved data codec and the event log give back exactly what went in. */
    @GameTest
    public void saveAndReloadGiveIdenticalState(GameTestHelper h) throws Exception {
        RoeWorld w = world(h);
        w.sim().emit(EventDraft.of("test_marker").summary("A marker | with, awkward\ncharacters."));
        w.save();
        RoeSavedData saved = new RoeSavedData();
        saved.store(w.state().save(), w.sim().ledger().size(), w.deferred().save());
        Tag tag = RoeSavedData.CODEC.encodeStart(NbtOps.INSTANCE, saved).getOrThrow();
        RoeSavedData back = RoeSavedData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        h.assertTrue(back.state().equals(w.state().save()), "world state survives the codec");
        h.assertTrue(WorldState.load(back.state()).save().equals(w.state().save()), "world state reloads identically");

        Path log = h.getLevel().getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data")
                .resolve("roe").resolve("ledger.log");
        h.assertTrue(Files.exists(log), "the event log file exists");
        Ledger reloaded = new Ledger();
        new LedgerLog(log).restoreInto(reloaded, back.ledgerSize());
        h.assertTrue(reloaded.size() == w.sim().ledger().size(), "every event is in the log");
        for (Event e : w.sim().ledger().all()) {
            h.assertTrue(reloaded.get(e.id()).encode().equals(e.encode()), "event " + e.id() + " reloads identically");
        }
        h.succeed();
    }

    /** M1-10: a Charter on an empty lectern founds a camp, once; a second inside the claim does nothing. */
    @GameTest(maxTicks = 400)
    public void charterOnALecternFoundsACampOnce(GameTestHelper h) {
        RoeWorld w = world(h);
        BlockPos rel = new BlockPos(1, 1, 1);
        h.setBlock(rel, Blocks.LECTERN);
        BlockPos abs = h.absolutePos(rel);
        int before = w.state().settlements().size();
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        use(h, player, abs);
        h.assertTrue(w.state().settlements().size() == before + 1, "a settlement was founded");
        Settlement camp = Founding.settlementAt(w.state(), RoeWorld.place(h.getLevel(), abs));
        h.assertTrue(camp != null, "the lectern stands in the new claim");
        Event founded = w.sim().ledger().latest(Founding.SETTLEMENT_FOUNDED);
        h.assertTrue(founded.actors().contains(io.github.bl4z3es.riseofempires.core.ledger.Ref.settlement(camp.id())), "the founding is in the Ledger");
        h.assertTrue(h.getLevel().getBlockState(abs).getValue(LecternBlock.HAS_BOOK), "the Charter lies on the lectern");

        BlockPos second = new BlockPos(3, 1, 1);
        h.setBlock(second, Blocks.LECTERN);
        use(h, player, h.absolutePos(second));
        h.assertTrue(w.state().settlements().size() == before + 1, "no second camp inside the claim");
        h.assertTrue(!h.getLevel().getBlockState(h.absolutePos(second)).getValue(LecternBlock.HAS_BOOK), "the second Charter stays in hand");
        h.succeed();
    }

    private static void use(GameTestHelper h, ServerPlayer player, BlockPos abs) {
        ItemStack charter = new ItemStack(ModItems.CHARTER);
        player.setItemInHand(InteractionHand.MAIN_HAND, charter);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        charter.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    /** M1-12: chests by a Granary Seal are the stockpile, both ways. */
    @GameTest(maxTicks = 400)
    public void chestsByAGranarySealAreTheStockpile(GameTestHelper h) {
        RoeWorld w = world(h);
        BlockPos centre = h.absolutePos(new BlockPos(2, 1, 2));
        Event founded = Founding.found(w.sim(), "test-granary-" + centre.asLong(), "Tester", RoeWorld.place(h.getLevel(), centre));
        Settlement camp = founded == null ? Founding.settlementAt(w.state(), RoeWorld.place(h.getLevel(), centre))
                : Founding.settlementAt(w.state(), RoeWorld.place(h.getLevel(), centre));
        String stock = camp.stockpileId();
        int wheatBefore = w.state().stockpile(stock).count("minecraft:wheat");
        h.setBlock(new BlockPos(2, 1, 2), ModItems.GRANARY_SEAL);
        h.setBlock(new BlockPos(3, 1, 2), Blocks.CHEST);
        h.runAfterDelay(60, () -> {
            ChestBlockEntity chest = (ChestBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(3, 1, 2)));
            chest.setItem(0, new ItemStack(Items.WHEAT, 10));
            h.runAfterDelay(60, () -> {
                h.assertTrue(w.state().stockpile(stock).count("minecraft:wheat") >= wheatBefore + 10,
                        "wheat put in by hand reached the record");
                w.sim().emit(EventDraft.of("test_delivery").effect(Effects.addStock(stock, "minecraft:bread", 5)));
                h.runAfterDelay(60, () -> {
                    int bread = 0;
                    for (int i = 0; i < chest.getContainerSize(); i++) {
                        if (chest.getItem(i).is(Items.BREAD)) bread += chest.getItem(i).getCount();
                    }
                    h.assertTrue(bread >= 5, "bread added by the rules reached the chest");
                    h.succeed();
                });
            });
        });
    }

    /** M1-17: an edit queued for a loaded chunk applies straight away. */
    @GameTest
    public void aDeferredEditForALoadedChunkApplies(GameTestHelper h) {
        RoeWorld w = world(h);
        BlockPos abs = h.absolutePos(new BlockPos(1, 1, 1));
        w.deferred().queue(h.getLevel(), abs, "minecraft:oak_planks", 0);
        h.assertTrue(h.getLevel().getBlockState(abs).is(Blocks.OAK_PLANKS), "the edit applied");
        h.succeed();
    }
}
