package com.sanket_satpute_20.ironmind.testutil.fake

import com.sanket_satpute_20.ironmind.domain.common.IdGenerator

class FakeIdGenerator(
    private val prefix: String = "id_"
) : IdGenerator {
    private var counter = 0

    var nextId: String? = null

    override fun generateId(): String {
        nextId?.let {
            val idToReturn = it
            nextId = null
            return idToReturn
        }
        counter++
        return "$prefix$counter"
    }

    fun reset() {
        counter = 0
    }
}
