package io.github.bl4z3es.riseofempires.gametest;

import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.sim.CampRules;
import io.github.bl4z3es.riseofempires.core.sim.camp.Survivors;
import io.github.bl4z3es.riseofempires.mc.block.ModItems;
import io.github.bl4z3es.riseofempires.mc.block.PlanItem;
import io.github.bl4z3es.riseofempires.mc.world.HollowRuin;
import io.github.bl4z3es.riseofempires.mc.world.RoeWorld;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Plays the opening in a real client and a normal, generated single-player world: join, find the
 * Hollow Ruin, found a camp, stock a granary, stake a farm, live through two days.
 *
 * <p>The in-world server tests run in an empty flat world with no player, so they cannot see a
 * fault that needs real terrain, real chunk loading or a player standing nearby. This test fails
 * if the game crashes or the server stops answering at any step. It also takes pictures.
 */
public class OpeningPlaythroughTest implements FabricClientGameTest {
    /** Generous: the test machine draws the game in software. Counted in client ticks. */
    private static final int PATIENCE = 20 * 240;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).create()) {
            TestServerContext server = world.getServer();
            world.getClientWorld().waitForChunksRender();
            context.takeScreenshot("roe-01-joined");

            // The ruin is put up when its chunk loads. Go and stand over it.
            int[] chunk = server.computeOnServer(s -> {
                RoeWorld w = RoeWorld.get(s);
                return new int[] {Integer.parseInt(w.state().tag(Ref.THE_WORLD, HollowRuin.CHUNK_X)),
                        Integer.parseInt(w.state().tag(Ref.THE_WORLD, HollowRuin.CHUNK_Z))};
            });
            // Creative and flying: the test needs to hover for its pictures and still be able to use items.
            server.runCommand("gamemode creative @a");
            server.runOnServer(s -> {
                ServerPlayer player = s.getPlayerList().getPlayers().get(0);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
            });
            server.runCommand("tp @a " + (chunk[0] * 16 + 8) + " 150 " + (chunk[1] * 16 + 8));
            until(context, server, s -> !RoeWorld.get(s).state().tag(Ref.THE_WORLD, HollowRuin.BUILT).isEmpty(), "the Hollow Ruin is built");
            Place ruin = server.computeOnServer(s -> RoeWorld.get(s).sim().ledger().latest(HollowRuin.STANDS).place());
            look(context, world, server, ruin.x() - 8, ruin.y() + 9, ruin.z() - 8, ruin.x() + 6, ruin.y() + 2, ruin.z() + 6);
            context.takeScreenshot("roe-02-hollow-ruin");

            // Found a camp a little way off, the way a player does: a Charter on a lectern.
            BlockPos lectern = server.computeOnServer(s -> {
                ServerLevel level = s.overworld();
                ServerPlayer player = s.getPlayerList().getPlayers().get(0);
                BlockPos pos = surface(level, ruin.x() + 56, ruin.z() + 6);
                level.setBlockAndUpdate(pos, Blocks.LECTERN.defaultBlockState());
                use(player, new ItemStack(ModItems.CHARTER), pos);
                return pos;
            });
            check(server.computeOnServer(s -> RoeWorld.get(s).state().settlements().size()) == 1, "the Charter founded a camp");

            // A granary with food and building stock, and a farm staked out beside it.
            server.runOnServer(s -> {
                ServerLevel level = s.overworld();
                ServerPlayer player = s.getPlayerList().getPlayers().get(0);
                BlockPos seal = surface(level, lectern.getX() + 4, lectern.getZ() - 4);
                level.setBlockAndUpdate(seal, ModItems.GRANARY_SEAL.defaultBlockState());
                BlockPos chestAt = seal.east();
                level.setBlockAndUpdate(chestAt, Blocks.CHEST.defaultBlockState());
                if (level.getBlockEntity(chestAt) instanceof ChestBlockEntity chest) {
                    int slot = 0;
                    for (var item : List.of(Items.BREAD, Items.BREAD, Items.BREAD, Items.OAK_LOG, Items.OAK_LOG,
                            Items.COBBLESTONE, Items.COBBLESTONE, Items.TORCH, Items.WHEAT_SEEDS)) {
                        chest.setItem(slot++, new ItemStack(item, 64));
                    }
                }
                BlockPos farm = surface(level, lectern.getX() + 14, lectern.getZ() + 10).below();
                use(player, PlanItem.of("farm"), farm);
            });
            look(context, world, server, lectern.getX() - 9, lectern.getY() + 8, lectern.getZ() - 9,
                    lectern.getX() + 4, lectern.getY(), lectern.getZ() + 4);
            context.takeScreenshot("roe-03-camp-founded");

            // Night falls and survivors set out for the fire. Let them in the next morning.
            long start = server.computeOnServer(s -> RoeWorld.get(s).state().hour());
            passHours(context, server, start + 14, "night has fallen");
            context.takeScreenshot("roe-04-first-night");
            passHours(context, server, start + 25, "the first night has passed");
            int admitted = server.computeOnServer(s -> {
                RoeWorld w = RoeWorld.get(s);
                Settlement camp = w.state().settlements().iterator().next();
                List<Character> waiting = Survivors.waiting(w.state(), camp.id());
                for (Character c : waiting) CampRules.survivors(w.sim()).admit(w.sim(), camp.id(), c.id());
                return waiting.size();
            });
            System.out.println("[riseofempires] survivors let in: " + admitted);
            context.waitTicks(300);
            context.takeScreenshot("roe-05-first-morning");

            // Two more days with people living and working in front of the player.
            passHours(context, server, start + 30, "the working day is under way");
            context.waitTicks(300);
            context.takeScreenshot("roe-06-at-work");
            passHours(context, server, start + 54, "a second day has passed");
            context.waitTicks(200);
            passHours(context, server, start + 78, "a third day has passed");
            context.waitTicks(200);
            context.takeScreenshot("roe-07-fourth-day");

            String report = server.computeOnServer(s -> {
                RoeWorld w = RoeWorld.get(s);
                Settlement camp = w.state().settlements().iterator().next();
                return "hour " + w.state().hour() + ", " + w.sim().ledger().size() + " events, "
                        + w.state().residents(camp.id()).size() + " residents";
            });
            System.out.println("[riseofempires] opening played through: " + report);
            check(server.computeOnServer(s -> RoeWorld.get(s).sim().ledger().size()) > 10, "the world kept a record of what happened");
        }
    }

    private static void look(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server,
            int x, int y, int z, int atX, int atY, int atZ) {
        server.runCommand("tp @a " + x + " " + y + " " + z + " facing " + atX + " " + atY + " " + atZ);
        context.waitTicks(20);
        world.getClientWorld().waitForChunksRender();
        context.waitTicks(40);
    }

    /** Waits for something to become true on the server. Fails if the server never gets there. */
    private static void until(ClientGameTestContext context, TestServerContext server,
            java.util.function.Predicate<net.minecraft.server.MinecraftServer> done, String what) {
        for (int waited = 0; waited < PATIENCE; waited += 10) {
            if (server.computeOnServer(done::test)) return;
            context.waitTicks(10);
        }
        throw new AssertionError("Gave up waiting until " + what);
    }

    /** Moves the clock on, the way sleeping does, and waits for the simulation to catch up. */
    private static void passHours(ClientGameTestContext context, TestServerContext server, long toHour, String what) {
        long now = server.computeOnServer(s -> RoeWorld.get(s).state().hour());
        if (toHour > now) server.runCommand("time add " + (toHour - now) * 1000);
        until(context, server, s -> RoeWorld.get(s).state().hour() >= toHour, what);
        System.out.println("[riseofempires] " + what + ": " + server.computeOnServer(s -> {
            RoeWorld w = RoeWorld.get(s);
            return "hour " + w.state().hour() + ", " + w.sim().ledger().size() + " events";
        }));
        context.waitTicks(100);
    }

    private static BlockPos surface(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    private static void use(ServerPlayer player, ItemStack stack, BlockPos on) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(on), Direction.UP, on, false)));
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError("Not true: " + what);
    }
}
