package com.cotiledon.mobilApp.ui.backend

import com.cotiledon.mobilApp.ui.managers.TokenManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        //Obtener la solicitud original
        val originalRequest = chain.request()

        //Obtener el token desde la instancia de TokenManager
        //Si no hay token, se devuelve la solicitud original
        val token = tokenManager.getToken() ?: return chain.proceed(originalRequest)

        //Crear una nueva solicitud con el token
        val newRequest = originalRequest.newBuilder()
            .addHeader("Authorization", "Bearer $token")
            .build()

        //Proceder con la nueva solicitud
        return chain.proceed(newRequest)
    }
}