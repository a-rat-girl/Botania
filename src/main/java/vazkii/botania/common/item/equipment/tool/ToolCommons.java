/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.common.item.equipment.tool;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.material.Material;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.IItemTier;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolItem;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.vector.Vector3i;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.ForgeHooks;

import vazkii.botania.api.BotaniaAPI;
import vazkii.botania.api.mana.ManaItemHandler;
import vazkii.botania.common.item.ModItems;
import vazkii.botania.common.item.equipment.tool.terrasteel.ItemTerraPick;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

public final class ToolCommons {

	public static final List<Material> materialsPick = Arrays.asList(Material.ROCK, Material.IRON, Material.ICE, Material.GLASS, Material.PISTON, Material.ANVIL, Material.SHULKER);
	public static final List<Material> materialsShovel = Arrays.asList(Material.ORGANIC, Material.EARTH, Material.SAND, Material.SNOW, Material.SNOW_BLOCK, Material.CLAY);
	public static final List<Material> materialsAxe = Arrays.asList(Material.CORAL, Material.LEAVES, Material.PLANTS, Material.WOOD, Material.GOURD);

	/**
	 * Consumes as much mana as possible, returning the amount of damage that couldn't be paid with mana
	 */
	public static int damageItemIfPossible(ItemStack stack, int amount, LivingEntity entity, int manaPerDamage) {
		if (!(entity instanceof PlayerEntity)) {
			return amount;
		}

		PlayerEntity player = (PlayerEntity) entity;
		while (amount > 0) {
			if (ManaItemHandler.instance().requestManaExactForTool(stack, player, manaPerDamage, true)) {
				amount--;
			} else {
				break;
			}
		}

		return amount;
	}

	public static void removeBlocksInIteration(PlayerEntity player, ItemStack stack, World world, BlockPos centerPos,
			Vector3i startDelta, Vector3i endDelta, Predicate<BlockState> filter) {
		for (BlockPos iterPos : BlockPos.getAllInBoxMutable(centerPos.add(startDelta),
				centerPos.add(endDelta))) {
			// skip original block space to avoid crash, vanilla code in the tool class will handle it
			if (iterPos.equals(centerPos)) {
				continue;
			}
			removeBlockWithDrops(player, stack, world, iterPos, filter);
		}
	}

	public static void removeBlockWithDrops(PlayerEntity player, ItemStack stack, World world, BlockPos pos,
			Predicate<BlockState> filter) {
		if (!world.isBlockLoaded(pos)) {
			return;
		}

		BlockState blockstate = world.getBlockState(pos);
		Block block = blockstate.getBlock();

		if (!world.isRemote && filter.test(blockstate) && !blockstate.isAir(world, pos)) {
			// [VanillaCopy] PlayerInteractionManager.tryHarvestBlock, removeBlock inlined
			// we could technically just call that method directly, but run into complications with infinite recursion (since this method)
			// can be invoked from a call chain beginning at tryHarvestBlock as well. It's simpler to just copy.

			int exp = ForgeHooks.onBlockBreakEvent(world, ((ServerPlayerEntity) player).interactionManager.getGameType(), (ServerPlayerEntity) player, pos);
			if (exp == -1) {
				return;
			}

			if (player.isCreative()) {
				boolean removed = blockstate.removedByPlayer(world, pos, player, false, world.getFluidState(pos));
				if (removed) {
					blockstate.getBlock().onPlayerDestroy(world, pos, blockstate);
				}
			} else {
				ItemStack itemstack = stack; // player.getHeldItemMainhand();
				ItemStack itemstack1 = itemstack.copy();
				boolean flag1 = blockstate.canHarvestBlock(world, pos, player); // previously player.func_234569_d_(blockstate)
				itemstack.onBlockDestroyed(world, blockstate, pos, player);
				if (itemstack.isEmpty() && !itemstack1.isEmpty()) {
					net.minecraftforge.event.ForgeEventFactory.onPlayerDestroyItem(player, itemstack1, Hand.MAIN_HAND);
				}
				boolean flag = blockstate.removedByPlayer(world, pos, player, flag1, world.getFluidState(pos));
				if (flag) {
					blockstate.getBlock().onPlayerDestroy(world, pos, blockstate);
				}

				if (flag && flag1) {
					TileEntity tileentity = world.getTileEntity(pos);
					block.harvestBlock(world, player, pos, blockstate, tileentity, itemstack1);
				}

				if (flag && exp > 0) {
					blockstate.getBlock().dropXpOnBlockBreak((ServerWorld) world, pos, exp);
				}
			}
		}
	}

	public static int getToolPriority(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}

		Item item = stack.getItem();
		if (!(item instanceof ToolItem)) {
			return 0;
		}

		ToolItem tool = (ToolItem) item;
		IItemTier material = tool.getTier();
		int materialLevel = 0;
		if (material == BotaniaAPI.instance().getManasteelItemTier()) {
			materialLevel = 10;
		}
		if (material == BotaniaAPI.instance().getElementiumItemTier()) {
			materialLevel = 11;
		}
		if (material == BotaniaAPI.instance().getTerrasteelItemTier()) {
			materialLevel = 20;
		}

		int modifier = 0;
		if (item == ModItems.terraPick) {
			modifier = ItemTerraPick.getLevel(stack);
		}

		int efficiency = EnchantmentHelper.getEnchantmentLevel(Enchantments.EFFICIENCY, stack);
		return materialLevel * 100 + modifier * 10 + efficiency;
	}

	public static BlockRayTraceResult raytraceFromEntity(Entity e, double distance, boolean fluids) {
		return (BlockRayTraceResult) e.pick(distance, 1, fluids);
	}
}
