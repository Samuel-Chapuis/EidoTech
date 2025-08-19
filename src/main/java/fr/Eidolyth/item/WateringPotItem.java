package fr.Eidolyth.item;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import fr.Eidolyth.block.plants.CustomSaplingBlock;
import fr.Eidolyth.block.plants.MangroveCustomSapling;

public class WateringPotItem extends BlockItem {
    
    public WateringPotItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState blockState = level.getBlockState(pos);
        Player player = context.getPlayer();
        ItemStack itemStack = context.getItemInHand();
        
        // Check if the block is one of our custom saplings
        if (isCustomSapling(blockState.getBlock())) {
            if (!level.isClientSide) {
                // Apply bonemeal effect to custom saplings
                if (applyCustomBonemeal(level, pos, blockState, player)) {
                    // Play bonemeal sound effect
                    level.playSound(null, pos, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
                    
                    // Spawn particles (this will be handled client-side)
                    level.gameEvent(GameEvent.ITEM_INTERACT_FINISH, pos, GameEvent.Context.of(player, blockState));
                    
                    // Damage the watering pot (optional - makes it consumable)
                    if (player != null && !player.getAbilities().instabuild) {
                        itemStack.shrink(1);
                    }
                    
                    return InteractionResult.SUCCESS;
                }
            } else {
                // Client-side: show success to prevent arm swing
                return InteractionResult.SUCCESS;
            }
        }
        
        // Only allow block placement if player is sneaking (holding shift)
        if (player != null && player.isShiftKeyDown()) {
            return super.useOn(context);
        }
        
        // If not sneaking and not a custom sapling, do nothing
        return InteractionResult.PASS;
    }
    
    /**
     * Check if the block is one of our custom saplings
     */
    private boolean isCustomSapling(Block block) {
        return block instanceof CustomSaplingBlock || block instanceof MangroveCustomSapling;
    }
    
    /**
     * Apply bonemeal effect specifically to custom saplings
     */
    private boolean applyCustomBonemeal(Level level, BlockPos pos, BlockState blockState, Player player) {
        Block block = blockState.getBlock();
        
        if (block instanceof CustomSaplingBlock customSapling) {
            if (customSapling.isValidBonemealTarget(level, pos, blockState)) {
                if (level instanceof ServerLevel serverLevel) {
                    RandomSource random = level.getRandom();
                    if (customSapling.isBonemealSuccess(level, random, pos, blockState)) {
                        customSapling.performBonemeal(serverLevel, random, pos, blockState);
                        return true;
                    }
                }
            }
        } else if (block instanceof MangroveCustomSapling mangroveSapling) {
            if (mangroveSapling.isValidBonemealTarget(level, pos, blockState)) {
                if (level instanceof ServerLevel serverLevel) {
                    RandomSource random = level.getRandom();
                    if (mangroveSapling.isBonemealSuccess(level, random, pos, blockState)) {
                        mangroveSapling.performBonemeal(serverLevel, random, pos, blockState);
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
}
