package com.sanket_satpute_20.ironmind.domain.model.protection

sealed interface ProtectionDurationOption {
    val durationMinutes: Int?
    val label: String

    data object OneMinute : ProtectionDurationOption {
        override val durationMinutes: Int = 1
        override val label: String = "1 min"
    }

    data object FifteenMinutes : ProtectionDurationOption {
        override val durationMinutes: Int = 15
        override val label: String = "15 min"
    }

    data object ThirtyMinutes : ProtectionDurationOption {
        override val durationMinutes: Int = 30
        override val label: String = "30 min"
    }

    data object OneHour : ProtectionDurationOption {
        override val durationMinutes: Int = 60
        override val label: String = "1 hour"
    }

    data object TwoHours : ProtectionDurationOption {
        override val durationMinutes: Int = 120
        override val label: String = "2 hours"
    }

    data object Indefinite : ProtectionDurationOption {
        override val durationMinutes: Int? = null
        override val label: String = "Indefinite"
    }

    data class Custom(val minutes: Int) : ProtectionDurationOption {
        override val durationMinutes: Int = minutes
        override val label: String = "$minutes min"
    }

    companion object {
        val DEFAULT: ProtectionDurationOption = FifteenMinutes

        val PRESET_OPTIONS: List<ProtectionDurationOption> = listOf(
            FifteenMinutes,
            ThirtyMinutes,
            OneHour,
            TwoHours,
            OneMinute,
            Indefinite
        )
    }
}

