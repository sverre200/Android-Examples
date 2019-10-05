/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import org.httprpc.WebServiceProxy
import java.lang.ref.WeakReference
import java.net.URL

class BackgroundTask<A: Activity, R>(activity: A,
    private val task: () -> R,
    private val resultHandler: (activity: A?, result: Result<R>) -> Unit
) : AsyncTask<Unit, Unit, R?>() {
    private val activityReference = WeakReference<A>(activity)

    private var value: R? = null
    private var exception: Exception? = null

    override fun doInBackground(vararg params: Unit?): R? {
        try {
            value = task()
        } catch (exception: Exception) {
            this.exception = exception
        }

        return value
    }

    override fun onPostExecute(value: R?) {
        resultHandler(activityReference.get(), if (exception == null) {
            Result.success(value!!)
        } else {
            Result.failure(exception!!)
        })
    }
}

fun <A: Activity, R> A.doInBackground(task: () -> R, resultHandler: (activity: A?, result: Result<R>) -> Unit) {
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

                    webServiceProxy.invoke { inputStream, _, _ -> BitmapFactory.decodeStream(inputStream) }
                }) { activity, result ->
                    // Add image to cache and update view holder, if visible
                    result.onSuccess { value ->
                        photoThumbnails[photo.id] = value

                        val viewHolder = activity?.recyclerView?.findViewHolderForAdapterPosition(position) as? PhotoViewHolder

                        viewHolder?.imageView?.setImageBitmap(value)
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

                val photos = webServiceProxy.invoke { inputStream, _, _ -> ObjectMapper().readValue(inputStream, List::class.java) }

                photos.map { @Suppress("UNCHECKED_CAST") Photo(it as Map<String, Any>) }
            }) { activity, result ->
                result.onSuccess { value ->
                    photos = value

                    activity?.recyclerView?.adapter?.notifyDataSetChanged()
                }.onFailure { exception ->
                    println(exception.message)
                }
            }
        }
    }
}
