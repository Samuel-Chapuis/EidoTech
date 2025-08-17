package fr.Eidolyth.block.plants;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.List;
import java.util.Optional;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.Block;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;

public class CustomSaplingBlock extends BushBlock implements BonemealableBlock {
    public static final IntegerProperty STAGE = BlockStateProperties.STAGE;
    protected int yoffset = 0;
    private final List<ResourceLocation> structureList;

    public CustomSaplingBlock(BlockBehaviour.Properties properties, List<ResourceLocation> structures) {
        super(properties);
        this.structureList = structures;
        this.registerDefaultState(this.stateDefinition.any().setValue(STAGE, 0));
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader levelReader, BlockPos blockPos, BlockState blockState) {
        return true;
    }

    @Override
    public boolean isBonemealSuccess(@Nonnull Level level, @Nonnull RandomSource random, @Nonnull BlockPos pos, @Nonnull BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(@Nonnull ServerLevel level, @Nonnull RandomSource random, @Nonnull BlockPos pos, @Nonnull BlockState state) {
        advanceTree(level, pos, state, random);
    }

    public void advanceTree(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (state.getValue(STAGE) == 0) {
            level.setBlock(pos, state.cycle(STAGE), 4);
            return;
        }

        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        Optional<StructureTemplate> opt = level
                .getStructureManager()
                .get(structureList.get(random.nextInt(structureList.size())));
        if (!opt.isPresent()) return;
        StructureTemplate template = opt.get();

        // Rotation random
        net.minecraft.world.level.block.Rotation rotation = net.minecraft.world.level.block.Rotation.getRandom(random);

        Vec3i size = template.getSize();
        int sx = size.getX();
        int sz = size.getZ();
        int hx = sx / 2;
        int hz = sz / 2;

        BlockPos offset;
        switch (rotation) {
            case CLOCKWISE_90:
                offset = new BlockPos(-hz, yoffset, hx);
                break;
            case COUNTERCLOCKWISE_90:
                offset = new BlockPos(hz, yoffset, -hx);
                break;
            case CLOCKWISE_180:
                offset = new BlockPos(-hx, yoffset, -hz);
                break;
            default: // NONE
                offset = new BlockPos(hx, yoffset, hz);
                break;
        }

        BlockPos origin = pos.subtract(offset);

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK)
                .addProcessor(BlockIgnoreProcessor.AIR);

        template.placeInWorld(level, origin, origin, settings, random, 2);
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE);
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return null;
    }
}
