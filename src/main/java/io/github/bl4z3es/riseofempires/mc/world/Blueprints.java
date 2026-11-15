package io.github.bl4z3es.riseofempires.mc.world;

import io.github.bl4z3es.riseofempires.RiseOfEmpires;
import io.github.bl4z3es.riseofempires.core.sim.camp.Blueprint;
import java.io.BufferedReader;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;

/**
 * Blueprints read from data packs: every {@code data/<ns>/blueprint/<id>.txt}. A data pack can
 * add or replace one without a code change (rule E11). Ids are the file name, for example
 * {@code farm}.
 */
public final class Blueprints {
    private Blueprints() {}

    private static final Map<String, Blueprint> LOADED = new TreeMap<>();

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(Blueprints::load);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, ok) -> load(server));
    }

    public static Blueprint get(String id) {
        return LOADED.get(id);
    }

    public static java.util.Set<String> ids() {
        return LOADED.keySet();
    }

    private static void load(MinecraftServer server) {
        LOADED.clear();
        Map<Identifier, Resource> found = server.getResourceManager()
                .listResources("blueprint", id -> id.getPath().endsWith(".txt"));
        for (Map.Entry<Identifier, Resource> e : found.entrySet()) {
            String path = e.getKey().getPath();
            String id = path.substring("blueprint/".length(), path.length() - ".txt".length());
            try (BufferedReader r = e.getValue().openAsReader()) {
                LOADED.put(id, Blueprint.parse(id, r.lines().collect(Collectors.joining("\n"))));
            } catch (Exception ex) {
                RiseOfEmpires.LOG.error("[riseofempires] Blueprint {} could not be read: {}", e.getKey(), ex.getMessage());
            }
        }
        RiseOfEmpires.LOG.info("[riseofempires] {} blueprints loaded: {}", LOADED.size(), LOADED.keySet());
    }
}
