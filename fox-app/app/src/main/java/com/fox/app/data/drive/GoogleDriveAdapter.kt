package com.fox.app.data.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/** Real Drive v3 REST client — plain OkHttp + org.json, no google-api-client dependency. */
class GoogleDriveAdapter(
    private val tokenProvider: DriveAuthTokenProvider,
    private val httpClient: OkHttpClient = OkHttpClient(),
) : DriveAdapter {

    override suspend fun listMarkdownFiles(folderId: String?): List<DriveFile> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DriveFile>()
        var pageToken: String? = null

        do {
            val query = buildString {
                append("(mimeType='text/markdown' or fileExtension='md') and trashed=false")
                if (folderId != null) append(" and '$folderId' in parents")
            }

            val urlBuilder = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("fields", "nextPageToken,files(id,name,modifiedTime,md5Checksum)")
                .addQueryParameter("pageSize", "1000")
            pageToken?.let { urlBuilder.addQueryParameter("pageToken", it) }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .header("Authorization", "Bearer ${tokenProvider.getAccessToken()}")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw DriveApiException("列出 Drive 檔案失敗: HTTP ${response.code} ${response.message}")
                }
                val json = JSONObject(response.body?.string().orEmpty())
                val filesArray: JSONArray = json.optJSONArray("files") ?: JSONArray()
                for (i in 0 until filesArray.length()) {
                    val f = filesArray.getJSONObject(i)
                    results += DriveFile(
                        id = f.getString("id"),
                        name = f.getString("name"),
                        modifiedTime = f.getString("modifiedTime"),
                        md5Checksum = f.optString("md5Checksum", "").ifEmpty { null },
                    )
                }
                pageToken = json.optString("nextPageToken", "").ifEmpty { null }
            }
        } while (pageToken != null)

        results
    }

    override suspend fun downloadFileContent(fileId: String): String = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId".toHttpUrl().newBuilder()
            .addQueryParameter("alt", "media")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${tokenProvider.getAccessToken()}")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw DriveApiException("下載 Drive 檔案失敗 (fileId=$fileId): HTTP ${response.code} ${response.message}")
            }
            response.body?.string() ?: throw DriveApiException("Drive 檔案內容為空 (fileId=$fileId)")
        }
    }
}
