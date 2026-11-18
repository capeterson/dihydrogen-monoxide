package casa.peterson.dhmo.core.model

/** Where an intake entry came from. Stored by name. */
enum class Source { MANUAL, WIDGET, NOTIFICATION, TILE, HEALTH_CONNECT_IMPORT, WEAR }

enum class ContainerIcon { GLASS, CUP, MUG, BOTTLE, LARGE_BOTTLE, FLASK }

enum class ProfileSource { MANUAL, HEALTH_CONNECT }

enum class ActivitySource { HEALTH_CONNECT, MANUAL_PLANNED }

/** Stored by name, so existing rows stay valid. Non-binary and unspecified use the same average values in the goal. */
enum class Gender { FEMALE, MALE, NON_BINARY, UNSPECIFIED }

enum class ActivityLevel { SEDENTARY, LIGHT, MODERATE, VERY_ACTIVE }

enum class Climate { TEMPERATE, WARM, HOT_HUMID }

enum class PregnancyStatus { NONE, PREGNANT }

enum class Intensity { LIGHT, MODERATE, VIGOROUS }

enum class GoalMode { CALCULATED, GLOBAL_OVERRIDE, DAY_OVERRIDE }

enum class ClampFlag { NONE, LOW, HIGH }

/** Why entries changed, carried by [EntryChange] (§6.4). */
enum class ChangeOrigin { LOCAL, HEALTH_CONNECT, RESTORE }

/** Why the reminder alarm is being recomputed (§8.7). */
enum class RescheduleReason {
    ENTRY_CHANGED,
    SETTINGS_CHANGED,
    GOAL_CHANGED,
    BOOT,
    TIME_CHANGED,
    TIMEZONE_CHANGED,
    APP_UPDATED,
    APP_FOREGROUND,
    SNOOZE,
}
