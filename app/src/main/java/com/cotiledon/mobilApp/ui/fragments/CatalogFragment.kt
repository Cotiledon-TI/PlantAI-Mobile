package com.cotiledon.mobilApp.ui.fragments

import com.cotiledon.mobilApp.ui.menus.PlantFiltersBottomSheet
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cotiledon.mobilApp.R
import com.cotiledon.mobilApp.ui.activities.MainContainerActivity
import com.cotiledon.mobilApp.ui.adapters.PlantRecyclerViewAdapter
import com.cotiledon.mobilApp.ui.dataClasses.cart.CartPlant
import com.cotiledon.mobilApp.ui.dataClasses.plant.Plant
import com.cotiledon.mobilApp.ui.dataClasses.plant.PlantResponse
import com.cotiledon.mobilApp.ui.managers.CartStorageManager
import com.cotiledon.mobilApp.ui.backend.catalog.RetrofitCatalogClient
import com.cotiledon.mobilApp.ui.dataClasses.catalog.PlantFilterParams
import com.cotiledon.mobilApp.ui.fragments.base.SearchableFragment
import com.cotiledon.mobilApp.ui.helpers.SearchBarHelper
import com.cotiledon.mobilApp.ui.managers.TokenManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.IOException
import retrofit2.HttpException


class CatalogFragment : SearchableFragment(), PlantFiltersBottomSheet.FilterListener {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PlantRecyclerViewAdapter
    private lateinit var cartManager: CartStorageManager
    private lateinit var backButton: ImageView
    private lateinit var filterButton: Button
    private lateinit var sortButton: MaterialButton
    private lateinit var searchBarHelper: SearchBarHelper

    private var currentPage = 1
    private val pageSize = 10
    private var isLoading = false
    private var hasMoreItems = true
    private var currentSearchQuery: String = ""
    private var isSearchMode = false
    //Agregar un job para la busqueda
    private var searchJob: Job? = null

    //Guardado de filtros a nivel del fragment
    private var currentFilters: PlantFilterParams? = null
    private var currentSortParams: PlantFilterParams? = null
    private var currentPlants = mutableListOf<Plant>()

    private var popupWindow: PopupWindow? = null

    //Se agrega esta función para cuando se navega desde la IA y se muestra el popup
    private fun showAIExplanationPopup(explanation: String) {
        val inflater = LayoutInflater.from(requireContext())
        val popupView = inflater.inflate(R.layout.ai_explanation_popup, null)

        //Inicializar popup
        popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 16f
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        //Agregar texto generado por IA
        popupView.findViewById<TextView>(R.id.ai_explanation_text).text = explanation

        //Botton de dismiss
        popupView.findViewById<Button>(R.id.dismiss_button).setOnClickListener {
            popupWindow?.dismiss()
        }

        //Centrar el popup en la pantalla
        val rootView = requireActivity().window.decorView.findViewById<View>(android.R.id.content)
        popupWindow?.showAtLocation(rootView, Gravity.CENTER, 0, 0)

        //Agregar transparencia del fondo del popup
        val dimBackground = ColorDrawable(Color.BLACK)
        dimBackground.alpha = 120 //0 a 255
        popupWindow?.setBackgroundDrawable(dimBackground)
    }

