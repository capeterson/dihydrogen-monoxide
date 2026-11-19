package casa.peterson.dhmo.core.data.di

import casa.peterson.dhmo.core.domain.goal.CurrentGoalPolicy
import casa.peterson.dhmo.core.domain.goal.GoalCalculator
import casa.peterson.dhmo.core.domain.goal.GoalPolicy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DomainModule {
    @Provides
    @Singleton
    fun goalPolicy(): GoalPolicy = CurrentGoalPolicy

    @Provides
    @Singleton
    fun goalCalculator(policy: GoalPolicy): GoalCalculator = GoalCalculator(policy)
}
