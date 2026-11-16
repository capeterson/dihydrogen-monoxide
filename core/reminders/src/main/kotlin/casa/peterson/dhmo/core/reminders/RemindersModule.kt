package casa.peterson.dhmo.core.reminders

import casa.peterson.dhmo.core.data.repository.EntryChangeListener
import casa.peterson.dhmo.core.data.repository.ReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RemindersModule {
    @Binds
    abstract fun reminderScheduler(impl: AlarmReminderScheduler): ReminderScheduler

    @Binds
    @IntoSet
    abstract fun entryListener(impl: ReminderEntryListener): EntryChangeListener

    @Binds
    abstract fun calendarBusyReader(impl: ContentResolverCalendarBusyReader): CalendarBusyReader

    @Binds
    abstract fun rolloverWorkScheduler(impl: WorkManagerRolloverScheduler): RolloverWorkScheduler
}
