package com.application.zaona.weather.service

import com.application.zaona.weather.model.Sponsor
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest

object AfdianService {
    private const val API_URL = "https://afdian.com/api/open/query-sponsor"
    private const val USER_ID = "00fb401a588211ebb7db52540025c377"
    private const val TOKEN = "jcqCb56tkgBv7eV48HhDUyaFPwKGMdsX"

    private val client = OkHttpClient()
    private val gson = Gson()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    suspend fun fetchSponsors(
        minimumAmount: Double = Sponsor.MINIMUM_SUPPORT_AMOUNT,
        perPage: Int = 100,
        onProgress: (Float) -> Unit = {}
    ): List<Sponsor> = withContext(Dispatchers.IO) {
        val validPerPage = perPage.coerceIn(1, 100)
        
        // First page to get total pages
        val firstPageResult = fetchSponsorsPage(1, validPerPage)
        val totalPage = firstPageResult.totalPage
        val allSponsorsList = ArrayList<Map<String, Any?>>(firstPageResult.list)
        
        onProgress(1f / totalPage.coerceAtLeast(1))

        // Fetch remaining pages
        if (totalPage > 1) {
            for (page in 2..totalPage) {
                val pageResult = fetchSponsorsPage(page, validPerPage)
                allSponsorsList.addAll(pageResult.list)
                onProgress(page.toFloat() / totalPage)
                Thread.sleep(200) // Delay to avoid rate limiting
            }
        }

        // Parse and filter
        val sponsors = allSponsorsList.mapNotNull { item ->
            try {
                val user = item["user"] as? Map<String, Any?>
                val name = (user?.get("name") as? String)?.trim()
                val amountString = item["all_sum_amount"] as? String ?: "0.00"
                val totalAmount = amountString.toDoubleOrNull() ?: 0.0
                
                if (totalAmount >= minimumAmount) {
                    Sponsor(
                        name = if (!name.isNullOrEmpty()) name else "未知赞助者",
                        totalAmount = totalAmount
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
        
        sponsors
    }

    private fun fetchSponsorsPage(page: Int, perPage: Int): AfdianResponseData {
        val body = buildRequestBody(page, perPage)
        val request = Request.Builder()
            .url(API_URL)
            .post(gson.toJson(body).toRequestBody(JSON))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Network error: ${response.code}")
            
            val responseBody = response.body?.string() ?: throw Exception("Empty response")
            val data = gson.fromJson(responseBody, AfdianResponse::class.java)
            
            if (data.ec != 200 || data.data == null) {
                throw Exception("API error: ${data.ec}")
            }
            
            return data.data
        }
    }

    private fun buildRequestBody(page: Int, perPage: Int): Map<String, Any> {
        val ts = System.currentTimeMillis() / 1000
        val params = mapOf(
            "page" to page,
            "per_page" to perPage
        )
        val paramsJson = gson.toJson(params)
        
        val signString = "$TOKEN" + "params$paramsJson" + "ts$ts" + "user_id$USER_ID"
        val sign = md5(signString)
        
        return mapOf(
            "user_id" to USER_ID,
            "params" to paramsJson,
            "ts" to ts,
            "sign" to sign
        )
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
    
    private data class AfdianResponse(
        val ec: Int,
        val em: String,
        val data: AfdianResponseData?
    )

    private data class AfdianResponseData(
        @SerializedName("total_count") val totalCount: Int,
        @SerializedName("total_page") val totalPage: Int,
        val list: List<Map<String, Any?>>
    )
}
