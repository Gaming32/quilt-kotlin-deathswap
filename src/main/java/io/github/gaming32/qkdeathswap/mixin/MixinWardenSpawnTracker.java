package io.github.gaming32.qkdeathswap.mixin;

import io.github.gaming32.qkdeathswap.PublicCloneable;
import net.minecraft.world.entity.monster.warden.WardenSpawnTracker;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(WardenSpawnTracker.class)
public class MixinWardenSpawnTracker implements Cloneable, PublicCloneable {
    @NotNull
    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
}
