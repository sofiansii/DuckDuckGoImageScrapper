package com.firstapp.betterpexel.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import com.firstapp.betterpexel.activities.MainActivity
import okhttp3.*
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class Downloader {
    private var count = 0
    private var desiredCount = 0
    private val offset = 0
    private val bitmaps = arrayListOf<Bitmap>()
    private val dispatcher = Dispatcher().apply { this.maxRequests =20 }
    private val client:OkHttpClient

    init {
        val httpCacheDirectory = File(MainActivity.context?.cacheDir, "offlineCache")
        val onlineInterceptor: Interceptor = object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val response: Response = chain.proceed(chain.request())
                val maxAge = 60 * 60 * 24 * 30 // read from cache for 60 seconds even if there is internet connection
                return response.newBuilder()
                    .header("Cache-Control", "public, max-age=$maxAge")
                    .removeHeader("Pragma")
                    .build()
            }
        }
        val cache = Cache(httpCacheDirectory, 10 * 1024 * 1024)
        client = OkHttpClient.Builder()
            .connectTimeout(30,TimeUnit.SECONDS)
            .readTimeout(30,TimeUnit.SECONDS)
            .writeTimeout(30,TimeUnit.SECONDS)
            .cache(cache)
            .dispatcher(dispatcher)
            .build()
    }



    fun getChunk(images:List<DuckDuckGo.Image>,max :Int, done :()->Unit){
        count = 0
        desiredCount = max
        for (i in 0.until(desiredCount)){
            getImage(images[i]) {done.invoke()}
        }
    }



    private fun getImage(image: DuckDuckGo.Image, done :()->Unit){
        val req = Request.Builder()
            .url(image.thumbnail)
            .get()
            .build()
        client.newCall(req)
            .enqueue(object :Callback{
                override fun onFailure(call: Call, e: IOException) {
                    println("failed")
                    Handler(Looper.getMainLooper()).postDelayed({
                        client.newCall(req)
                    },1000)
                }
                override fun onResponse(call: Call, response: Response) {
                    try{
                        val b = BitmapFactory.decodeStream(response.body?.byteStream())
                        response.body?.close()
                        image.bitmap = b
                        synchronized(count){
                            count ++
                        }
                        println("count is $count")
                        if (count == desiredCount){
                            Handler(Looper.getMainLooper()).post {
                                done.invoke()
                            }
                        }
                    }catch (e:Exception){
                        response.close()
                        offset+1
                        e.printStackTrace()
                        Handler(Looper.getMainLooper()).postDelayed({
                            client.newCall(req).enqueue(this)
                        },1000)
                    }
                }
            })
    }


}