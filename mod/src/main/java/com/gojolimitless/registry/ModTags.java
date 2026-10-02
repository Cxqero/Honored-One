package com.gojolimitless.registry;

import com.gojolimitless.GojoLimitless;
import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public final class ModTags {
    private ModTags() {}
    /** Blocks no technique can erase (in addition to unbreakable ones). data/gojolimitless/tags/block/limitless_immune.json */
    public static final TagKey<Block> LIMITLESS_IMMUNE = TagKey.of(RegistryKeys.BLOCK, Identifier.of(GojoLimitless.MOD_ID, "limitless_immune"));
}
