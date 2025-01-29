package com.cotiledon.mobilApp.ui.dataClasses.profile

data class ProfileResponse(
    val id: Int,
    val nombre: String,
    val apellido: String,
    val nombreUsuario: String,
    val email: String,
    val telefono: String?,
    val genero: String?,
    val rut: String,
    val fechaNacimiento: String,
    val rol: String,
    val direcciones: List<String>,
    val access_token: String?,
    val expToken: Long?
)

fun ProfileResponse.toVisitorResponse(): VisitorResponse {
    //Creamos un VisitorResponse a partir de un ProfileResponse
    return VisitorResponse(
        //Campos básicos son rellenados automáticamente
        id = this.id,
        nombre = this.nombre,
        apellido = this.apellido,
        nombreUsuario = this.nombreUsuario,
        email = this.email,
        rut = this.rut,
        rol = "Visitante",

        //Convertimos las direcciones en una dirección con la estructura válida
        //Si la dirección no es válida, se omite
        direcciones = this.direcciones.mapNotNull { direccionStr ->
            try {
                val parts = direccionStr.split(",")
                Direccion(
                    id = parts[0].toInt(),
                    comuna = parts[1],
                    calle = parts[2],
                    numero = parts[3],
                    departamento = parts.getOrNull(4),
                    referencia = parts.getOrNull(5)
                )
            } catch (e: Exception) {
                null
            }
        },

        access_token = this.access_token ?: "",
        expToken = this.expToken ?: 0L
    )
}