package slimeknights.tconstruct.smeltery.tileentity;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullConsumer;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.EmptyFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import slimeknights.mantle.util.WeakConsumerWrapper;
import slimeknights.tconstruct.library.EmptyItemHandler;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.tileentity.tank.ISmelteryTankHandler;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Shared logic between drains and ducts
 */
public abstract class SmelteryInputOutputTileEntity<T> extends SmelteryComponentTileEntity {
  /** Capability this TE watches */
  private final Capability<T> capability;
  /** Empty capability for in case the valid capability becomes invalid without invalidating */
  protected final T emptyInstance;
  /** Listener to attach to consumed capabilities */
  protected final NonNullConsumer<LazyOptional<T>> listener = new WeakConsumerWrapper<>(this, (te, cap) -> te.clearHandler());
  @Nullable
  private LazyOptional<T> capabilityHolder = null;

  protected SmelteryInputOutputTileEntity(TileEntityType<?> type, Capability<T> capability, T emptyInstance) {
    super(type);
    this.capability = capability;
    this.emptyInstance = emptyInstance;
  }

  /** Clears all cached capabilities */
  private void clearHandler() {
    if (capabilityHolder != null) {
      capabilityHolder.invalidate();
      capabilityHolder = null;
    }
  }

  @Override
  protected void invalidateCaps() {
    super.invalidateCaps();
    clearHandler();
  }

  @Override
  protected void setMaster(@Nullable BlockPos master, @Nullable Block block) {
    // invalidate if the master changed
    if (!Objects.equals(getMasterPos(), master)) {
      clearHandler();
      assert world != null;
      world.notifyNeighborsOfStateChange(pos, getBlockState().getBlock());
    }
    super.setMaster(master, block);
  }

  /**
   * Gets the capability to store in this IO block. Capability parent should have the proper listeners attached
   * @param parent  Parent tile entity
   * @return  Capability from parent, or empty if absent
   */
  protected LazyOptional<T> getCapability(TileEntity parent) {
    LazyOptional<T> handler = parent.getCapability(capability);
    if (handler.isPresent()) {
      handler.addListener(listener);

      return LazyOptional.of(() -> handler.orElse(emptyInstance));
    }
    return LazyOptional.empty();
  }

  /**
   * Fetches the capability handlers if missing
   */
  private LazyOptional<T> getCachedCapability() {
    if (capabilityHolder == null) {
      if (validateMaster()) {
        BlockPos master = getMasterPos();
        if (master != null && this.world != null) {
          TileEntity te = world.getTileEntity(master);
          if (te != null) {
            capabilityHolder = getCapability(te);
            return capabilityHolder;
          }
        }
      }
      capabilityHolder = LazyOptional.empty();
    }
    return capabilityHolder;
  }

  @Override
  public <C> LazyOptional<C> getCapability(Capability<C> capability, @Nullable Direction facing) {
    if (capability == this.capability) {
      return getCachedCapability().cast();
    }
    return super.getCapability(capability, facing);
  }

  /** Fluid implementation of smeltery IO */
  public static class DrainTileEntity extends SmelteryInputOutputTileEntity<IFluidHandler> {
    public DrainTileEntity() {
      this(TinkerSmeltery.drain.get());
    }

    protected DrainTileEntity(TileEntityType<?> type) {
      super(type, CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, EmptyFluidHandler.INSTANCE);
    }

    @Override
    protected LazyOptional<IFluidHandler> getCapability(TileEntity parent) {
      // fluid capability is not exposed directly in the smeltery
      if (parent instanceof ISmelteryTankHandler) {
        LazyOptional<IFluidHandler> capability = ((ISmelteryTankHandler) parent).getFluidCapability();
        if (capability.isPresent()) {
          capability.addListener(listener);
          return LazyOptional.of(() -> capability.orElse(emptyInstance));
        }
      }
      return LazyOptional.empty();
    }
  }

  /** Item implementation of smeltery IO */
  public static class DuctTileEntity extends SmelteryInputOutputTileEntity<IItemHandler> {
    public DuctTileEntity() {
      this(TinkerSmeltery.drain.get());
    }

    protected DuctTileEntity(TileEntityType<?> type) {
      super(type, CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EmptyItemHandler.INSTANCE);
    }
  }
}
