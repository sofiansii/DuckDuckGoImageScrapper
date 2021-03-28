package com.firstapp.betterpexel.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import com.firstapp.betterpexel.activities.MainActivity
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.Serializable
import java.util.concurrent.TimeUnit


class DuckDuckGo(private val arguments :Map<String,String>,private val onGotVqd: (DuckDuckGo) -> Unit) {

    val h = Handler(Looper.getMainLooper())
    val WIDE = "WIDE"
    val TALL = "TALL"
    val SQUARE = "SQUARE"
    private var next :String? = null
    val images  = arrayListOf<Image>()
    var size :Int= 0
    var keyword :String
    init {
        if (arguments.containsKey("q")){
            keyword  = arguments["q"] ?: error(" ")
        }else{
            keyword = " "
        }
    }

    var thereIsInternet = true
    var hasMore = true
    private lateinit var vqd :String
    var ready = 0
    private var realReady = 0
    var offset = 0
    private val baseUrl = "https://duckduckgo.com/"

    private val dispatcher = Dispatcher().apply { maxRequests = 20}
    private val client :OkHttpClient

    init {



        val httpCacheDirectory = File(MainActivity.context?.cacheDir, "offlineCache")
        val cache = Cache(httpCacheDirectory, 10 * 1024 * 1024)
        client = OkHttpClient.Builder()
            .connectTimeout(60,TimeUnit.SECONDS)
            .readTimeout(60,TimeUnit.SECONDS)
            .writeTimeout(60,TimeUnit.SECONDS)
            .cache(cache)
            .dispatcher(dispatcher)
            .build()


        if (arguments.containsKey("q")){
            try{
                getVqd()
                println("init done")
            }catch (e:Exception){
                e.printStackTrace()
            }
        }
    }

