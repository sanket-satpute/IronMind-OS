package com.sanket_satpute_20.ironmind.testutil.fake

import com.sanket_satpute_20.ironmind.domain.common.IdGenerator

class FakeIdGenerator(
    private val prefix: String = "id_"
) : IdGenerator {
    private var counter = 0

    override fun generateId(): String {
        counter++
        return "$prefix$counter"
    }

    fun reset() {
        counter = 0
    }
}
