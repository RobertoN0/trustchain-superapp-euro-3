package nl.tudelft.trustchain.eurotoken.utils

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

/**
 * Helper class to handle Bluetooth permissions
 */
class BluetoothPermissionHelper(private val fragment: Fragment) {

    /**
     * Get the list of required permissions based on Android version
     */
    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    /**
     * Check if all required permissions are granted
     */
    fun hasRequiredPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(fragment.requireContext(), it) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Request the necessary permissions
     */
    fun requestPermissions() {
        ActivityCompat.requestPermissions(
            fragment.requireActivity(),
            requiredPermissions,
            PERMISSION_REQUEST_CODE
        )
    }

    /**
     * Show a dialog explaining why we need permissions
     */
    fun showPermissionExplanationDialog() {
        AlertDialog.Builder(fragment.requireContext())
            .setTitle("Bluetooth Permissions Required")
            .setMessage("This app needs Bluetooth permissions to broadcast data to nearby devices.")
            .setPositiveButton("Grant") { _, _ ->
                requestPermissions()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Handle permission result
     * Call this method from onRequestPermissionsResult in the fragment
     */
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // All permissions granted
                return true
            } else {
                // Some permissions denied
                Toast.makeText(
                    fragment.requireContext(),
                    "Bluetooth broadcasting requires all permissions",
                    Toast.LENGTH_LONG
                ).show()

                // Check if we should show settings dialog
                if (!requiredPermissions.all {
                        ActivityCompat.shouldShowRequestPermissionRationale(fragment.requireActivity(), it)
                    }) {
                    // User checked "Don't ask again", show settings dialog
                    showSettingsDialog()
                }

                return false
            }
        }
        return false
    }

    /**
     * Show a dialog prompting the user to open app settings
     */
    private fun showSettingsDialog() {
        AlertDialog.Builder(fragment.requireContext())
            .setTitle("Permissions Required")
            .setMessage("Bluetooth broadcasting requires permissions that you have denied. Please enable them in app settings.")
            .setPositiveButton("Settings") { _, _ ->
                // Open app settings
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = android.net.Uri.fromParts("package", fragment.requireContext().packageName, null)
                intent.data = uri
                fragment.startActivity(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
    }
}
