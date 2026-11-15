package casa.peterson.dhmo.core.common

import javax.inject.Qualifier

/** Which coroutine dispatcher a `@Dispatcher` injection point wants. */
enum class DhmoDispatcher { IO, Default }

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val dispatcher: DhmoDispatcher)

/** The process-wide `CoroutineScope` (`SupervisorJob() + Dispatchers.Default`). */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
