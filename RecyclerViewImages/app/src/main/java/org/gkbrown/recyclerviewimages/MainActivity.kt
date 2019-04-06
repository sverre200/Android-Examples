package org.gkbrown.recyclerviewimages

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.AsyncTask
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.android.synthetic.main.activity_main.*
import org.gkbrown.kilo.WebServiceProxy
import java.lang.ref.WeakReference
import java.net.URL

class BackgroundTask<A: Activity, R>(activity: A,
    private val task: () -> R,
    private val resultHandler: (activity: A?, result: R?, exception: Exception?) -> Unit
) : AsyncTask<Unit, Unit, R?>() {
    private val activityReference = WeakReference<A>(activity)

    private var result: R? = null
    private var exception: Exception? = null

    override fun doInBackground(vararg params: Unit?): R? {
        try {
            result = task()
        } catch (exception: Exception) {
            this.exception = exception
        }

        return result
    }

    override fun onPostExecute(result: R?) {
        resultHandler(activityReference.get(), result, exception)
    }
}

fun <A: Activity, R> A.doInBackground(task: () -> R, resultHandler: (activity: A?, result: R?, exception: Exception?) -> Unit) {
    BackgroundTask(this, task, resultHandler).execute()
}

class MainActivity : AppCompatActivity() {
    // Photo class
    class Photo (map: Map<String, Any>) {
        val id = map["id"] as Int
        val title = map["title"] as String
        val thumbnailUrl = URL(map["thumbnailUrl"] as String)
    }

    // Photo view holder
    inner class PhotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.imageView)
        val textView: TextView = view.findViewById(R.id.textView)
    }

    // Photo adapter
    inner class PhotoAdapter : RecyclerView.Adapter<PhotoViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
            return PhotoViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_photo, parent, false))
        }

        override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
            val photo = photos!![position]

            // Attempt to load image from cache
            val thumbnail = photoThumbnails[photo.id]

            holder.imageView.setImageBitmap(thumbnail)

            if (thumbnail == null) {
                // Request image
                doInBackground({
                    val webServiceProxy = WebServiceProxy("GET", photo.thumbnailUrl)

                    webServiceProxy.invoke { inputStream, _ -> BitmapFactory.decodeStream(inputStream) }
                }) { activity, result, _ ->
                    // Add image to cache and update view holder, if visible
                    if (result != null) {
                        photoThumbnails[photo.id] = result

                        val viewHolder = activity?.recyclerView?.findViewHolderForAdapterPosition(position) as? PhotoViewHolder

                        viewHolder?.imageView?.setImageBitmap(result)
                    }
                }
            }

            holder.textView.text = photo.title
        }

        override fun getItemCount(): Int {
            return photos?.size ?: 0
        }
    }

    // Photo list
    var photos: List<Photo>? = null

    // Thumbnail cache
    val photoThumbnails = HashMap<Int, Bitmap>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = PhotoAdapter()
    }

    override fun onResume() {
        super.onResume()

        // Load photo data
        if (photos == null) {
            doInBackground({
                val webServiceProxy = WebServiceProxy("GET", URL("https://jsonplaceholder.typicode.com/photos"))

                val photos = webServiceProxy.invoke { inputStream, _ -> ObjectMapper().readValue(inputStream, List::class.java) }

                photos.map { @Suppress("UNCHECKED_CAST") Photo(it as Map<String, Any>) }
            }) { activity, result, exception ->
                if (exception == null) {
                    photos = result

                    activity?.recyclerView?.adapter?.notifyDataSetChanged()
                } else {
                    println(exception.message)
                }
            }
        }
    }
}
