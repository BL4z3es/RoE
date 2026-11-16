package io.github.bl4z3es.riseofempires;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RiseOfEmpires implements ModInitializer {
    public static final String MOD_ID = "roe";
    public static final Logger LOG = LoggerFactory.getLogger("riseofempires");

    @Override
    public void onInitialize() {
        io.github.bl4z3es.riseofempires.mc.block.ModItems.register();
        io.github.bl4z3es.riseofempires.mc.entity.ModEntities.register();
        io.github.bl4z3es.riseofempires.mc.world.Blueprints.register();
        io.github.bl4z3es.riseofempires.mc.world.Sites.register();
        io.github.bl4z3es.riseofempires.mc.world.Upkeep.register();
        io.github.bl4z3es.riseofempires.mc.world.HollowRuin.register();
        io.github.bl4z3es.riseofempires.mc.entity.Bodies.register();
        io.github.bl4z3es.riseofempires.mc.entity.RaidBattles.register();
        io.github.bl4z3es.riseofempires.mc.world.RoeWorld.register();
        io.github.bl4z3es.riseofempires.mc.world.ArrivedChunks.register();
        io.github.bl4z3es.riseofempires.mc.world.DeferredEdits.register();
        io.github.bl4z3es.riseofempires.mc.world.Construction.register();
        io.github.bl4z3es.riseofempires.mc.world.Stockpiles.register();
        io.github.bl4z3es.riseofempires.mc.command.RoeCommands.register();
        LOG.info("[riseofempires] initialised");
    }
}
