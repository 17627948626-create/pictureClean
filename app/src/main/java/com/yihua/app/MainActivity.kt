package com.yihua.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.yihua.app.ui.navigation.AppNavigation
import com.yihua.app.ui.theme.YiHuaTheme

class MainActivity : ComponentActivity() {

    private val photoPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Compose screen re-checks permission on resume/recomposition.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YiHuaTheme {
                AppNavigation()
            }
        }
        requestPhotoPermissionsIfNeeded()
    }

    private fun requestPhotoPermissionsIfNeeded() {
        if (hasAnyPhotoAccess()) return
        photoPermissionLauncher.launch(requiredPhotoPermissions())
    }

    private fun hasAnyPhotoAccess(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            isGranted(Manifest.permission.READ_MEDIA_IMAGES) ||
                isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            isGranted(Manifest.permission.READ_MEDIA_IMAGES)
        else -> isGranted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun requiredPhotoPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
