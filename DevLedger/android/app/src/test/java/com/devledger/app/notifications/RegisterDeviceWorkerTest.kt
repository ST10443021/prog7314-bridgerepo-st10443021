package com.devledger.app.notifications

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RegisterDeviceWorkerTest {
    private lateinit var context: Context
    private val registrar = mockk<DeviceRegistrar>()

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun worker(): RegisterDeviceWorker = TestListenableWorkerBuilder<RegisterDeviceWorker>(context)
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
                if (workerClassName == RegisterDeviceWorker::class.java.name) {
                    RegisterDeviceWorker(appContext, workerParameters, registrar)
                } else null
        }).build()

    @Test fun `API acknowledgement completes work`() = runTest {
        coEvery { registrar.registerCurrentToken() } returns DeviceRegistrationOutcome.REGISTERED
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
    }

    @Test fun `transient failure really returns WorkManager retry`() = runTest {
        coEvery { registrar.registerCurrentToken() } returns DeviceRegistrationOutcome.RETRY
        assertEquals(ListenableWorker.Result.retry(), worker().doWork())
    }

    @Test fun `permanent rejection fails work`() = runTest {
        coEvery { registrar.registerCurrentToken() } returns DeviceRegistrationOutcome.REJECTED
        assertEquals(ListenableWorker.Result.failure(), worker().doWork())
    }

    @Test fun `signed out safely finishes without repeated anonymous attempts`() = runTest {
        coEvery { registrar.registerCurrentToken() } returns DeviceRegistrationOutcome.SIGNED_OUT
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
    }

    @Test fun `obsolete account attempt finishes`() = runTest {
        coEvery { registrar.registerCurrentToken() } returns DeviceRegistrationOutcome.SESSION_CHANGED
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
    }

    @Test fun `worker cancellation is not converted into success or retry`() = runTest {
        coEvery { registrar.registerCurrentToken() } throws CancellationException("stopped")
        try {
            worker().doWork()
            fail("Cancellation was swallowed")
        } catch (_: CancellationException) { }
    }

    @Test fun `request waits for network and backs off exponentially without storing credentials`() {
        val request = RegisterDeviceWorker.request()
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, request.workSpec.backoffPolicy)
        assertEquals(30_000L, request.workSpec.backoffDelayDuration)
        assertTrue(request.workSpec.input.keyValueMap.isEmpty())
        assertTrue(request.tags.contains(RegisterDeviceWorker.WORK_NAME))
    }

    @Test fun `sign in and token refresh replace the same unique job`() {
        val manager = mockk<WorkManager>(relaxed = true)
        mockkStatic(WorkManager::class)
        try {
            every { WorkManager.getInstance(context) } returns manager
            RegisterDeviceWorker.enqueue(context)
            verify(exactly = 1) {
                manager.enqueueUniqueWork(RegisterDeviceWorker.WORK_NAME, ExistingWorkPolicy.REPLACE, any<OneTimeWorkRequest>())
            }
        } finally { unmockkStatic(WorkManager::class) }
    }

    @Test fun `startup keeps pending work and its retry backoff`() {
        val manager = mockk<WorkManager>(relaxed = true)
        mockkStatic(WorkManager::class)
        try {
            every { WorkManager.getInstance(context) } returns manager
            RegisterDeviceWorker.enqueue(context, replaceExisting = false)
            verify(exactly = 1) {
                manager.enqueueUniqueWork(RegisterDeviceWorker.WORK_NAME, ExistingWorkPolicy.KEEP, any<OneTimeWorkRequest>())
            }
        } finally { unmockkStatic(WorkManager::class) }
    }

    @Test fun `registrar sign out cancels the unique job`() {
        val manager = mockk<WorkManager>(relaxed = true)
        mockkStatic(WorkManager::class)
        try {
            every { WorkManager.getInstance(context) } returns manager
            val realRegistrar = DeviceRegistrar(context, mockk(), mockk(), mockk(), mockk())
            realRegistrar.cancelRegistration()
            verify(exactly = 1) { manager.cancelUniqueWork(RegisterDeviceWorker.WORK_NAME) }
        } finally { unmockkStatic(WorkManager::class) }
    }
}
