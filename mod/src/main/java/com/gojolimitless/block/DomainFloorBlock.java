package com.gojolimitless.block;

import com.gojolimitless.entity.DomainEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/**
 * The invisible floor a domain lays under its caster when it's cast in mid-air, so no one inside falls through. Can't
 * be seen, targeted or broken; the domain takes it away when it ends. One left behind by a domain that is gone (the
 * world closed mid-domain) removes itself.
 */
public class DomainFloorBlock extends Block {
    public static final MapCodec<DomainFloorBlock> CODEC = createCodec(DomainFloorBlock::new);

    public DomainFloorBlock(Settings settings) { super(settings); }

    @Override protected MapCodec<? extends Block> getCodec() { return CODEC; }

    @Override protected BlockRenderType getRenderType(BlockState state) { return BlockRenderType.INVISIBLE; }

    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) { return VoxelShapes.empty(); }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) { return VoxelShapes.fullCube(); }

    @Override protected boolean isTransparent(BlockState state, BlockView world, BlockPos pos) { return true; }

    @Override protected float getAmbientOcclusionLightLevel(BlockState state, BlockView world, BlockPos pos) { return 1f; }

    @Override
    protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (world.getEntitiesByClass(DomainEntity.class, new Box(pos).expand(96), e -> !e.isRemoved()).isEmpty()) world.removeBlock(pos, false);
    }
}
