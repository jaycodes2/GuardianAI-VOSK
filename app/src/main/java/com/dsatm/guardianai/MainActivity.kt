package com.dsatm.guardianai

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.fragment.app.FragmentActivity
import com.dsatm.audio_redaction.ui.AudioRedactionScreen
import com.dsatm.guardianai.security.SecurityManager
import com.dsatm.guardianai.ui.theme.GuardianAITheme
import com.dsatm.image_redaction.ui.ImageRedactionScreen
import com.dsatm.text_redaction.ui.TextRedactionScreen
import com.dsatm.guardianai.ui.screens.CryptoDemoScreen

// Navigation destinations
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Image : Screen("image_screen", "Image", Icons.Default.Image)
    object Audio : Screen("audio_screen", "Audio", Icons.Default.Mic)
    object Text : Screen("text_screen", "Text", Icons.Default.TextFields)
    object Crypto : Screen("crypto_screen", "Crypto", Icons.Default.Lock)
}

class MainActivity : FragmentActivity() {

    private lateinit var securityManager: SecurityManager
    private var isAuthenticated by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        securityManager = SecurityManager(this)

        if (securityManager.isBiometricReady()) {
            performBiometricAuth()
        } else {
            isAuthenticated = true
        }

        setContent {
            GuardianAITheme {
                if (isAuthenticated) {
                    MainScreen()
                } else {
                    SplashScreen()
                }
            }
        }
    }

    private fun performBiometricAuth() {
        securityManager.authenticateForAppAccess(
            this,
            onSuccess = {
                isAuthenticated = true
                Toast.makeText(this, "Authentication successful!", Toast.LENGTH_SHORT).show()
            },
            onFailure = { errorMessage ->
                isAuthenticated = false
                Toast.makeText(this, "Authentication failed: $errorMessage", Toast.LENGTH_LONG).show()
                finish()
            }
        )
    }
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { BottomNavigationBar(navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Image.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Image.route) { ImageRedactionScreen() }
            composable(Screen.Audio.route) { AudioRedactionScreen() } // ✅ no manager param
            composable(Screen.Text.route) { TextRedactionScreen() }
            composable(Screen.Crypto.route) { CryptoDemoScreen(activity = LocalContext.current as FragmentActivity) }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val items = listOf(Screen.Image, Screen.Audio, Screen.Text, Screen.Crypto)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar {
        items.forEach { screen ->
            val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
            NavigationBarItem(
                icon = { Icon(imageVector = screen.icon, contentDescription = screen.label) },
                label = { Text(screen.label) },
                selected = isSelected,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        CircularProgressIndicator()
        Text(text = "Authenticating...", modifier = Modifier.padding(top = 80.dp))
    }
}
