package altermarkive.guardian

import altermarkive.guardian.databinding.MainBinding
import android.app.AlertDialog
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.google.android.material.bottomnavigation.BottomNavigationView

class Main : AppCompatActivity() {
    fun checkForSetName() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val name = sharedPreferences.getString("name", "")
        if (name.isNullOrEmpty()) {
            // show dialog
            val builder = AlertDialog.Builder(this)
            builder
                .setTitle("Set your name")

            val input = EditText(this)
            builder.setView(input)

            builder.setPositiveButton("OK") { dialog, which ->
                val name = input.text.toString()
                sharedPreferences.edit().putString("name", name).apply()
            }

            builder.setNegativeButton("Cancel") { dialog, which ->
                dialog.cancel()
            }

            builder.show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        Detector.instance(this)
        val binding = MainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val navView: BottomNavigationView = binding.navigation
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.main) as NavHostFragment
        val navController = navHostFragment.navController
        val appBarConfiguration =
            AppBarConfiguration(setOf(R.id.about, R.id.signals, R.id.settings))
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        checkForSetName()
        Guardian.initiate(this)
    }
}
