package com.nxteam.nxstore.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nxteam.nxstore.model.AppItem
import com.nxteam.nxstore.ui.detail.DetailScreen
import com.nxteam.nxstore.ui.home.HomeScreen
import com.nxteam.nxstore.ui.search.SearchScreen

private sealed class Dest(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Dest("home", "Home", Icons.Default.Home)
    data object Search : Dest("search", "Search", Icons.Default.Search)
}

private val bottomDests = listOf(Dest.Home, Dest.Search)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NxStoreApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val isDetail = currentRoute == "detail"

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(if (isDetail) "Details" else "NX Store") },
                navigationIcon = {
                    if (isDetail) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors()
            )
        },
        bottomBar = {
            if (!isDetail) {
                NavigationBar {
                    val current = backStack?.destination
                    bottomDests.forEach { dest ->
                        val selected = current?.hierarchy?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        val open: (AppItem) -> Unit = { item ->
            NavSelection.current = item
            navController.navigate("detail")
        }
        NavHost(navController = navController, startDestination = Dest.Home.route) {
            composable(Dest.Home.route) { HomeScreen(contentPadding = padding, onOpen = open) }
            composable(Dest.Search.route) { SearchScreen(contentPadding = padding, onOpen = open) }
            composable("detail") { DetailScreen(contentPadding = padding) }
        }
    }
}
