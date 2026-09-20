package com.sanket_satpute_20.ironmind.testutil.fake

import com.sanket_satpute_20.ironmind.domain.logging.IronLogger

class FakeIronLogger : IronLogger {
    val loggedMessages = mutableListOf<String>()

    override fun logLifecycle(component: String, event: String, parameters: Map<String, Any?>) {
        val paramStr = parameters.entries.joinToString(" ") { "${it.key}=${it.value}" }
        loggedMessages.add("IronMindLifecycle [$component] [$event] $paramStr".trim())
    }
}
