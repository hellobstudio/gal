package hu.egyszeru.galeria

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import hu.egyszeru.galeria.ui.AlbumScreen
import hu.egyszeru.galeria.ui.AlbumsScreen
import hu.egyszeru.galeria.ui.GalleryViewModel
import hu.egyszeru.galeria.ui.PermissionScreen
import hu.egyszeru.galeria.ui.ViewerScreen
import hu.egyszeru.galeria.ui.theme.GaleriaTheme
import hu.egyszeru.galeria.util.MediaAccess
import hu.egyszeru.galeria.util.mediaAccess
import hu.egyszeru.galeria.util.mediaPermissions

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GaleriaTheme { GalleryRoot() } }
    }
}

/** Csak akkor lép vissza, ha ez a képernyő még az aktuális (dupla visszalépés ellen). */
private fun NavController.back(entry: NavBackStackEntry) {
    if (currentBackStackEntry?.id == entry.id) popBackStack()
}

@Composable
private fun GalleryRoot() {
    val vm: GalleryViewModel = viewModel()
    val context = LocalContext.current
    var access by remember { mutableStateOf(mediaAccess(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        access = mediaAccess(context)
        if (access != MediaAccess.NONE) vm.start()
    }
    val systemDialogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> vm.onRequestResult(result.resultCode == Activity.RESULT_OK) }

    // Visszatéréskor frissítünk (pl. engedélyt adtak a beállításokban, vagy új fotó készült)
    LifecycleResumeEffect(Unit) {
        access = mediaAccess(context)
        if (access != MediaAccess.NONE) vm.start()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        vm.requests.collect { sender ->
            systemDialogLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }
    LaunchedEffect(Unit) {
        vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    if (access == MediaAccess.NONE) {
        PermissionScreen(onRequest = { permissionLauncher.launch(mediaPermissions()) })
        return
    }

    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "albums") {
        composable("albums") {
            AlbumsScreen(
                vm = vm,
                partialAccess = access == MediaAccess.PARTIAL,
                onRequestAccess = { permissionLauncher.launch(mediaPermissions()) },
                onOpen = { key -> nav.navigate("album/${Uri.encode(key)}") },
            )
        }
        composable("album/{key}") { entry ->
            val key = entry.arguments?.getString("key") ?: return@composable
            AlbumScreen(
                vm = vm,
                albumKey = key,
                onBack = { nav.back(entry) },
                onOpenItem = { id -> nav.navigate("viewer/${Uri.encode(key)}/$id") },
            )
        }
        composable(
            "viewer/{key}/{id}",
            arguments = listOf(
                navArgument("key") { type = NavType.StringType },
                navArgument("id") { type = NavType.LongType },
            ),
        ) { entry ->
            ViewerScreen(
                vm = vm,
                albumKey = entry.arguments?.getString("key") ?: "",
                startId = entry.arguments?.getLong("id") ?: 0L,
                onBack = { nav.back(entry) },
            )
        }
    }
}
