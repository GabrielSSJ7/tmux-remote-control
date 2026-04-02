package com.remotecontrol.data.api

import com.remotecontrol.data.model.Command
import com.remotecontrol.data.model.CreateCommand
import retrofit2.http.*

interface CommandsApi {
    @GET("commands")
    suspend fun list(): List<Command>

    @POST("commands")
    suspend fun create(@Body body: CreateCommand): Command

    @PUT("commands/{id}")
    suspend fun update(@Path("id") id: String, @Body body: CreateCommand): Command

    @DELETE("commands/{id}")
    suspend fun delete(@Path("id") id: String)
}
