package com.remotecontrol.data.api

import com.remotecontrol.data.model.Session
import retrofit2.http.*

interface SessionsApi {
    @GET("sessions")
    suspend fun list(): List<Session>

    @POST("sessions")
    suspend fun create(@Body body: Map<String, String?>): Session

    @DELETE("sessions/{id}")
    suspend fun delete(@Path("id") id: String)

    @GET("sessions/{id}/status")
    suspend fun status(@Path("id") id: String): Session

    @POST("sessions/{id}/exec")
    suspend fun exec(@Path("id") id: String, @Body body: Map<String, String>)
}
