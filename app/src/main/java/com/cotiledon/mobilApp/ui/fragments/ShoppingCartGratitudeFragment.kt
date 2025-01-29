package com.cotiledon.mobilApp.ui.fragments

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.cotiledon.mobilApp.R
import com.cotiledon.mobilApp.ui.activities.MainContainerActivity
import com.cotiledon.mobilApp.ui.managers.CartStorageManager
import com.cotiledon.mobilApp.ui.managers.OrderManager
import com.cotiledon.mobilApp.ui.managers.TokenManager
import com.google.android.material.bottomnavigation.BottomNavigationView

class ShoppingCartGratitudeFragment : Fragment() {
    private lateinit var continueShoppingButton: Button
    private lateinit var backButton: ImageButton

    private val tokenManager: TokenManager by lazy {
        TokenManager(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_shopping_cart_gratitude, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        continueShoppingButton = view.findViewById(R.id.button_continue)
        setupContinueShoppingButton()

        backButton = view.findViewById(R.id.btn_back)
        backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupContinueShoppingButton() {
        continueShoppingButton.setOnClickListener {

            if (tokenManager.isVisitor()) {
                //Si son visitantes, se debe cerrar la sesión
                handleVisitorLogout()
            }

            (activity as? MainContainerActivity)?.let { mainActivity ->
                val bottomNav = mainActivity.findViewById<BottomNavigationView>(R.id.bottom_navigation)

                //Limpiar el backstack completo
                parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)

                //Ir al fragmento de Home
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, HomeFragment())
                    .commit()

                //Actualizar el item del nav_menu
                bottomNav.selectedItemId = R.id.nav_home
            }
        }
    }

    private fun handleVisitorLogout() {
        try {
            tokenManager.clearAuthData()
            OrderManager.clearVisitorDetails()

            val cartManager = CartStorageManager(requireContext(), tokenManager)
            cartManager.clearCart()

            //Debemos asegurarnos de limpiar cualquier storage local
            context?.getSharedPreferences("visitor_prefs", Context.MODE_PRIVATE)
                ?.edit()
                ?.clear()
                ?.apply()

        } catch (e: Exception) {
            Log.e("ShoppingCartGratitude", "Error clearing visitor credentials", e)
            //Incluso si hay un error, se debe cerrar la sesión ya que esta es la ultima pantalla
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    parentFragmentManager.popBackStack()
                }
            }
        )
    }

    companion object {
        fun newInstance() = ShoppingCartGratitudeFragment()
    }
}