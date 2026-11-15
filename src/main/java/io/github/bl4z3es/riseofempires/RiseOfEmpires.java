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
        io.github.bl4z3es.riseofempires.mc.world.RoeWorld.register();
        io.github.bl4z3es.riseofempires.mc.world.DeferredEdits.register();
        io.github.bl4z3es.riseofempires.mc.world.Construction.register();
        io.github.bl4z3es.riseofempires.mc.world.Stockpiles.register();
        io.github.bl4z3es.riseofempires.mc.command.RoeCommands.register();
        LOG.info("[riseofempires] initialised");
    }
}