    private sealed class DisplayMode {
        data object Catalog : DisplayMode()
        data class Search(val query: String) : DisplayMode()
    }
    private var currentMode: DisplayMode = DisplayMode.Catalog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_catalog, container, false)

        //Contruir PlantFilterParams a partir de los argumentos (para aplicar filtros en caso de
        //que se navegue desde la IA)
        arguments?.let { args ->
            if (args.containsKey("environment")) {  //Checkear si se recibieron argumentos
                currentFilters = PlantFilterParams(
                    environment = args.getInt("environment"),
                    petFriendly = args.getBoolean("pet_friendly"),
                    lighting = args.getInt("lighting"),
                    temperatureTolerance = args.getInt("temperature"),
                    irrigationType = args.getInt("irrigation"),
                    size = args.getString("size")

                )

                //Mostrar el popup
                args.getString("ai_explanation")?.let { explanation ->
                    view.post {
                        showAIExplanationPopup(explanation)
                    }
                }
            }
        }

        return view
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        setupRecyclerView()
        setupClickListeners()
        setupSearch()

    }

    override fun onResume() {
        super.onResume()
        resetLoadingState()
        loadPlants()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun resetLoadingState() {
        currentPage = 1
        isLoading = false
        hasMoreItems = true
        currentPlants.clear()
        adapter.notifyDataSetChanged()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        val tokenManager = TokenManager(requireContext())

        cartManager = CartStorageManager(
            context = requireContext(),
            tokenManager
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        //Cancelar cualquier busqueda en curso
        searchJob?.cancel()
        popupWindow?.dismiss()
        popupWindow = null
    }

    private fun initializeViews(view: View) {
        recyclerView = view.findViewById(R.id.rv_products)
        backButton = view.findViewById(R.id.btn_back)
        filterButton = view.findViewById(R.id.btn_filter)
        sortButton = view.findViewById(R.id.btn_sort)
    }


    private fun setupRecyclerView() {
        adapter = PlantRecyclerViewAdapter(
            initialPlants = mutableListOf(),
            onItemClick = {   plant -> navigateToProductDetail(plant)},
            onAddToCartClick = { plant -> handleAddToCart(plant) }
        )

        val gridLayoutManager = GridLayoutManager(context, 2).apply {
            //Posicionar el loading en el ancho completo de las dos columnas
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return when (adapter.getItemViewType(position)) {
                        PlantRecyclerViewAdapter.VIEW_TYPE_LOADING -> 2  //Full width para carga
                        else -> 1  //Solo una columna para items
                    }
                }
            }
        }

        recyclerView.apply {
            layoutManager = gridLayoutManager
            adapter = this@CatalogFragment.adapter

            //Scroll listener para paginación
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    val layoutManager = recyclerView.layoutManager as GridLayoutManager
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    if (!isLoading && hasMoreItems) {
                        if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                            && firstVisibleItemPosition >= 0
                        ) {
                            loadPlants()
                        }
                    }
                }
            })
        }
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        filterButton.setOnClickListener {
            showFilterBottomSheet()
        }

        sortButton.setOnClickListener {
            showSortPopupMenu()
            animateIconRotation(sortButton, R.drawable.sort_icon_up)
        }
    }

    @SuppressLint("Recycle", "ObjectAnimatorBinding")
    private fun animateIconRotation(button: MaterialButton, newIconResId: Int) {
        val currentIcon = button.icon

        val rotateOut = ObjectAnimator.ofFloat(currentIcon, "rotationZ",0f, 180f)
        rotateOut.duration = 100

        rotateOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                val newIcon : Drawable? = ContextCompat.getDrawable(button.context, newIconResId)
                button.icon = newIcon
            }
        })

        rotateOut.start()
    }

    private fun setupSearch() {
        //Encontrar el root que contiene el search bar
        //(Se utiliza el searchbar integrado en el layout)
        val searchBarView = view?.findViewById<View>(R.id.searchbar_section)

        //Solo inicializar si se encontro el view
        searchBarView?.let { rootView ->
            //Crear la instancia del SearchBarHelper con:
            // - rootView: La vista raiz que contenga los elementos del search bar
            // - this: El fragmento implementando el searchBar
            searchBarHelper = SearchBarHelper(rootView, this)

            //Settear un hint apropiado
            searchBarHelper.setHint(getString(R.string.search_hint))
        }
    }

    //Sobreescribir el hint de busqueda
    override fun getSearchHint(): String {
        return getString(R.string.search_catalog_hint)
    }

    //Modificar el comportamiento de busqueda al presionar enter
    override fun onQueryTextSubmit(query: String) {
        if (query.isNotEmpty()) {
            //Cambiar a modo de busqueda y refrescar la vista
            currentMode = DisplayMode.Search(query)
            refreshDisplay()
        }
    }

    override fun onQueryTextChange(newText: String) {
        //Cancelar cualquier busqueda en curso
        searchJob?.cancel()

        if (newText.isEmpty()) {
            //Cambiar a modo de catalogo
            currentMode = DisplayMode.Catalog

            //Limpiar la lista de plantas
            adapter.clearPlants()

            //Resetear paginacion
            currentPage = 1
            isLoading = false
            hasMoreItems = true

            //Recargar las plantas con los filtros actuales
            loadPlants()
        } else {
            //Iniciar nueva busqueda
            searchJob = lifecycleScope.launch(Dispatchers.Main) {
                try {
                    delay(300) // Esperar 300ms antes de iniciar la busqueda
                    currentMode = DisplayMode.Search(newText)

                    adapter.clearPlants()
                    currentPage = 1
                    isLoading = false
                    hasMoreItems = true

                    loadPlants()
                } catch (e: CancellationException) {
                    //Hacer nada si la busqueda se cancela
                }
            }
        }
    }

    //Nuevo metodo para refrescar la vista
    private fun refreshDisplay() {
        //Cancelar cualquier busqueda en curso
        searchJob?.cancel()

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                adapter.clearPlants()
                currentPage = 1
                isLoading = false
                hasMoreItems = true

                loadPlants()
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }



    override fun onFiltersApplied(filterParams: PlantFilterParams) {
        //Guardar nuevos filtros
        currentFilters = filterParams

        //Cambiar a modo de catalogo si estaba en modo de busqueda
        currentMode = DisplayMode.Catalog

        //Limpiar la lista de plantas
        adapter.clearPlants()
        currentPage = 1
        isLoading = false
        hasMoreItems = true

        loadPlants()
    }

    override fun getCurrentFilters(): PlantFilterParams? {
        return currentFilters
    }

    private fun showFilterBottomSheet() {
        val location = IntArray(2)
        filterButton.getLocationOnScreen(location)
        val filterButtonY = location[1] + filterButton.height

        val bottomSheet = PlantFiltersBottomSheet.newInstance(filterButtonY)

        val maxPrice = currentPlants.maxOfOrNull { it.precio.toFloat() } ?: 100000f
        bottomSheet.setMaxProductPrice(maxPrice)

        //Settear el target fragment para el bottom sheet de filtros
        bottomSheet.setTargetFragment(this, 0)
        bottomSheet.show(parentFragmentManager, "filters")


    }

    @SuppressLint("NotifyDataSetChanged")
    private fun applyFilters(newFilters: PlantFilterParams) {
        currentPage = 1
        isLoading = false
        hasMoreItems = true

        currentPlants.clear()
        adapter.notifyDataSetChanged()

        currentFilters = newFilters

        loadPlants()
    }

    @SuppressLint("InflateParams")
    private fun showSortPopupMenu() {
        val inflater = requireContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView = inflater.inflate(R.layout.sort_popup_menu, null)

        val sortPopupWindow = PopupWindow(
            popupView,
            205.dpToPx(requireContext()),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 16f
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            setOnDismissListener {
                animateIconRotation(sortButton, R.drawable.sort_icon)
                dismiss()
            }
        }

        popupView.apply {
            findViewById<TextView>(R.id.popular_products_option).setOnClickListener {
                applySorting(PlantFilterParams.ORDER_BY_RATING, PlantFilterParams.ORDER_DESC)
                sortPopupWindow.dismiss()
            }

            findViewById<TextView>(R.id.most_sold_option).setOnClickListener {
                applySorting(PlantFilterParams.ORDER_BY_UNITS_SOLD, PlantFilterParams.ORDER_DESC)
                sortPopupWindow.dismiss()
            }

            findViewById<TextView>(R.id.price_high_to_low_option).setOnClickListener {
                applySorting(PlantFilterParams.ORDER_BY_PRICE, PlantFilterParams.ORDER_DESC)
                sortPopupWindow.dismiss()
            }

            findViewById<TextView>(R.id.price_low_to_high_option).setOnClickListener {
                applySorting(PlantFilterParams.ORDER_BY_PRICE, PlantFilterParams.ORDER_ASC)
                sortPopupWindow.dismiss()
            }
        }

        val location = IntArray(2)
        sortButton.getLocationOnScreen(location)

        sortPopupWindow.showAsDropDown(
            sortButton,
            -65,
            0,
            Gravity.START
        )

    }


    @SuppressLint("NotifyDataSetChanged")
    private fun applySorting(orderBy: String, order: String) {
        val newParams = currentFilters?.copy() ?: PlantFilterParams()
        newParams.apply {
            this.orderBy = orderBy
            this.order = order
        }

        //Resetear y recargar con nuevos parámetros
        currentFilters = newParams

        adapter.clearPlants()

        currentPage = 1
        isLoading = false
        hasMoreItems = true

        //Cargar nueva data con los nuevos parámetros
        loadPlants()
    }



    private fun Int.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    //Modificar loadPlants para manejo de corrutinas
    private fun loadPlants() {
        if (isLoading || !hasMoreItems) return
        isLoading = true
        adapter.showLoading()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val retrofitClient = RetrofitCatalogClient.createCatalogClient()
                val response: PlantResponse<Plant> = when (val mode = currentMode) {
                    is DisplayMode.Search -> {
                        //Para modo de busqueda, no aplicamos filtros
                        retrofitClient.searchPlants(
                            page = currentPage,
                            pageSize = pageSize,
                            searchQuery = mode.query
                        )
                    }
                    DisplayMode.Catalog -> {
                        //Para modo de catálogo, aplicamos los filtros
                        retrofitClient.getPlants(
                            page = currentPage,
                            pageSize = pageSize,
                            filterParams = currentFilters
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    hasMoreItems = currentPage * pageSize < response.totalItems

                    if (response.data.isNotEmpty()) {
                        adapter.hideLoading()
                        adapter.updatePlants(response.data)
                        currentPage++
                    } else {
                        showEmptyState(currentMode)
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    handleError(e)
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    adapter.hideLoading()
                }
            }
        }
    }

    private fun handleError(error: Exception) {
        Log.e(TAG, "Error loading plants", error)

        activity?.runOnUiThread {
            //Esconder el indicador de carga
            adapter.hideLoading()

            //Mostrar un mensaje de error
            val errorMessage = when (error) {
                is IOException -> getString(R.string.error_network)
                is HttpException -> getString(R.string.error_server)
                else -> getString(R.string.error_loading_plants)
            }

            Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
        }
    }

    //Metodo helper para mostrar el estado vacio
    private fun showEmptyState(mode: DisplayMode) {
        val message = when (mode) {
            is DisplayMode.Search -> getString(R.string.no_search_results, mode.query)
            DisplayMode.Catalog -> getString(R.string.no_catalog_items)
        }

        activity?.runOnUiThread {
            adapter.hideLoading()

            //Mostrar mensaje de empty state
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun handleAddToCart(plant: Plant) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (plant.stock > 0) {
                    val cartPlant = plant.imagenes.firstOrNull()?.ruta?.let {
                        CartPlant(
                            plantName = plant.nombre,
                            plantPrice = plant.precio.toString(),
                            plantId = plant.id.toString(),
                            plantStock = plant.stock.toString(),
                            plantQuantity = 1,
                            plantImage = it.drop(1)
                        )
                    }

                    cartPlant?.let {
                        cartManager.saveProductToCart(it)

                        activity?.runOnUiThread {
                            Toast.makeText(
                                requireContext(),
                                "${plant.nombre} añadido al carrito",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        (activity as? MainContainerActivity)?.updateCartBadge()
                    }

                } else {
                    activity?.runOnUiThread {
                        Toast.makeText(
                            requireContext(),
                            "Lo sentimos, ${plant.nombre} no tiene más stock",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            } catch (e: Exception) {
                Log.e("CatalogFragment", "Error al añadir producto al carrito", e)
                activity?.runOnUiThread {
                    Toast.makeText(
                        requireContext(),
                        "No se pudo añadir el producto al carrito",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    //Función para navegar a la vista de detalle
    private fun navigateToProductDetail(plant: Plant) {

        val productDetailFragment = ProductDetailFragment.newInstance(plant)

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, productDetailFragment)
            .addToBackStack(null)
            .commit()
    }

    //Fragment factory
    companion object {
        fun newInstance(args: Bundle) : CatalogFragment {
            return CatalogFragment().apply {
                arguments = args
            }
        }
        fun createArguments(categoryId: String): Bundle {
            return Bundle().apply {
                putString("category_id", categoryId)
            }
        }
        private const val TAG = "CatalogFragment"
    }
}