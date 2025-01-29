package com.cotiledon.mobilApp.ui.fragments.base
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.cotiledon.mobilApp.ui.helpers.SearchBarHelper

/*
 Fragmento base para poder manejar la barra de busqueda.
 Los fragmentos que quieran manejar la barra de busqueda deben extender esta clase
 e implementar el SearchBarHelper.SearchCallback interface.
 */
abstract class SearchableFragment : Fragment(), SearchBarHelper.SearchCallback {
    private var searchBarHelper: SearchBarHelper? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSearchBar(view)
    }

    private fun setupSearchBar(view: View) {
        searchBarHelper = SearchBarHelper(view, this)

        //Setear hint base. (Puede ser sobrescrito en los childs)
        searchBarHelper?.setHint(getSearchHint())

        //Configurar visibilidad del boton de camara
        searchBarHelper?.setCameraButtonEnabled(isCameraEnabled())
    }

    //Puede ser sobrescrito en los childs para cambiar el hint
    protected open fun getSearchHint(): String {
        return "Buscar..."
    }

    //Puede ser sobrescrito en los childs para cambiar la visibilidad del boton de camara
    protected open fun isCameraEnabled(): Boolean {
        return false
    }

    //Implementaciones por defecto para el SearchBarHelper
    override fun onQueryTextSubmit(query: String) {
        //A ser implementado por los childs
    }

    override fun onQueryTextChange(newText: String) {
        //A ser implementado por los childs
    }

    override fun onCameraButtonClick() {
        //A ser implementado por los childs
    }
}