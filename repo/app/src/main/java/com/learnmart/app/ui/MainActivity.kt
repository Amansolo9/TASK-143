package com.learnmart.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.learnmart.app.data.local.SeedDataBootstrapper
import com.learnmart.app.ui.navigation.LearnMartNavGraph
import com.learnmart.app.ui.theme.LearnMartTheme
import com.learnmart.app.worker.WorkScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var seedDataBootstrapper: SeedDataBootstrapper

    @Inject
    lateinit var workScheduler: WorkScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Seed data on first launch and schedule background work
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                seedDataBootstrapper.seedIfEmpty()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Seed failed: ${e.message}", e)
            }
        }
        try {
            workScheduler.scheduleAllPeriodicWork()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "WorkScheduler failed: ${e.message}", e)
        }

        setContent {
            LearnMartTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    LearnMartNavGraph(navController = navController)
                }
            }
        }
    }
}
