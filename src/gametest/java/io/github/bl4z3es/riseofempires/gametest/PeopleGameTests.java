package io.github.bl4z3es.riseofempires.gametest;

import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.sim.Effects;
import io.github.bl4z3es.riseofempires.core.sim.Founding;
import io.github.bl4z3es.riseofempires.core.sim.camp.BuildSites;
import io.github.bl4z3es.riseofempires.mc.block.PlanItem;
import io.github.bl4z3es.riseofempires.mc.entity.ModEntities;
import io.github.bl4z3es.riseofempires.mc.entity.PersonEntity;
import io.github.bl4z3es.riseofempires.mc.world.Blueprints;
import io.github.bl4z3es.riseofempires.mc.world.RoeWorld;
import io.github.bl4z3es.riseofempires.mc.world.Sites;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class PeopleGameTests {
    /** M1-07: a body walks where it is sent, and a new body for the same character is the same person. */
    @GameTest(maxTicks = 400)
    public void aPersonWalksAndKeepsTheirIdentity(GameTestHelper h) {
        RoeWorld w = RoeWorld.get(h.getLevel().getServer());
        String id = "test-walker-" + h.absolutePos(BlockPos.ZERO).asLong();
        w.sim().emit(EventDraft.of("test_person").effect(Effects.putCharacter(new Character(id, "Wenna", "gentle",
                "a quiet winter", "", List.of(), "", Character.Status.ALIVE))));
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) {
            h.setBlock(new BlockPos(x, 0, z), net.minecraft.world.level.block.Blocks.STONE);
        }
        PersonEntity body = h.spawn(ModEntities.PERSON, new BlockPos(1, 1, 1));
        bind(body, w.state().character(id));
        BlockPos goal = h.absolutePos(new BlockPos(6, 1, 6));
        h.onEachTick(() -> {
            if (!body.isRemoved() && body.getNavigation().isDone()) {
                body.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 1.0);
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(body.distanceToSqr(Vec3.atBottomCenterOf(goal)) < 4, "the person reached the spot");
            body.discard();
            PersonEntity again = ModEntities.PERSON.create(h.getLevel(), EntitySpawnReason.EVENT);
            bind(again, w.state().character(id));
            h.assertTrue(again.characterId().equals(id), "a new body is the same character");
            h.assertTrue(again.getName().getString().equals("Wenna"), "and carries their name");
        });
    }

    private static void bind(PersonEntity body, Character c) {
        try {
            var m = PersonEntity.class.getDeclaredMethod("bind", Character.class);
            m.setAccessible(true);
            m.invoke(body, c);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** M1-13: a plan laid inside a claim opens a build site with the blueprint's steps and an origin. */
    @GameTest
    public void aPlanStakesOutASite(GameTestHelper h) {
        RoeWorld w = RoeWorld.get(h.getLevel().getServer());
        h.assertTrue(Blueprints.get("farm") != null, "the farm blueprint loaded from the data pack");
        BlockPos centre = h.absolutePos(new BlockPos(2, 1, 2));
        Founding.found(w.sim(), "test-plan-" + centre.asLong(), "Planner", RoeWorld.place(h.getLevel(), centre));
        Settlement camp = Founding.settlementAt(w.state(), RoeWorld.place(h.getLevel(), centre));
        int before = BuildSites.open(w.state(), camp.id()).size();
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        ItemStack plan = PlanItem.of("farm");
        player.setItemInHand(InteractionHand.MAIN_HAND, plan);
        BlockPos ground = h.absolutePos(new BlockPos(4, 0, 4));
        plan.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(ground), Direction.UP, ground, false)));
        List<BuildSites.Site> open = BuildSites.open(w.state(), camp.id());
        h.assertTrue(open.size() == before + 1, "a site was opened");
        BuildSites.Site site = open.get(open.size() - 1);
        h.assertTrue(site.blueprint().equals("farm"), "it is the farm");
        h.assertTrue(site.steps().size() == Blueprints.get("farm").steps().size(), "with the blueprint's steps");
        h.assertTrue(Sites.origin(w.state(), site.id()) != null, "and a place in the world");
        h.succeed();
    }
}
