package io.github.gaming32.qkdeathswap.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import io.github.gaming32.qkdeathswap.DeathSwapStateManager;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.level.storage.WritableLevelData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@Mixin(ServerLevel.class)
public abstract class MixinServerLevel extends Level {
    @Mutable
    @Shadow
    private @Nullable EndDragonFight dragonFight;

    protected MixinServerLevel(WritableLevelData writableLevelData, ResourceKey<Level> resourceKey, RegistryAccess registryAccess, Holder<DimensionType> holder, Supplier<ProfilerFiller> supplier, boolean bl, boolean bl2, long l, int i) {
        super(writableLevelData, resourceKey, registryAccess, holder, supplier, bl, bl2, l, i);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void spawnEndBossFight(
        MinecraftServer minecraftServer,
        Executor executor,
        LevelStorageSource.LevelStorageAccess levelStorageAccess,
        ServerLevelData serverLevelData,
        ResourceKey<Level> resourceKey,
        LevelStem levelStem,
        ChunkProgressListener chunkProgressListener,
        boolean bl,
        long l,
        List<CustomSpawner> list,
        boolean bl2,
        RandomSequences randomSequences,
        CallbackInfo ci
    ) {
        if (DeathSwapStateManager.INSTANCE.isCreatingFantasyWorld().get() && dimensionTypeRegistration().is(BuiltinDimensionTypes.END)) {
            dragonFight = new EndDragonFight((ServerLevel)(Object)this, l, EndDragonFight.Data.DEFAULT);
        }
    }

    @WrapWithCondition(
        method = "saveLevelData",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/storage/WorldData;setEndDragonFightData(Lnet/minecraft/world/level/dimension/end/EndDragonFight$Data;)V"
        )
    )
    private boolean dontOverwriteGlobalDragonFight(WorldData instance, EndDragonFight.Data data) {
        return !DeathSwapStateManager.INSTANCE.isFantasyWorld((ServerLevel)(Object)this);
    }
}
