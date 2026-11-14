package io.github.bl4z3es.riseofempires;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RiseOfEmpires implements ModInitializer {
    public static final String MOD_ID = "roe";
    public static final Logger LOG = LoggerFactory.getLogger("riseofempires");

    @Override
    public void onInitialize() {
        LOG.info("[riseofempires] initialised");
    }
}
