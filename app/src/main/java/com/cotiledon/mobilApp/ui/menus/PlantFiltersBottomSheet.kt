package com.cotiledon.mobilApp.ui.menus

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioGroup
import android.widget.TextView
import com.cotiledon.mobilApp.R
import com.cotiledon.mobilApp.ui.dataClasses.catalog.PlantFilterParams
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.RangeSlider
import java.text.NumberFormat
import java.util.Locale

class PlantFiltersBottomSheet : BottomSheetDialogFragment() {

    //Interfaz para comunicar los campos de filtros
    interface FilterListener {
        fun onFiltersApplied(filterParams: PlantFilterParams)
        fun getCurrentFilters(): PlantFilterParams?
    }

    private var maxProductPrice: Float = 100000f  //Valor máximo por default
    private val minAllowedPrice: Float = 0f      //Valor mínimo siempre es 0
    private val minMaxPrice: Float = 1000f       //Valor mínimo permitido

    private var filterListener: FilterListener? = null
    private lateinit var currentFilters: PlantFilterParams

    private lateinit var budgetSlider: RangeSlider
    private lateinit var budgetValueText: TextView
    private lateinit var sizeGroup: RadioGroup
    private lateinit var petFriendlyCheckbox: CheckBox
    private lateinit var lightGroup: RadioGroup
    private lateinit var temperatureGroup: RadioGroup
    private lateinit var irrigationGroup: RadioGroup
    private lateinit var clearButton: Button
    private lateinit var applyButton: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.filter_bottom_sheet_menu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViews(view)

        setupBottomSheetBehavior()

