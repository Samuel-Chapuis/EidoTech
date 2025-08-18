package fr.Eidolyth.block.plants;

import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PinkPetalsBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import javax.annotation.Nullable;

public class LeafLitterBlock extends PinkPetalsBlock implements net.minecraft.client.color.block.BlockColor {

    // Fixed collision box for all leaf litter amounts - full block width, thin height
    private static final VoxelShape FIXED_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);

    private final int blendColor;
    private final float blendRatio;

    public LeafLitterBlock(BlockBehaviour.Properties properties) {
        this(properties, 0x48B518, 0.0f); // Default green color, no blending
    }

    public LeafLitterBlock(BlockBehaviour.Properties properties, int blendColor, float blendRatio) {
        super(properties);
        this.blendColor = blendColor;
        this.blendRatio = Math.max(0.0f, Math.min(1.0f, blendRatio)); // Clamp between 0 and 1
    }

    @Override
    public int getColor(BlockState state, @Nullable BlockAndTintGetter world, @Nullable BlockPos pos, int tintIndex) {
        // Simple test: make normal leaf litter bright orange, spring leaf litter bright green
        if (blendRatio > 0.0f) {
            return 0xFF8C00; // Bright orange (dark orange)
        }
        return 0x00FF00; // Bright green
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return FIXED_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return FIXED_SHAPE;
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return FIXED_SHAPE;
    }
    
    private int blendColors(int color1, int color2, float ratio) {
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;
        
        int r = (int) (r1 * (1 - ratio) + r2 * ratio);
        int g = (int) (g1 * (1 - ratio) + g2 * ratio);
        int b = (int) (b1 * (1 - ratio) + b2 * ratio);
        
        return (r << 16) | (g << 8) | b;
    }
}
