package com.github.ashutoshgngwr.noice.fragment

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.RetryTestRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SupportDevelopmentFragmentTest {

  @Rule
  @JvmField
  val retryTestRule = RetryTestRule(5)

  private lateinit var fragmentScenario: FragmentScenario<SupportDevelopmentFragment>

  @Before
  fun setup() {
    fragmentScenario = launchFragmentInContainer(null, R.style.Theme_App)
  }

  @Test
  fun testShareWithFriendsButton() {
    Intents.init()
    onView(withId(R.id.button_share)).perform(scrollTo(), click())

    Intents.intended(
      IntentMatchers.filterEquals(
        Intent(
          Intent.ACTION_VIEW, Uri.parse(
            ApplicationProvider.getApplicationContext<Context>()
              .getString(R.string.support_development__share_url)
          )
        )
      )
    )

    Intents.release()
  }
}