package casa.peterson.dhmo.core.reminders

import casa.peterson.dhmo.core.data.repository.ReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RemindersModule {
    @Binds
    @Singleton
    abstract fun reminderScheduler(impl: NoOpReminderScheduler): ReminderScheduler
}
