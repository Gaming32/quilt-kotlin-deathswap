package io.github.gaming32.qkdeathswap.mixin;

import net.minecraft.scoreboard.ScoreboardCriterion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ScoreboardCriterion.class)
public interface ScoreboardCriterionAccessor {
    @Invoker
    static ScoreboardCriterion callCreate(String name) {
        throw new UnsupportedOperationException();
    }
}
