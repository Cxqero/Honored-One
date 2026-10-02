package com.gojolimitless.destruction;

import com.gojolimitless.config.ConfigManager;
import com.gojolimitless.config.LimitlessConfig;
import com.gojolimitless.net.Payloads;
import com.gojolimitless.registry.ModTags;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameRules;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Budgeted terrain destruction. Techniques queue shapes; each server tick removes up to
 * {@code general.blocksPerTick} blocks (no drops, no neighbour-update cascades) and streams a
 * sample of the removed blocks to clients as debris.
 */
public final class DestructionEngine {
    public enum Mode {
        /** Blocks are pulled into an entity (Blue). */
        PULL(0),
        /** Blocks are blasted outward (Red). */
        BLAST(1),
        /** Blocks simply cease to exist (Purple). */
        ERASE(2);
        public final int id;
        Mode(int id) { this.id = id; }
    }

    private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;
    private static final BlockState AIR = Blocks.AIR.getDefaultState();
    private static final Map<ServerWorld, ArrayDeque<Job>> JOBS = new WeakHashMap<>();

    private DestructionEngine() {}

    private static final class Job {
        final Vec3d center; final int cx, cy, cz; final int[] offsets; int index;
        final Mode mode; final int ownerId; final Vec3d debrisOrigin;
        final DebrisSampler sampler;
        Job(Vec3d center, int[] offsets, Mode mode, int ownerId, Vec3d debrisOrigin, int debrisBudget) {
            this.center = center;
            this.cx = (int) Math.floor(center.x); this.cy = (int) Math.floor(center.y); this.cz = (int) Math.floor(center.z);
            this.offsets = offsets; this.mode = mode; this.ownerId = ownerId; this.debrisOrigin = debrisOrigin;
            this.sampler = new DebrisSampler(debrisBudget);
        }
        boolean done() { return index * 3 >= offsets.length; }
    }

    public static boolean allowed(ServerWorld world) {
        LimitlessConfig.General g = ConfigManager.get().general;
        if (!g.destruction) return false;
        return !g.respectMobGriefing || world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING);
    }

    /** Queue a sphere of destruction. Processed at the end of this server tick (and following ticks if over budget). */
    public static void carveSphere(ServerWorld world, Vec3d center, double radius, Mode mode, int ownerId) {
        if (radius < 0.5 || !allowed(world)) return;
        JOBS.computeIfAbsent(world, w -> new ArrayDeque<>())
                .add(new Job(center, SphereOffsets.of(radius), mode, ownerId, center, ConfigManager.get().general.debrisPerTick));
    }

    public static boolean canErase(ServerWorld world, BlockPos pos, BlockState state) {
        if (state.isAir()) return false;
        if (state.getHardness(world, pos) < 0) return false;          // bedrock, barriers, portals, command blocks…
        if (state.isIn(ModTags.LIMITLESS_IMMUNE)) return false;
        if (state.hasBlockEntity() && !ConfigManager.get().general.eraseContainers) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof Inventory) return false;
        }
        return true;
    }

    /** Erase one block right now (no budget, no debris). Used by techniques that run their own budgeted sweep. */
    public static boolean eraseAt(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!canErase(world, pos, state)) return false;
        if (state.hasBlockEntity() && world.getBlockEntity(pos) instanceof Inventory inv) inv.clear();
        world.setBlockState(pos, AIR, FLAGS);
        return true;
    }

    public static void tick(ServerWorld world) {
        ArrayDeque<Job> q = JOBS.get(world);
        if (q == null || q.isEmpty()) return;
        int budget = ConfigManager.get().general.blocksPerTick;
        BlockPos.Mutable m = new BlockPos.Mutable();
        Random rand = world.getRandom();
        while (!q.isEmpty() && budget > 0) {
            Job job = q.peekFirst();
            int[] o = job.offsets;
            while (!job.done() && budget > 0) {
                int i = job.index * 3;
                job.index++;
                m.set(job.cx + o[i], job.cy + o[i + 1], job.cz + o[i + 2]);
                if (world.isOutOfHeightLimit(m)) continue;
                if (!world.isChunkLoaded(m.getX() >> 4, m.getZ() >> 4)) continue;
                BlockState state = world.getBlockState(m);
                if (!canErase(world, m, state)) continue;
                if (state.hasBlockEntity() && world.getBlockEntity(m) instanceof Inventory inv) inv.clear();
                world.setBlockState(m, AIR, FLAGS);
                budget--;
                job.sampler.offer(m, state, rand);
            }
            if (job.done()) {
                q.pollFirst();
                flushDebris(world, job);
            }
        }
        // flush partially processed jobs' debris too so visuals keep up
        for (Job job : q) flushDebris(world, job);
    }

    private static void flushDebris(ServerWorld world, Job job) {
        int[] data = job.sampler.drain();
        if (data.length == 0) return;
        Payloads.Debris pkt = new Payloads.Debris(job.ownerId, job.mode.id, job.debrisOrigin.x, job.debrisOrigin.y, job.debrisOrigin.z, data);
        for (ServerPlayerEntity p : PlayerLookup.around(world, job.center, 320)) {
            ServerPlayNetworking.send(p, pkt);
        }
    }

    /** Reservoir sample of removed blocks so large craters send a bounded number of debris pieces. */
    private static final class DebrisSampler {
        private final int cap;
        private int[] buf;
        private int count, seen;
        DebrisSampler(int cap) { this.cap = Math.max(0, cap); this.buf = new int[this.cap * 4]; }

        void offer(BlockPos p, BlockState s, Random r) {
            if (cap == 0) return;
            seen++;
            int slot;
            if (count < cap) slot = count++;
            else {
                int j = r.nextInt(seen);
                if (j >= cap) return;
                slot = j;
            }
            int k = slot * 4;
            buf[k] = p.getX(); buf[k + 1] = p.getY(); buf[k + 2] = p.getZ();
            buf[k + 3] = Block.getRawIdFromState(s);
        }

        int[] drain() {
            int[] out = java.util.Arrays.copyOf(buf, count * 4);
            count = 0; seen = 0;
            return out;
        }
    }

    public static void clear(ServerWorld world) { JOBS.remove(world); }
}
