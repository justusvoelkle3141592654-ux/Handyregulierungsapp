package de.handyzeitvertreib.app.data

import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.truth.Truth.assertThat
import de.handyzeitvertreib.app.core.model.RegulationAction
import de.handyzeitvertreib.app.enforcement.LimitCheckWorker
import de.handyzeitvertreib.app.testing.HOUR
import de.handyzeitvertreib.app.testing.MINUTE
import de.handyzeitvertreib.app.testing.TestHzvApplication
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LimitCheckWorkerTest {
    @Test
    fun workerRecordsReachedLimitOnlyOnce() =
        runBlocking {
            val app = ApplicationProvider.getApplicationContext<TestHzvApplication>()
            app.events.session("com.example.video", app.clock.startOfToday() + HOUR, 50 * MINUTE)
            app.container.limitRepository.saveAppLimit("com.example.video", 45)

            val worker = TestListenableWorkerBuilder<LimitCheckWorker>(app).build()
            assertThat(worker.doWork()).isEqualTo(ListenableWorker.Result.success())
            assertThat(worker.doWork()).isEqualTo(ListenableWorker.Result.success())

            val events = app.container.regulationRepository.eventsFor(app.clock.today())
            assertThat(events.count { it.action == RegulationAction.LIMIT_REACHED }).isEqualTo(1)
        }

    @Test
    fun workerWithoutAccessDoesNothing() =
        runBlocking {
            val app = ApplicationProvider.getApplicationContext<TestHzvApplication>()
            app.events.access = false
            app.container.limitRepository.saveAppLimit("com.example.video", 45)
            val worker = TestListenableWorkerBuilder<LimitCheckWorker>(app).build()
            assertThat(worker.doWork()).isEqualTo(ListenableWorker.Result.success())
            assertThat(app.container.regulationRepository.eventsFor(app.clock.today())).isEmpty()
        }
}
