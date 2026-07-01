package com.example.data.github

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

data class GithubContent(
    val name: String,
    val path: String,
    val type: String, // "file" or "dir"
    val content: String?, // Base64 encoded
    val sha: String
)

data class GithubPutRequest(
    val message: String,
    val content: String, // Base64 encoded
    val sha: String? = null,
    val branch: String
)

data class GithubTreeEntry(
    val path: String,
    val mode: String,
    val type: String, // "blob" or "tree"
    val sha: String,
    val size: Int? = null
)

data class GithubTreeResponse(
    val sha: String,
    val url: String,
    val tree: List<GithubTreeEntry>,
    val truncated: Boolean
)

interface GithubApiService {
    @GET("repos/{owner}/{repo}/git/trees/{branch}")
    suspend fun getTreeRecursive(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String,
        @Query("recursive") recursive: Int = 1
    ): GithubTreeResponse

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getContent(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String,
        @Query("ref") branch: String
    ): GithubContent

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getDirectoryContents(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String,
        @Query("ref") branch: String
    ): List<GithubContent>

    @GET("repos/{owner}/{repo}/contents")
    suspend fun getRootContents(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("ref") branch: String
    ): List<GithubContent>

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun putContent(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String,
        @Body request: GithubPutRequest
    )
}
