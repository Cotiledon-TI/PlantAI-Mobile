package com.cotiledon.mobilApp.ui.helpers


import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import com.cotiledon.mobilApp.R
import android.view.View
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Helper para gestionar la barra de búsqueda y el botón de cámara de manera sencilla y global
 */
class SearchBarHelper(
    private val rootView: View,
    private val searchCallback: SearchCallback
) {
    private var searchEditText: EditText? = null
    private var cameraButton: ImageView? = null
    private var debounceJob: Job? = null
    private val DEBOUNCE_DELAY = 300L

    //Interfaz para manejar los callbacks
    interface SearchCallback {
        fun onQueryTextSubmit(query: String)
        fun onQueryTextChange(newText: String)
        fun onCameraButtonClick()
    }

    //Obtener el scope de la coroutine usada en el root view
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        initializeViews()
        setupSearchListeners()
    }

    private fun initializeViews() {
        //Inicializar vistas utilizando el rootView
        searchEditText = rootView.findViewById(R.id.search_edit_text)
        cameraButton = rootView.findViewById(R.id.camera_button)
    }
    private fun setupSearchListeners() {
        searchEditText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                //Cancelar el job anterior
                debounceJob?.cancel()

                //Crear el nuevo job
                debounceJob = scope.launch {
                    delay(DEBOUNCE_DELAY)
                    s?.toString()?.let { searchCallback.onQueryTextChange(it) }
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        searchEditText?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchEditText?.text?.toString()?.let {
                    searchCallback.onQueryTextSubmit(it)
                }
                true
            } else {
                false
            }
        }

        cameraButton?.setOnClickListener {
            searchCallback.onCameraButtonClick()
        }
    }

    fun cleanup() {
        scope.cancel()
    }

    fun clearSearch() {
        searchEditText?.apply {
            setText("")
            clearFocus()
        }
    }


    fun setHint(hint: String) {
        searchEditText?.hint = hint
    }

    fun setCameraButtonEnabled(enabled: Boolean) {
        cameraButton?.apply {
            isEnabled = enabled
            alpha = if (enabled) 1.0f else 0.5f
        }
    }

    fun getCurrentSearchText(): String {
        return searchEditText?.text?.toString() ?: ""
    }
}