    fun getVqd(){
        val url = baseUrl
        val multiReq = FormBody.Builder().addEncoded("q",keyword).build()
        println(keyword)
        val req = Request.Builder()
            .post(multiReq)
            .removeHeader("accept-encoding")
            .url(url).build()
        client.newCall(req).enqueue(object :Callback{
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                h.postDelayed({client.newCall(req).enqueue(this)},1000)
            }

            override fun onResponse(call: Call, response: Response) {
                val reg = Regex("vqd=([\\d-]+)\\&")
                val res = response.body?.string()
                try {
                    vqd = reg.find(res!!)!!.value
                    vqd=vqd.substring(4,vqd.length-1)
                    onGotVqd.invoke(this@DuckDuckGo)
                }catch (e:Exception){
                    h.postDelayed({client.newCall(req).enqueue(this)},1000)
                }


            }
        })

    }

    fun getImages(done: (DuckDuckGo)->Unit){
        println("we are in get image")

        val req :Request
        try {
            val url = prepareLink()
            req = Request.Builder()
                .url(url!!)
                .get()
                .build()
        }catch (e:Exception){
            hasMore = false
            done.invoke(this)
            println("failed preparing request")
            return
        }
        client.newCall(req).enqueue(object :Callback{
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                h.postDelayed({client.newCall(req).enqueue(this) },3000)
            }

            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string()
                if (res != null){
                    val jobj :JSONObject
                    val resuls :JSONArray
                    try{
                        jobj = JSONObject(res)
                        resuls = jobj.getJSONArray("results")
                    }catch (e:Exception){
                        e.printStackTrace()
                        println(" the rs for the above error is \n $res\n the url is ${req.url} \n and the vqd is $vqd ")
                        h.postDelayed({client.newCall(req).enqueue(this)},1000)
                        return
                    }
                    if (resuls.length() == 0) {
                        h.postDelayed({client.newCall(req).enqueue(this)},1000)
                        return
                    }
                    for (i in 0.until(resuls.length())){
                        val obj = resuls.getJSONObject(i)
                        images.add(
                            Image(
                                obj.getString("image"),
                                obj.getString("thumbnail"),
                                obj.getString("title"),
                                "${obj.getInt("width")}x${obj.getInt("height")}",
                                obj.getString("url")
                            )
                        )
                        size++
                    }
                    Handler(Looper.getMainLooper()).post{done.invoke(this@DuckDuckGo)}
                    try {
                        val tnext = jobj.getString("next")
                        if (next != tnext) next = tnext else hasMore = false
                    }catch (e:Exception){
                        hasMore = false
                    }
                }else{
                    h.postDelayed({client.newCall(req).enqueue(this)},1000)
                }
            }

        })
    }



    private fun prepareLink():HttpUrl?{
        val params = hashMapOf(
            Pair("l", "us-en"),
            Pair("o", "json"),
            Pair("vqd",vqd),
            Pair("p", "1"),
            Pair("v7exp", "a"),
            Pair("q",keyword)
        )
        val httpUrl:HttpUrl
        for ((k,v) in arguments){
            params[k] = v
        }

        when {
            next == null -> {
                httpUrl = HttpUrl.Builder().apply {
                    scheme("https")
                    host("duckduckgo.com")
                    encodedPath("/i.js")
                    for ((k,v) in params)addQueryParameter(k,v)
                }.build()
            }
            hasMore -> {
                httpUrl = HttpUrl.Builder().apply {
                    scheme("https")
                    host("duckduckgo.com")
                    encodedPath("/i.js")
                    var a = next!!
                    a = a.substring(5)

                    a.split("&").forEach {parandvalue ->
                        parandvalue.split("=").apply {
                            addEncodedQueryParameter(this[0],this[1])
                        }
                    }
                    addQueryParameter("vqd",vqd)

                }.build()
            }
            else -> {
                return null
            }
        }
        println("the url is $httpUrl")
        return httpUrl
    }

    private var called = false
    private var pendingcallack : ()->Unit = {}
    fun moreImages(done:()->Unit){
        println("called read = $ready adn RealReady = $realReady")
        when{
            ready == 0 -> {
                getchunk{ready += 20;done.invoke();if (hasMore)getchunk{realReady = ready + 20}}
            }
            realReady > ready -> {

                ready  = realReady
                done.invoke()
                if (hasMore) getchunk {
                    realReady += 20
                    println("called 2nd statement read = $ready adn RealReady = $realReady")
                    if (called){
                        moreImages(pendingcallack )
                        called = false
                        println("scheduled executed read = $ready adn RealReady = $realReady")
                    }
                }
            }
            realReady == ready -> {
                println("scheduled read = $ready adn RealReady = $realReady")
                called = true
                pendingcallack = done
            }

        }
    }

    fun getchunk(done:()->Unit){
        when {
            images.size -  (ready + offset +20)  >30 -> {
                count =0
                for (i in (ready+offset).until(ready+offset+20)){
                    getImage(images[i]){
                        count =0
                        done.invoke()
                    }
                }
            }
            hasMore -> {
                try {
                    getImages {getchunk(done)}
                }catch (e:Exception){
                    e.printStackTrace()
                }
            }
            else -> {
                done.invoke()
            }
        }
    }




    private var count = 0
    private fun getImage(image: Image, done:()->Unit){
        val req = Request.Builder()
            .url(image.thumbnail)
            .get()
            .build()
        client.newCall(req)
            .enqueue(object :Callback{
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                    println("failed")
                    client.newCall(req).enqueue(this)
                }
                override fun onResponse(call: Call, response: Response) {
                    try{
                        val b = BitmapFactory.decodeStream(response.body?.byteStream())
                        response.body?.close()
                        image.bitmap = b
                        synchronized(count){
                            count++
                        }
                        if (count == 20){
                            done.invoke()
                        }
                    }catch (e:Exception){
                        println("faileeeed")
                        offset+1
                        e.printStackTrace()
                        client.newCall(req.newBuilder().url(images[ready+offset+20].thumbnail).build()).enqueue(object :Callback{
                            override fun onFailure(call: Call, e: IOException) {
                                e.printStackTrace()
                                println("failed")
                                client.newCall(req).enqueue(this)
                            }

                            override fun onResponse(call: Call, response: Response) {
                                println("count = $count")
                                try{
                                    val b = BitmapFactory.decodeStream(response.body?.byteStream())
                                    images[ready+offset+20].bitmap = b
                                    count ++
                                    println("count is $count")
                                    if (count == 20){
                                        done.invoke()
                                    }
                                }catch (e:Exception){
                                    offset+1
                                    client.newCall(req.newBuilder().url(images[ready +offset].thumbnail).build()).enqueue(this)
                                }
                            }
                        })
                    }
                }
            })
    }

    data class Image(val image :String,val thumbnail:String,val title :String,val dimensions :String,val url :String, var bitmap:Bitmap?  = null) :Serializable


}