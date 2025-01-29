package com.cotiledon.mobilApp.ui.activities

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.cotiledon.mobilApp.R
import androidx.lifecycle.lifecycleScope
import com.cotiledon.mobilApp.ui.backend.user.RetrofitUserClient
import com.cotiledon.mobilApp.ui.fragments.CategoriesFragment
import com.cotiledon.mobilApp.ui.fragments.HomeFragment
import com.cotiledon.mobilApp.ui.fragments.ProfileUserFragment
import com.cotiledon.mobilApp.ui.fragments.ShoppingCartFragment
import com.cotiledon.mobilApp.ui.fragments.SignInPreviousFragment
import com.cotiledon.mobilApp.ui.managers.CartStorageManager
import com.cotiledon.mobilApp.ui.managers.TokenManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

class MainContainerActivity : BaseActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var cartManager: CartStorageManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //Esconder la barra de notificaciones
        WindowCompat.setDecorFitsSystemWindows(window, false)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main_container)
        supportActionBar?.hide()

        //Inicializar el botom navigation
        bottomNavigationView = findViewById(R.id.bottom_navigation)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, windowInsets ->
            val statusBars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(0, statusBars.top, 0, 0)
            windowInsets
        }

        //Inicialiar el cart manager
        cartManager = CartStorageManager(this, TokenManager(this))

        //Inicializar el botom navigation
        bottomNavigationView = findViewById(R.id.bottom_navigation)

        //Inicializar el cart badge
        updateCartBadge()

        //Inicializar siempre la vista con el HomeFragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
        }
        //Manejo de la navegación
        setupNavigation()
    }

    private fun setupNavigation() {
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, HomeFragment())
                        .commit()
                    true
                }
                R.id.nav_profile -> {
                    handleProfileNavigation()
                    true
                }
                R.id.nav_cart -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, ShoppingCartFragment())
                        .commit()
                    true
                }
                R.id.nav_menu -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, CategoriesFragment())
                        .commit()
                    true
                }
                else -> false
            }
        }
    }

    //Función para actualizar el cart badge
     fun updateCartBadge() {
        val itemCount = cartManager.cartItemsCount()
        val cartItem = bottomNavigationView.menu.findItem(R.id.nav_cart)

        if (itemCount > 0) {
            val badge = bottomNavigationView.getOrCreateBadge(cartItem.itemId)
            badge.apply {
                number = itemCount
                isVisible = true
                backgroundColor = ContextCompat.getColor(this@MainContainerActivity, R.color.badge_red)
            }
        } else {
            bottomNavigationView.removeBadge(cartItem.itemId)
        }
    }

    //Función para manejar la navegación del perfil
    private fun handleProfileNavigation() {
        lifecycleScope.launch {
            try {
                val token = tokenManager.getToken()
                Log.d("TokenManager", "Token obtenido: $token")
                if (token != null) {
                    //Intentamos obtener el perfil
                    val userClient = RetrofitUserClient.createUserClient(TokenManager(this@MainContainerActivity))
                    val response = userClient.getUserProfile()

                    //Si se encuentra se muestra el fragmento de perfil
                    if (response.isSuccessful) {
                        response.body()?.let { profile ->
                            if (profile.rol != "Visitante") {
                                supportFragmentManager.beginTransaction()
                                    .replace(R.id.fragment_container, ProfileUserFragment.newInstance())
                                    .commit()
                            } else {
                                //Sino se muestra el de login
                                showLoginScreen()
                            }
                        }
                    } else {
                        //Si existe un error, se muestra el login
                        showLoginScreen()
                    }
                } else {
                    //Si no hay token, se muestra el login
                    showLoginScreen()
                }
            } catch (e: Exception) {
                //Si ocurrio un error, se muestra el login
                showLoginScreen()
            }
        }
    }

    private fun showLoginScreen() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, SignInPreviousFragment())
            .commit()
    }

}