package com.sanket_satpute_20.ironmind.domain.common

/**
 * Interface for generating stable, unique IDs.
 */
interface IdGenerator {
    /**
     * Generates a unique string identifier.
     */
    fun generateId(): String
}
