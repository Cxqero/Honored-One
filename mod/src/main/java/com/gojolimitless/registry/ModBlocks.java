package com.gojolimitless.registry;

import com.gojolimitless.GojoLimitless;
import com.gojolimitless.block.DomainFloorBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModBlocks {
    private ModBlocks() {}

    /** Invisible floor under a domain cast in mid-air (no item: it only exists while a domain stands on it). */
    public static final Block DOMAIN_FLOOR = Registry.register(Registries.BLOCK, Identifier.of(GojoLimitless.MOD_ID, "domain_floor"),
            new DomainFloorBlock(AbstractBlock.Settings.create()
                    .strength(-1.0f, 3_600_000.0f).dropsNothing().noBlockBreakParticles().nonOpaque().ticksRandomly()
                    .allowsSpawning((s, w, p, t) -> false).solidBlock((s, w, p) -> false).suffocates((s, w, p) -> false)
                    .blockVision((s, w, p) -> false).pistonBehavior(PistonBehavior.BLOCK)));

    public static void init() {}
}
