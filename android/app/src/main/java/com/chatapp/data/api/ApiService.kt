package com.chatapp.data.api

import com.chatapp.data.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("api/auth/register")
    suspend fun register(@Body body: AuthRequest): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body body: AuthRequest): Response<AuthResponse>

    @Multipart
    @POST("api/upload")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part,
        @Part("roomId") roomId: RequestBody,
        @Part("sender") sender: RequestBody
    ): Response<FileInfo>

    @GET("api/rooms")
    suspend fun getRooms(): Response<List<RoomInfo>>

    @GET("api/messages/{roomId}")
    suspend fun getMessages(
        @Path("roomId") roomId: String,
        @Query("limit") limit: Int = 50,
        @Query("before") before: Long = System.currentTimeMillis()
    ): Response<List<Message>>

    @GET("api/private-messages/{otherUserId}")
    suspend fun getPrivateMessages(
        @Path("otherUserId") otherUserId: Long,
        @Query("limit") limit: Int = 50,
        @Query("before") before: Long = System.currentTimeMillis()
    ): Response<List<Message>>

    @GET("api/users/search")
    suspend fun searchUsers(@Query("q") query: String): Response<List<SearchUser>>

    @GET("api/friends")
    suspend fun getFriends(): Response<List<User>>

    @GET("api/friend-requests")
    suspend fun getFriendRequests(): Response<FriendRequestsResponse>

    @POST("api/friend-request")
    suspend fun sendFriendRequest(@Body body: Map<String, Long>): Response<Map<String, Any>>

    @POST("api/friend-accept")
    suspend fun acceptFriendRequest(@Body body: Map<String, Long>): Response<Map<String, Any>>

    @POST("api/friend-reject")
    suspend fun rejectFriendRequest(@Body body: Map<String, Long>): Response<Map<String, Any>>
}
