package com.tvbox.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tvbox.app.data.DefaultMovieRepository
import com.tvbox.app.data.SharedHistoryRepository
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tvbox.app.ui.TvBoxApp
import com.tvbox.app.ui.TvBoxViewModel
import com.tvbox.app.ui.theme.TVBoxTheme

class MainActivity : ComponentActivity() {
    private val viewModel: TvBoxViewModel by viewModels {
        TvBoxViewModelFactory(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TVBoxTheme {
                val state = viewModel.state.collectAsStateWithLifecycle().value
                BackHandler(enabled = state.screen != com.tvbox.app.ui.TvScreen.Home) {
                    viewModel.goBack()
                }
                TvBoxApp(state = state, actions = viewModel)
            }
        }
    }
}

private class TvBoxViewModelFactory(
    private val activity: ComponentActivity,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return TvBoxViewModel(
            repository = DefaultMovieRepository(),
            historyRepository = SharedHistoryRepository(activity.applicationContext),
        ) as T
    }
}
