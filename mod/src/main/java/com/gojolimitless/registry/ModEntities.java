package com.gojolimitless.registry;

import com.gojolimitless.GojoLimitless;
import com.gojolimitless.entity.BlueOrbEntity;
import com.gojolimitless.entity.FxHostEntity;
import com.gojolimitless.entity.RedOrbEntity;
import com.gojolimitless.entity.PurpleEntity;
import com.gojolimitless.entity.NukeEntity;
import com.gojolimitless.entity.DomainEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModEntities {
    private ModEntities() {}

    public static final EntityType<BlueOrbEntity> BLUE_ORB = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(GojoLimitless.MOD_ID, "blue_orb"),
            EntityType.Builder.<BlueOrbEntity>create(BlueOrbEntity::new, SpawnGroup.MISC)
                    .dimensions(1.0f, 1.0f)
                    .maxTrackingRange(20)
                    .trackingTickInterval(1)
                    .disableSaving()
                    .disableSummon()
                    .makeFireImmune()
                    .build("blue_orb"));

    public static final EntityType<RedOrbEntity> RED_ORB = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(GojoLimitless.MOD_ID, "red_orb"),
            EntityType.Builder.<RedOrbEntity>create(RedOrbEntity::new, SpawnGroup.MISC)
                    .dimensions(0.6f, 0.6f)
                    .maxTrackingRange(24)
                    .trackingTickInterval(1)
                    .disableSaving()
                    .disableSummon()
                    .makeFireImmune()
                    .build("red_orb"));

    public static final EntityType<PurpleEntity> PURPLE = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(GojoLimitless.MOD_ID, "hollow_purple"),
            EntityType.Builder.<PurpleEntity>create(PurpleEntity::new, SpawnGroup.MISC)
                    .dimensions(1.0f, 1.0f)
                    .maxTrackingRange(32)
                    .trackingTickInterval(1)
                    .disableSaving()
                    .disableSummon()
                    .makeFireImmune()
                    .build("hollow_purple"));

    public static final EntityType<NukeEntity> NUKE = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(GojoLimitless.MOD_ID, "remote_hollow_purple"),
            EntityType.Builder.<NukeEntity>create(NukeEntity::new, SpawnGroup.MISC)
                    .dimensions(1.0f, 1.0f)
                    .maxTrackingRange(40)
                    .trackingTickInterval(20)
                    .disableSaving()
                    .disableSummon()
                    .makeFireImmune()
                    .build("remote_hollow_purple"));

    public static final EntityType<DomainEntity> DOMAIN = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(GojoLimitless.MOD_ID, "unlimited_void"),
            EntityType.Builder.<DomainEntity>create(DomainEntity::new, SpawnGroup.MISC)
                    .dimensions(1.0f, 1.0f)
                    .maxTrackingRange(16)
                    .trackingTickInterval(20)
                    .disableSaving()
                    .disableSummon()
                    .makeFireImmune()
                    .build("unlimited_void"));

    /** Client-only host that renders free-floating effects (debris, flashes, ripples). Never spawned by the server. */
    public static final EntityType<FxHostEntity> FX_HOST = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(GojoLimitless.MOD_ID, "fx_host"),
            EntityType.Builder.<FxHostEntity>create(FxHostEntity::new, SpawnGroup.MISC)
                    .dimensions(0.1f, 0.1f)
                    .maxTrackingRange(1)
                    .disableSaving()
                    .disableSummon()
                    .makeFireImmune()
                    .build("fx_host"));

    public static void init() {}
}
