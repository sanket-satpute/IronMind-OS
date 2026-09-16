package com.sanket_satpute_20.ironmind.infrastructure.common

import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import java.util.UUID

/**
 * Standard implementation of IdGenerator that generates UUIDs.
 */
class UuidGenerator : IdGenerator {
    override fun generateId(): String {
        return UUID.randomUUID().toString()
    }
}
