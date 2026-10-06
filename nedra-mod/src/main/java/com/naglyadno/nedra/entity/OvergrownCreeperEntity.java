package com.naglyadno.nedra.entity;

import com.naglyadno.nedra.block.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.rule.GameRules;

/**
 * Заросший крипер. Взрывается как обычный, но после взрыва вокруг воронки за мгновение прорастает мох,
 * папоротник и светошляпки - споры, которыми он набит, разлетаются по камню (если включено mobGriefing).
 */
public class OvergrownCreeperEntity extends CreeperEntity {

	private static final int BLOOM_RADIUS = 4;

	public OvergrownCreeperEntity(EntityType<? extends OvergrownCreeperEntity> entityType, World world) {
		super(entityType, world);
	}

	@Override
	public void tick() {
		super.tick();
		World world = this.getEntityWorld();
		// взрыв крипера помечает его мёртвым и сразу удаляет, при этом здоровье остаётся целым
		// (при обычной смерти здоровье 0) - так отличаем взрыв от гибели от оружия
		if (world instanceof ServerWorld serverWorld && this.isRemoved() && this.dead && this.getHealth() > 0.0F
				&& Boolean.TRUE.equals(serverWorld.getGameRules().getValue(GameRules.DO_MOB_GRIEFING))) {
			bloom(serverWorld, this.getBlockPos());
		} else if (world.isClient() && this.random.nextInt(20) == 0) {
			world.addParticleClient(ParticleTypes.SPORE_BLOSSOM_AIR,
					this.getParticleX(0.5), this.getRandomBodyY(), this.getParticleZ(0.5), 0.0, 0.0, 0.0);
		}
	}

	private void bloom(ServerWorld world, BlockPos center) {
		for (int dx = -BLOOM_RADIUS; dx <= BLOOM_RADIUS; dx++) {
			for (int dz = -BLOOM_RADIUS; dz <= BLOOM_RADIUS; dz++) {
				if (dx * dx + dz * dz > BLOOM_RADIUS * BLOOM_RADIUS || this.random.nextFloat() < 0.35F) {
					continue;
				}
				for (int dy = 3; dy >= -4; dy--) {
					BlockPos ground = center.add(dx, dy, dz);
					BlockPos above = ground.up();
					BlockState groundState = world.getBlockState(ground);
					if (!world.getBlockState(above).isAir() || !groundState.isIn(BlockTags.MOSS_REPLACEABLE)) {
						continue;
					}
					world.setBlockState(ground, Blocks.MOSS_BLOCK.getDefaultState());
					float roll = this.random.nextFloat();
					BlockState plant = roll < 0.25F ? ModBlocks.DEEP_FERN.getDefaultState()
							: roll < 0.33F ? ModBlocks.GLOWCAP.getDefaultState()
							: roll < 0.55F ? Blocks.MOSS_CARPET.getDefaultState() : null;
					if (plant != null && plant.canPlaceAt(world, above)) {
						world.setBlockState(above, plant);
					}
					break;
				}
			}
		}
	}
}
