package com.cotiledon.mobilApp.ui.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.cotiledon.mobilApp.R
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class IAMainFragment : Fragment() {

    private lateinit var checkBox: CheckBox
    private lateinit var continueButton: Button

    //Constantes de camara y permisos
    companion object {
        private const val CAMERA_PERMISSION_CODE = 1001
        private const val CAMERA_REQUEST_CODE = 1002
    }

    private var photoFile: File? = null
    private var currentPhotoPath: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_ia_main, container, false)

        checkBox = view.findViewById(R.id.ia_checkBox)
        continueButton = view.findViewById(R.id.ia_continue_button)

        continueButton.setOnClickListener {
            if (checkBox.isChecked) {
                checkCameraPermissionAndOpen()
            } else {
                Toast.makeText(requireContext(), "Por favor, danos permiso primero", Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }

    //Funcion para pedir permisos y abrir la camara
    private fun checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            openCamera()
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        }
    }

    //Crear un archivo temporal para la foto
    @Throws(IOException::class)
    private fun createImageFile(): File {
        //Crear un nombre de archivo temporal con un timestamp
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"

        //Obtener el directorio para almacenar la foto
        val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)

        //Crear el archivo
        return File.createTempFile(
            imageFileName,
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    //Funcion para abrir la camara
    @SuppressLint("QueryPermissionsNeeded")
    private fun openCamera() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            //Asegurarse que hay una aplicacion que pueda manejar la accion
            takePictureIntent.resolveActivity(requireActivity().packageManager)?.also {
                //Crear el archivo donde va a guardar la foto
                photoFile = try {
                    createImageFile()
                } catch (ex: IOException) {
                    Toast.makeText(requireContext(), "Error creando el archivo de imagen", Toast.LENGTH_SHORT).show()
                    null
                }

                //Continuar solo si el archivo se pudo crear
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        requireContext(),
                        "com.cotiledon.mobilApp.fileprovider",
                        photoFile!!
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, CAMERA_REQUEST_CODE)
                }
            }
        }
    }

    //Manejar el resultado del permiso
    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            CAMERA_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openCamera()
                } else {
                    if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                        //Mostrar diálogo de porque necesitamos el permiso
                        AlertDialog.Builder(requireContext())
                            .setTitle("Se necesita permiso de la camara")
                            .setMessage("Necesitamos acceso a la camara para usar esta funcionalidad")
                            .setPositiveButton("Dar Permiso") { _, _ ->
                                requestPermissions(
                                    arrayOf(Manifest.permission.CAMERA),
                                    CAMERA_PERMISSION_CODE
                                )
                            }
                            .setNegativeButton("Cancelar") { dialog, _ ->
                                dialog.dismiss()
                                Toast.makeText(
                                    requireContext(),
                                    "Se necesita permiso de la camara para usar esta funcionalidad",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .create()
                            .show()
                    } else {
                        //Si se denega el permiso, abrir la configuración para que lo permita
                        AlertDialog.Builder(requireContext())
                            .setTitle("Se necesita permiso de la camara")
                            .setMessage("Se ha denegaddo el permiso de la cámara. Por favor, vaya a los ajustes y permita el acceso a la cámara.")
                            .setPositiveButton("Ir a Ajustes") { _, _ ->
                                //Abrir settings de la app
                                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", requireActivity().packageName, null)
                                })
                            }
                            .setNegativeButton("Cancelar") { dialog, _ ->
                                dialog.dismiss()
                            }
                            .create()
                            .show()
                    }
                }
            }
        }
    }

    //Manejar resultado de la cámara
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CAMERA_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            //Mostrar diálogo para retomar o utilizar la foto
            showImageConfirmationDialog()
        }
    }

    //Diálogo para confirmar la foto
    private fun showImageConfirmationDialog() {
        //Obtener el tamaño de la foto
        val fileSize = photoFile?.length() ?: 0L
        val fileSizeMB = bytesToMB(fileSize)

        //Mostrar el tamaño de la foto
        Log.d("Captura de imagen", "Tamaño de la foto: ${String.format("%.2f", fileSizeMB)} MB")

        AlertDialog.Builder(requireContext())
            .setTitle("Foto capturada")
            .setMessage("Tamaño de la foto: ${String.format("%.2f", fileSizeMB)} MB\n\n¿Quieres utilizar esta foto o tomar otra?")
            .setPositiveButton("Utilizar") { dialog, _ ->
                dialog.dismiss()
                navigateToPromptFragment()
            }
            .setNegativeButton("Volver a intentar") { dialog, _ ->
                dialog.dismiss()
                openCamera()
            }
            .show()
    }

    private fun bytesToMB(bytes: Long): Double {
        return bytes.toDouble() / (1024 * 1024)
    }

    //Navegar al fragment de prompt
    private fun navigateToPromptFragment() {
        //Crear un bundle con la ruta de la imagen
        val bundle = Bundle().apply {
            putString("photo_path", currentPhotoPath)
        }

        //Crear y pasar argumentos al fragment
        val promptFragment = IAPromptFragment().apply {
            arguments = bundle
        }

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, promptFragment)
            .addToBackStack(null)
            .commit()
    }
}