package ro.patrickszcustoms.config;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;

import java.util.ArrayList;

/** Helpers for the per-entity-type aim assist selection (stored as registry ids like "minecraft:zombie"). */
public final class AimEntities {
    private AimEntities() {}

    public static String id(EntityType<?> type) { return Registries.ENTITY_TYPE.getId(type).toString(); }

    /** First use: select every hostile (MONSTER spawn group) entity type. */
    public static void ensureDefaults(ModConfig c) {
        if (c.aimEntities == null) {
            c.aimEntities = new ArrayList<>();
            setHostileOnly(c);
        }
    }

    public static void setHostileOnly(ModConfig c) {
        if (c.aimEntities == null) c.aimEntities = new ArrayList<>();
        c.aimEntities.clear();
        for (EntityType<?> t : Registries.ENTITY_TYPE) {
            if (t.getSpawnGroup() == SpawnGroup.MONSTER) c.aimEntities.add(id(t));
        }
    }

    public static boolean has(ModConfig c, String id) { return c.aimEntities.contains(id); }

    public static void add(ModConfig c, String id) { if (!c.aimEntities.contains(id)) c.aimEntities.add(id); }

    public static void remove(ModConfig c, String id) { c.aimEntities.remove(id); }

    public static void toggle(ModConfig c, String id) {
        if (!c.aimEntities.remove(id)) c.aimEntities.add(id);
    }
}
