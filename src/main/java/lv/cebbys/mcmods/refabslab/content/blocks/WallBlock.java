package lv.cebbys.mcmods.refabslab.content.blocks;

import lv.cebbys.mcmods.celib.respro.imp.loggers.ResproLogger;
import lv.cebbys.mcmods.celib.utilities.CelibBlockPos;
import lv.cebbys.mcmods.refabslab.content.RefabslabBlocks;
import lv.cebbys.mcmods.refabslab.events.RefabslabEventsClient;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import static net.minecraft.util.math.Direction.Axis.X;
import static net.minecraft.util.math.Direction.Axis.Z;
import static net.minecraft.util.math.Direction.AxisDirection.NEGATIVE;
import static net.minecraft.util.math.Direction.AxisDirection.POSITIVE;

public class WallBlock extends RefabslabBlock {

    public static final DirectionProperty FACING;
    public static final VoxelShape FACING_WEST;
    public static final VoxelShape FACING_EAST;
    public static final VoxelShape FACING_SOUTH;
    public static final VoxelShape FACING_NORTH;

    static {
        FACING = DirectionProperty.of("facing",
                Direction.WEST,
                Direction.EAST,
                Direction.SOUTH,
                Direction.NORTH
        );
        FACING_WEST = VoxelShapes.cuboid(0.0, 0.0, 0.0, 0.5, 1.0, 1.0);
        FACING_EAST = VoxelShapes.cuboid(0.5, 0.0, 0.0, 1.0, 1.0, 1.0);
        FACING_NORTH = VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 1.0, 0.5);
        FACING_SOUTH = VoxelShapes.cuboid(0.0, 0.0, 0.5, 1.0, 1.0, 1.0);
    }

    private final SlabBlock slabVariant;

    public WallBlock(SlabBlock slab) {
        super(FabricBlockSettings.copy(slab));
        this.slabVariant = slab;
        this.setDefaultState(this.getDefaultState().with(FACING, Direction.EAST));
    }

    public SlabBlock getSlabVariant() {
        return this.slabVariant;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return switch (state.get(FACING)) {
            case EAST -> FACING_EAST;
            case WEST -> FACING_WEST;
            case SOUTH -> FACING_SOUTH;
            case NORTH -> FACING_NORTH;
            default -> VoxelShapes.fullCube();
        };
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        World world = ctx.getWorld();
        BlockPos pos = ctx.getBlockPos();
        BlockState existing = world.getBlockState(pos);
        if (ctx.getStack().getItem() instanceof BlockItem blockItem) {
            BlockState inventory = blockItem.getBlock().getDefaultState();
            if (existing.getBlock() instanceof WallBlock existingBlock && inventory.getBlock() instanceof WallBlock inventoryBlock) {
                if (world.isClient()) {
                    RefabslabEventsClient.Execute.createDoubleWallEvent(pos, existingBlock, inventoryBlock, existing.get(WallBlock.FACING));
                }
                return existing;
            }
        }

        Vec3d localPos = ctx.getHitPos()
                .subtract(CelibBlockPos.of(ctx.getBlockPos()).toVec3d())
                .subtract(new Vec3d(0.5, 0.5, 0.5));
        Direction.Axis axis;
        Direction.AxisDirection axisDirection;
        if (Math.abs(localPos.x) > Math.abs(localPos.z)) {
            axis = Direction.Axis.X;
            axisDirection = localPos.x > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE;
        } else {
            axis = Direction.Axis.Z;
            axisDirection = localPos.z > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE;
        }
        return this.getDefaultState().with(WallBlock.FACING, Direction.from(axis, axisDirection));
    }

    public boolean canReplace(BlockState placedState, ItemPlacementContext context) {
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        Item heldItem = context.getStack().getItem();
        if(heldItem instanceof BlockItem heldBlockItem) {
            BlockState heldState = heldBlockItem.getBlock().getDefaultState();
            if (heldState.getBlock() instanceof WallBlock && placedState.getBlock() instanceof WallBlock) {
                BlockState doubleWallState = RefabslabBlocks.DOUBLE_WALL.getDefaultState();
                if (world.canPlace(doubleWallState, pos, ShapeContext.absent())) {
                    Direction placedFacing = placedState.get(WallBlock.FACING);
                    Direction hitFacing = context.getSide();
                    Vec3d hitPos = context.getHitPos();
                    if (hitFacing.equals(placedFacing.getOpposite())) {
                        return true;
                    } else {
                        boolean positive = POSITIVE == placedFacing.getDirection();
                        Comparator comparator = (n1, n2) -> positive ? n1 < n2 : n1 >= n2;
                        double[] values;
                        Direction.Axis sideAxis;
                        if (placedFacing.getAxis().equals(X)) {
                            values = new double[] {pos.getX(), hitPos.getX(), pos.getZ(), hitPos.getZ()};
                            sideAxis = Z;
                        } else {
                            values = new double[] {pos.getZ(), hitPos.getZ(), pos.getX(), hitPos.getX()};
                            sideAxis = X;
                        }
                        if (hitFacing.equals(placedFacing)) {
                            return positive ? values[0] == (int) values[1] : values[0] + 1 == (int) values[1];
                        } else if (comparator.compare(values[1] - values[0], 0.5)) {
                            if (hitFacing.equals(Direction.from(sideAxis, NEGATIVE))) {
                                return values[2] + 1 == (int) values[3];
                            } else if (hitFacing.equals(Direction.from(sideAxis, POSITIVE))) {
                                return values[2] == (int) values[3];
                            } else if (hitFacing.equals(Direction.UP)) {
                                return pos.getY() == (int) hitPos.getY();
                            } else if (hitFacing.equals(Direction.DOWN)) {
                                return pos.getY() + 1 == (int) hitPos.getY();
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private interface Comparator {
        boolean compare(double o1, double o2);
    }

    @Override
    public float getBlockHardness(BlockView world, BlockPos pos) {
        return this.getSlabVariant().getDefaultState().getHardness(world, pos);
    }

    @Override
    public float getMiningSpeed(PlayerEntity player, BlockState state) {
        WallBlock wall = this.getFromState(state);
        return wall == null ? 0.0F : player.getBlockBreakingSpeed(wall.getSlabVariant().getDefaultState());
    }

    @Override
    public float getHarvestSpeed(PlayerEntity player, BlockState state) {
        WallBlock wall = this.getFromState(state);
        return wall == null ? 0.0F : this.canPlayerHarvest(player, state) ? 30.0F : 100.0F;
    }

    @Override
    public boolean canPlayerHarvest(PlayerEntity player, BlockState state) {
        WallBlock wall = this.getFromState(state);
        return wall == null || player.canHarvest(wall.getSlabVariant().getDefaultState());
    }

    private WallBlock getFromState(BlockState state) {
        return state.getBlock() instanceof WallBlock ? (WallBlock) state.getBlock() : null;
    }
}