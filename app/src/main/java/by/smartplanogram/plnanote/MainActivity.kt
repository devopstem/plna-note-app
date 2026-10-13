package by.smartplanogram.plnanote

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import by.smartplanogram.plnanote.databinding.ActivityMainBinding
import by.smartplanogram.plnanote.ui.home.HomeFragment
import by.smartplanogram.plnanote.ui.lists.ListsFragment
import by.smartplanogram.plnanote.ui.map.MapFragment
import by.smartplanogram.plnanote.ui.profile.ProfileFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> open(HomeFragment())
                R.id.nav_lists -> open(ListsFragment())
                R.id.nav_map -> openMap()
                R.id.nav_profile -> open(ProfileFragment())
                else -> return@setOnItemSelectedListener false
            }
            true
        }

        if (savedInstanceState == null) {
            if (intent.getStringExtra(MapFragment.EXTRA_ROUTE_LIST_ID) != null) {
                binding.bottomNav.selectedItemId = R.id.nav_map
            } else {
                binding.bottomNav.selectedItemId = R.id.nav_home
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getStringExtra(MapFragment.EXTRA_ROUTE_LIST_ID) != null) {
            if (binding.bottomNav.selectedItemId == R.id.nav_map) {
                openMap() // reselect не вызывает listener — открываем вручную
            } else {
                binding.bottomNav.selectedItemId = R.id.nav_map
            }
        }
    }

    fun openTab(itemId: Int) {
        binding.bottomNav.selectedItemId = itemId
    }

    private fun openMap() {
        val listId = intent.getStringExtra(MapFragment.EXTRA_ROUTE_LIST_ID)
        val fragment = if (listId != null) MapFragment.withRoute(listId) else MapFragment()
        open(fragment)
    }

    private fun open(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
