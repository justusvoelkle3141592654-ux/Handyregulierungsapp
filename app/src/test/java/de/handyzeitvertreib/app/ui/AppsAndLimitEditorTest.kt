package de.handyzeitvertreib.app.ui

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import de.handyzeitvertreib.app.R
import de.handyzeitvertreib.app.testing.TestHzvApplication
import de.handyzeitvertreib.app.testing.awaitCondition
import de.handyzeitvertreib.app.testing.awaitUi
import de.handyzeitvertreib.app.ui.apps.AppsRoute
import de.handyzeitvertreib.app.ui.apps.AppsViewModel
import de.handyzeitvertreib.app.ui.designsystem.HzvTheme
import de.handyzeitvertreib.app.ui.limits.AppLimitEditorRoute
import de.handyzeitvertreib.app.ui.limits.AppLimitEditorViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppsAndLimitEditorTest {
    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestHzvApplication>()
    private val context: Context = app

    @Test
    fun searchNarrowsTheAppList() {
        runBlocking { app.container.usageRepository.refresh() }
        val viewModel = AppsViewModel(app.container)
        compose.setContent { HzvTheme { AppsRoute(viewModel, onOpenApp = {}, contentPadding = PaddingValues()) } }
        compose.awaitUi { !viewModel.state.value.loading }
        assertThat(
            viewModel.state.value.items
                .map { it.label },
        ).contains("Video")
        compose.onNodeWithTag("app-search").performTextInput("cha")
        compose.awaitUi { viewModel.state.value.query == "cha" }
        assertThat(
            viewModel.state.value.items
                .map { it.label },
        ).containsExactly("Chat")
        compose.waitForIdle()
        compose.onNodeWithTag("apps-list").performScrollToNode(hasText("Chat"))
        compose.onNodeWithText("Chat").assertIsDisplayed()
        compose.onNodeWithText("Video").assertDoesNotExist()
    }

    @Test
    fun createThenEditLimit() {
        var done = 0
        val viewModel = AppLimitEditorViewModel(app.container, "com.example.chat")
        compose.setContent { HzvTheme { AppLimitEditorRoute(viewModel, onDone = { done++ }, contentPadding = PaddingValues()) } }
        compose.awaitUi { !viewModel.state.value.loading }
        compose.onNodeWithText(context.getString(R.string.limit_editor_new_title)).assertExists()
        compose.onNodeWithTag("duration-increase").performClick()
        compose.onNodeWithTag("duration-increase").performClick()
        compose.onNodeWithTag("duration-value").assertTextEquals(context.getString(R.string.duration_minutes, 40))
        compose.onNodeWithTag("limit-save").performScrollTo().performClick()
        compose.awaitUi { viewModel.state.value.saved }
        compose.awaitUi { done == 1 }
        val saved =
            runBlocking {
                app.container.limitRepository
                    .currentAppLimits()
                    .single()
            }
        assertThat(saved.dailyLimitMinutes).isEqualTo(40)
        assertThat(saved.enabled).isTrue()

        val editor = AppLimitEditorViewModel(app.container, "com.example.chat")
        awaitCondition { !editor.state.value.loading }
        assertThat(editor.state.value.existingId).isEqualTo(saved.id)
        editor.setMinutes(3)
        editor.setEnabled(false)
        editor.save()
        awaitCondition { editor.state.value.saved }
        val edited =
            runBlocking {
                app.container.limitRepository
                    .currentAppLimits()
                    .single()
            }
        assertThat(edited.dailyLimitMinutes).isEqualTo(5)
        assertThat(edited.enabled).isFalse()
    }

    @Test
    fun decreaseIsDisabledAtMinimum() {
        val viewModel = AppLimitEditorViewModel(app.container, "com.example.chat")
        viewModel.setMinutes(5)
        compose.setContent { HzvTheme { AppLimitEditorRoute(viewModel, onDone = {}, contentPadding = PaddingValues()) } }
        compose.awaitUi { !viewModel.state.value.loading }
        viewModel.setMinutes(5)
        compose.waitForIdle()
        compose.onNodeWithTag("duration-decrease").performClick()
        compose.onNodeWithTag("duration-value").assertTextEquals(context.getString(R.string.duration_minutes, 5))
    }
}
