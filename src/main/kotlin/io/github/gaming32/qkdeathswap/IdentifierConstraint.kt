package io.github.gaming32.qkdeathswap

import net.minecraft.util.Identifier
import org.quiltmc.config.api.Constraint
import java.util.*

object IdentifierConstraint: Constraint<String> {
    override fun test(value: String): Optional<String> {
        if (Identifier.isValid(value)) {
            return Optional.empty()
        }
        return Optional.of("Invalid identifier: $value")
    }

    override fun getRepresentation(): String = "net.minecraft.util.Identifier"
}