        //Si ya hay filtros guardados, se cargan
        currentFilters = filterListener?.getCurrentFilters()?.copy() ?: PlantFilterParams()
        initializeFilterValues()
        setupFilterListeners()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        //Intentar diferentes maneras de obtener el listener
        filterListener = when {
            //Checkeamos si el fragmento padre implementa el listener
            parentFragment is FilterListener -> parentFragment as FilterListener
            //Si no, checkeamos si el activity implementa el listener
            context is FilterListener -> context
            //Si tmapoco, checkeamos si el fragmento objetivo implementa el listener
            targetFragment is FilterListener -> targetFragment as FilterListener
            else -> {
                //Si no hay ninguno de los anteriores, volvemos a intentar con el parent
                parentFragmentManager.fragments.firstOrNull { it is FilterListener } as? FilterListener
                    ?: throw RuntimeException("Ya sea el fragmento padre, la actividad o el fragmento " +
                            "objetivo debe implementar el listener de filtros.")
            }
        }
    }

    private fun initializeViews(view: View) {
        budgetSlider = view.findViewById(R.id.budgetSlider)
        budgetValueText = view.findViewById(R.id.budgetValue)
        sizeGroup = view.findViewById(R.id.sizeGroup)
        petFriendlyCheckbox = view.findViewById(R.id.petFriendlyCheckbox)
        lightGroup = view.findViewById(R.id.lightGroup)
        temperatureGroup = view.findViewById(R.id.temperatureGroup)
        irrigationGroup = view.findViewById(R.id.irrigationGroup)
        clearButton = view.findViewById(R.id.clearFiltersButton)
        applyButton = view.findViewById(R.id.applyFilterButton)

        setupBudgetSlider()
    }

    private fun setupBudgetSlider() {
        budgetSlider.apply {
            //Settear los valores iniciales
            valueFrom = minAllowedPrice
            valueTo = maxOf(maxProductPrice, minMaxPrice)
            values = listOf(valueFrom, valueTo)

            //Settear steps de 1000
            stepSize = 1000f

            //Formateamos el texto a la moneda chilena
            setLabelFormatter { value ->
                NumberFormat.getCurrencyInstance(Locale("es", "CL"))
                    .format(value.toDouble())
            }

            updateBudgetText(valueFrom, valueTo)
        }

        //Agregar el listener para el slider
        budgetSlider.addOnChangeListener { slider, _, _ ->
            val values = slider.values
            //Permitir que el valor mínimo cambie
            val minValue = values[0]
            val maxValue = values[1]

            //Actualizar el valor de los extremos
            currentFilters.apply {
                minPrice = minValue.toInt()
                maxPrice = maxValue.toInt()
            }

            updateBudgetText(minValue, maxValue)
        }
    }

    private fun setupBottomSheetBehavior() {
        (dialog as? BottomSheetDialog)?.let { bottomSheetDialog ->
            bottomSheetDialog.behavior.apply {
                //Calcular donde debe estar el sheet
                val filterButtonBottom = requireArguments().getInt(ARG_FILTER_BUTTON_Y, 0)
                val screenHeight = resources.displayMetrics.heightPixels
                val peekHeight = screenHeight - filterButtonBottom

                //Settear las propiedades
                setPeekHeight(peekHeight)
                maxHeight = peekHeight
                isDraggable = false
                state = BottomSheetBehavior.STATE_EXPANDED

                //Agregar un listener para mantener el sheet en la altura correcta
                addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                    override fun onStateChanged(bottomSheet: View, newState: Int) {
                        //Asegurarse de que el sheet se mantenga en la altura correcta
                        if (newState == BottomSheetBehavior.STATE_DRAGGING) {
                            state = BottomSheetBehavior.STATE_EXPANDED
                        }
                    }

                    override fun onSlide(bottomSheet: View, slideOffset: Float) {
                        //No es necesario
                    }
                })
            }
        }
    }

    fun setMaxProductPrice(price: Float) {
        maxProductPrice = maxOf(price, minMaxPrice)  //Asegurar que el precio no sea menor que
        // minMaxPrice
        if (::budgetSlider.isInitialized) {
            setupBudgetSlider()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        //Crear un BottomSheetDialog personalizado
        return BottomSheetDialog(requireContext(), theme).apply {
            //Customizar la apariencia
            window?.apply {
                //Agregar transparencia
                setDimAmount(0.3f)
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            }
        }
    }

    override fun getTheme(): Int {
        return R.style.BottomSheetDialogTheme
    }

    private fun setupFilterListeners() {
        budgetSlider.addOnChangeListener { slider, _, _ ->
            val values = slider.values
            val minValue = minAllowedPrice
            val maxValue = maxOf(values[1], minMaxPrice)

            currentFilters.apply {
                minPrice = minValue.toInt()
                maxPrice = maxValue.toInt()
            }

            applyButton.setOnClickListener {
                val appliedFilters = PlantFilterParams().apply {
                    //Rango de precios (si se ha cambiado)
                    if (budgetSlider.values[0] > minAllowedPrice ||
                        budgetSlider.values[1] < budgetSlider.valueTo) {
                        minPrice = budgetSlider.values[0].toInt()
                        maxPrice = budgetSlider.values[1].toInt()
                    }

                    //Seleccion de tamaño
                    size = when (sizeGroup.checkedRadioButtonId) {
                        R.id.sizeS -> PlantFilterParams.SIZE_S
                        R.id.sizeM -> PlantFilterParams.SIZE_M
                        R.id.sizeL -> PlantFilterParams.SIZE_L
                        R.id.sizeXL -> PlantFilterParams.SIZE_XL
                        else -> null
                    }

                    //Seleccion de pet friendly
                    petFriendly = if (petFriendlyCheckbox.isChecked) true else null

                    //Seleccion de luz
                    lighting = when (lightGroup.checkedRadioButtonId) {
                        R.id.lightDirect -> 1
                        R.id.lightPartial -> 2
                        R.id.lightShade -> 3
                        else -> null
                    }

                    //Seleccion de temperatura
                    temperatureTolerance = when (temperatureGroup.checkedRadioButtonId) {
                        R.id.tempWarm -> 1
                        R.id.tempMild -> 2
                        R.id.tempCold -> 3
                        else -> null
                    }

                    //Seleccion de riego
                    irrigationType = when (irrigationGroup.checkedRadioButtonId) {
                        R.id.irrigationManual -> 1
                        R.id.irrigationDrip -> 2
                        R.id.irrigationCapillary -> 3
                        R.id.irrigationSubmersion -> 4
                        R.id.irrigationSelfWatering -> 5
                        R.id.irrigationMisting -> 6
                        R.id.irrigationAutomatic -> 7
                        else -> null
                    }

                    //preservar cualquier dato de orden
                    orderBy = currentFilters.orderBy
                    order = currentFilters.order
                }

                //Pasar los filtros aplicados
                filterListener?.onFiltersApplied(appliedFilters)
                dismiss()
            }

            clearButton.setOnClickListener {
                //Resetear los filtros
                resetFilters()

                //Crear filtros limpios, preservando los datos de orden
                val emptyFilters = PlantFilterParams().apply {
                    orderBy = currentFilters.orderBy
                    order = currentFilters.order
                }

                //Aplicar filtros limpios
                filterListener?.onFiltersApplied(emptyFilters)
                dismiss()
            }

            updateBudgetText(minValue, maxValue)

            //Forzar los valores del slider para mantener la apariencia
            if (values[0] != minValue || values[1] != maxValue) {
                slider.values = listOf(minValue, maxValue)
            }
        }

        //Listener para el grupo de tamaños
        sizeGroup.setOnCheckedChangeListener { _, checkedId ->
            currentFilters.size = when (checkedId) {
                R.id.sizeS -> PlantFilterParams.SIZE_S
                R.id.sizeM -> PlantFilterParams.SIZE_M
                R.id.sizeL -> PlantFilterParams.SIZE_L
                R.id.sizeXL -> PlantFilterParams.SIZE_XL
                else -> null
            }
        }

        //Listener para el checkbox de pet friendly
        petFriendlyCheckbox.setOnCheckedChangeListener { _, isChecked ->
            currentFilters.petFriendly = if (isChecked) true else null
        }

        //Listener para el grupo de iluminacion
        lightGroup.setOnCheckedChangeListener { _, checkedId ->
            currentFilters.lighting = when (checkedId) {
                R.id.lightDirect -> 1  // Sol Directo
                R.id.lightPartial -> 2 // Semi Sombra
                R.id.lightShade -> 3   // Sombra
                else -> null
            }
        }

        //Listener para el grupo de temperaturas
        temperatureGroup.setOnCheckedChangeListener { _, checkedId ->
            currentFilters.temperatureTolerance = when (checkedId) {
                R.id.tempWarm -> 1    // Cálido
                R.id.tempMild -> 2    // Templado
                R.id.tempCold -> 3    // Frío
                else -> null
            }
        }

        //Listener para el grupo de riegos
        irrigationGroup.setOnCheckedChangeListener { _, checkedId ->
            currentFilters.irrigationType = when (checkedId) {
                R.id.irrigationManual -> 1      // Manual
                R.id.irrigationDrip -> 2        // Goteo
                R.id.irrigationCapillary -> 3   // Capilar
                R.id.irrigationSubmersion -> 4  // Sumersión
                R.id.irrigationSelfWatering -> 5// Autorriego
                R.id.irrigationMisting -> 6     // Nebulización
                R.id.irrigationAutomatic -> 7   // Automático
                else -> null
            }
        }

        clearButton.setOnClickListener {
            resetFilters()
        }
    }

    private fun initializeFilterValues() {
        //Inicializar los valores de los filtros
        currentFilters.apply {
            minPrice?.let { min ->
                maxPrice?.let { max ->
                    budgetSlider.values = listOf(min.toFloat(), max.toFloat())
                    updateBudgetText(min.toFloat(), max.toFloat())
                }
            }

            when (size) {
                PlantFilterParams.SIZE_S -> sizeGroup.check(R.id.sizeS)
                PlantFilterParams.SIZE_M -> sizeGroup.check(R.id.sizeM)
                PlantFilterParams.SIZE_L -> sizeGroup.check(R.id.sizeL)
                PlantFilterParams.SIZE_XL -> sizeGroup.check(R.id.sizeXL)
            }

            petFriendlyCheckbox.isChecked = petFriendly == true

            lighting?.let {
                val lightId = when (it) {
                    1 -> R.id.lightDirect
                    2 -> R.id.lightPartial
                    3 -> R.id.lightShade
                    else -> null
                }
                lightId?.let { id -> lightGroup.check(id) }
            }

            temperatureTolerance?.let {
                val tempId = when (it) {
                    1 -> R.id.tempWarm
                    2 -> R.id.tempMild
                    3 -> R.id.tempCold
                    else -> null
                }
                tempId?.let { id -> temperatureGroup.check(id) }
            }

            irrigationType?.let {
                val irrigationId = when (it) {
                    1 -> R.id.irrigationManual
                    2 -> R.id.irrigationDrip
                    3 -> R.id.irrigationCapillary
                    4 -> R.id.irrigationSubmersion
                    5 -> R.id.irrigationSelfWatering
                    6 -> R.id.irrigationMisting
                    7 -> R.id.irrigationAutomatic
                    else -> null
                }
                irrigationId?.let { id -> irrigationGroup.check(id) }
            }
        }
    }

    private fun resetFilters() {
        budgetSlider.values = listOf(minAllowedPrice, maxOf(maxProductPrice, minMaxPrice))
        sizeGroup.clearCheck()
        petFriendlyCheckbox.isChecked = false
        lightGroup.clearCheck()
        temperatureGroup.clearCheck()
        irrigationGroup.clearCheck()

        //Resetear los valores de los filtros
        currentFilters = PlantFilterParams()
    }

    @SuppressLint("SetTextI18n")
    private fun updateBudgetText(min: Float, max: Float) {
        //Formatear el texto a la moneda chilena
        val formatter = NumberFormat.getCurrencyInstance(Locale("es", "CL"))
        budgetValueText.text = "${formatter.format(min.toDouble())} - ${formatter.format(max.toDouble())}"
    }

    companion object {
        private const val ARG_FILTER_BUTTON_Y = "filter_button_y"

        fun newInstance(filterButtonY: Int): PlantFiltersBottomSheet {
            return PlantFiltersBottomSheet().apply {
                arguments = Bundle().apply {
                    putInt(ARG_FILTER_BUTTON_Y, filterButtonY)
                }
            }
        }
    }
}