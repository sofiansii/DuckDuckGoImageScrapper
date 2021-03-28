package com.firstapp.betterpexel.adapters

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.firstapp.betterpexel.tools.Downloader
import com.firstapp.betterpexel.tools.DuckDuckGo
import com.firstapp.betterpexel.R
import com.firstapp.betterpexel.fragments.HomeFragment
import com.firstapp.betterpexel.fragments.RelatedImagesFragment
import com.google.android.material.tabs.TabLayout
import kotlinx.android.synthetic.main.image_cell.view.*
import java.io.ByteArrayOutputStream
import java.io.Serializable
import java.nio.ByteBuffer


open class RecyclerAdapter(
    private val fragment :Fragment,
    private val sglm: StaggeredGridLayoutManager,
    private val args :Map<String,String>
):RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    val duckduck = DuckDuckGo(args) {
        try {
            getMore()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    private val downloader = Downloader()
    protected var requested = true
    private var lastImageseen = 0

    protected val inflater = LayoutInflater.from(fragment.context)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == 2) object:RecyclerView.ViewHolder(inflater.inflate(R.layout.image_cell,parent,false)){}
        else object:RecyclerView.ViewHolder(inflater.inflate(R.layout.progress_bar,parent,false)){}
    }

    override fun getItemCount(): Int = if (duckduck.hasMore) duckduck.ready +1 else duckduck.ready /*if we still can paginate add circular progress bar  */

    override fun getItemId(position: Int): Long = position.toLong()


    override fun getItemViewType(position: Int): Int = if (position == duckduck.ready || duckduck.ready == 0) 1 else 2 /*show circular progressbar if we at postion 0 or at the last element */

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (position != duckduck.ready  && duckduck.ready != 0){
            holder.itemView.image.setImageBitmap(duckduck.images[position].bitmap)
            holder.itemView.image.setOnClickListener{onClick(position)}
            if (position > lastImageseen) lastImageseen = position

        }else{
            (holder.itemView.layoutParams as StaggeredGridLayoutManager.LayoutParams).isFullSpan = true
            if(!requested){
                requested = true
                Handler(Looper.getMainLooper()).postDelayed({
                    getMore()
                },1000)
            }
        }

    }

    protected open fun onClick(position: Int){
        val b = Bundle()
        b.putSerializable("image",duckduck.images[position])
        val f = RelatedImagesFragment()
        f.arguments = b
        val fm = fragment.fragmentManager
        try{
            (fragment as HomeFragment).fadeIn()
        }catch (e:Exception){}
        fm?.beginTransaction()
            ?.hide(fragment)
            ?.add(fragment.id,f)
            ?.addToBackStack(null)
            ?.commit()



    }




    protected fun getMore(){
       duckduck.moreImages {
           Handler(Looper.getMainLooper()).post {
               notifyDataSetChanged()
               requested = false
           }
       }
    }




}