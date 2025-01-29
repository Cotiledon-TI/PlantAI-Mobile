package com.cotiledon.mobilApp.ui.fragments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.cotiledon.mobilApp.R
import com.cotiledon.mobilApp.ui.backend.ia.RetrofitIAClient
import com.cotiledon.mobilApp.ui.dataClasses.ia.IABase64Request
import com.cotiledon.mobilApp.ui.dataClasses.ia.IAResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class IAPromptFragment : Fragment() {
    private var photoPath: String? = null
    private lateinit var iaPrompt: EditText
    private lateinit var nextButton: Button
    private lateinit var backButton: ImageView
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private val iaClient = RetrofitIAClient.createIAClient()
    private lateinit var backPressedCallback: OnBackPressedCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //Inicializar el callback
        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        }

        //Agregar el callback a la actividad
        requireActivity().onBackPressedDispatcher.addCallback(
            this, //Owner del lifecycle
            backPressedCallback
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        //Remover el callback cuando la vista es destruida
        backPressedCallback.remove()
    }

    companion object {
        private const val MAX_IMAGE_DIMENSION = 800  //Settear la dimension maxima de la imagen
        private const val COMPRESSION_QUALITY = 80   //Settear calidad de compresión
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_ia_prompt, container, false)
        iaPrompt = view.findViewById(R.id.ia_prompt_edit_text)
        nextButton = view.findViewById(R.id.ia_prompt_continue_button)
        photoPath = arguments?.getString("photo_path")
        backButton = view.findViewById(R.id.back_btn_ia)
        nextButton.setOnClickListener {
            processImageAndSend()
        }
        backButton.setOnClickListener {
            handleBackNavigation()
        }

        return view

    }

    private fun processImageAndSend() {
        val consulta = iaPrompt.text.toString().trim().takeIf { it.isNotBlank() } ?: ""

        coroutineScope.launch {
            try {
                showLoading(true)
                val base64Image = convertImageToBase64()

                //Crear el request con la imagen en base64 y el prompt
                val request = IABase64Request(
                    base64 = base64Image,
                    consulta = consulta
                )

                val response = withContext(Dispatchers.IO) {
                    iaClient.iaApiService.sendImageBase64(request)
                }

                handleSuccessResponse(response)
            } catch (e: Exception) {
                handleError(e)
            } finally {
                showLoading(false)
            }
        }
    }

    private fun handleBackNavigation() {
        photoPath?.let { path ->
            try {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        coroutineScope.cancel()

        parentFragmentManager.popBackStack()
    }

    private suspend fun convertImageToBase64(): String {
        return withContext(Dispatchers.IO) {
            var byteArrayOutputStream: ByteArrayOutputStream? = null

            try {
                //Obtenemos las dimensiones de la imagen
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true  //Esto nos permite leer las dimensiones sin cargar
                // la imagen
                }
                BitmapFactory.decodeFile(photoPath, options)

                //Calcular el factor de escala
                val scaleFactor = calculateScaleFactor(
                    originalWidth = options.outWidth,
                    originalHeight = options.outHeight,
                    targetSize = MAX_IMAGE_DIMENSION
                )

                //Ahora cargamos la imagen con el factor de escala
                val bitmap = BitmapFactory.Options().apply {
                    inJustDecodeBounds = false
                    inSampleSize = scaleFactor
                }.let { scaledOptions ->
                    BitmapFactory.decodeFile(photoPath, scaledOptions)
                } ?: throw IllegalStateException("Error cargando la imagen")

                //Volver a modificar la imagen si es necesario
                val resizedBitmap = resizeBitmapIfNeeded(bitmap, MAX_IMAGE_DIMENSION)

                //Convertir la imagen a base64
                byteArrayOutputStream = ByteArrayOutputStream()
                resizedBitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    COMPRESSION_QUALITY,
                    byteArrayOutputStream
                )

                //Crear el string de base64
                val imageBytes = byteArrayOutputStream.toByteArray()
                val base64String = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                "data:image/jpeg;base64,$base64String"

            } finally {
                //Limpiar recursos
                byteArrayOutputStream?.close()
            }
        }
    }

    private fun calculateScaleFactor(
        originalWidth: Int,
        originalHeight: Int,
        targetSize: Int
    ): Int {
        //Calcular la menor escala de dos que cumpla con la condicion de targetSize
        var scaleFactor = 1
        while (originalWidth / (scaleFactor * 2) >= targetSize ||
            originalHeight / (scaleFactor * 2) >= targetSize) {
            scaleFactor *= 2
        }
        return scaleFactor
    }

    private fun resizeBitmapIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        //Si el bipmap ya cumple con el tamaño maximo, no hacer nada
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }

        //Calcular nuevas dimensiones mientras mantenemos la proporción
        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int

        if (width > height) {
            newWidth = maxDimension
            newHeight = (maxDimension / ratio).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * ratio).toInt()
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }


    private fun handleSuccessResponse(response: IAResponse) {
        //En vez de mostrar el resultado directamente, navegar al fragmento de catalogo
        val catalogFragment = CatalogFragment().apply {
            arguments = Bundle().apply {
                putInt("environment", response.data.idEntorno)
                putBoolean("pet_friendly", response.data.petFriendly)
                putInt("lighting", response.data.idIluminacion)
                putInt("temperature", response.data.idToleranciaTemperatura)
                putInt("irrigation", response.data.idTipoRiego)
                putString("size", response.data.sizePlant)
                putString("ai_explanation", response.message)
            }
        }

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, catalogFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun handleError(error: Exception) {
        val errorMessage = when (error) {
            is IllegalStateException -> "Error cargando la imagen: ${error.message}"
            else -> "La imagen es demasiado grande, intenta con una más pequeña."
        }

        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
    }

    //Mostrar el indicador de carga y deshabilitar el boton
    private fun showLoading(show: Boolean) {
        view?.findViewById<ProgressBar>(R.id.loading_indicator)?.visibility =
            if (show) View.VISIBLE else View.GONE
        nextButton.isEnabled = !show
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}