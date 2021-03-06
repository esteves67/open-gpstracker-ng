/*------------------------------------------------------------------------------
 **     Ident: Sogeti Smart Mobile Solutions
 **    Author: rene
 ** Copyright: (c) 2017 Sogeti Nederland B.V. All Rights Reserved.
 **------------------------------------------------------------------------------
 ** Sogeti Nederland B.V.            |  No part of this file may be reproduced
 ** Distributed Software Engineering |  or transmitted in any form or by any
 ** Lange Dreef 17                   |  means, electronic or mechanical, for the
 ** 4131 NJ Vianen                   |  purpose, without the express written
 ** The Netherlands                  |  permission of the copyright holder.
 *------------------------------------------------------------------------------
 *
 *   This file is part of OpenGPSTracker.
 *
 *   OpenGPSTracker is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   OpenGPSTracker is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with OpenGPSTracker.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package nl.sogeti.android.gpstracker.ng.features.tracklist

import android.content.Context
import androidx.databinding.DataBindingUtil
import android.net.Uri
import androidx.annotation.MainThread
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import nl.sogeti.android.gpstracker.ng.base.common.onMainThread
import nl.sogeti.android.gpstracker.ng.features.FeatureConfiguration
import nl.sogeti.android.gpstracker.ng.features.map.rendering.TrackPolylineProvider
import nl.sogeti.android.gpstracker.ng.features.summary.SummaryManager
import nl.sogeti.android.gpstracker.ng.features.trackedit.readTrackType
import nl.sogeti.android.gpstracker.ng.features.util.mapLatLng
import nl.sogeti.android.gpstracker.service.integration.ContentConstants.Waypoints.WAYPOINTS
import nl.sogeti.android.gpstracker.service.util.readName
import nl.sogeti.android.gpstracker.utils.concurrent.BackgroundThreadFactory
import nl.sogeti.android.gpstracker.utils.contentprovider.append
import nl.sogeti.android.gpstracker.utils.contentprovider.count
import nl.sogeti.android.gpstracker.v2.sharedwear.util.StatisticsFormatter
import nl.sogeti.android.opengpstrack.ng.features.R
import nl.sogeti.android.opengpstrack.ng.features.databinding.RowTrackBinding
import java.util.concurrent.Executors
import javax.inject.Inject

class TrackListViewAdapter(val context: Context) : androidx.recyclerview.widget.RecyclerView.Adapter<TrackListViewAdapter.ViewHolder>() {

    private val executor = Executors.newFixedThreadPool(1, BackgroundThreadFactory("TrackListDiffer"))
    private var layoutManager: androidx.recyclerview.widget.RecyclerView.LayoutManager? = null
    private val rowModels = mutableMapOf<Uri, TrackViewModel>()
    private var displayedTracks = listOf<Uri>()
    private var newTracks: List<Uri>? = null
    private var calculatingTracks: List<Uri>? = null
    @Inject
    lateinit var summaryManager: SummaryManager
    @Inject
    lateinit var statisticsFormatter: StatisticsFormatter

    var listener: TrackListAdapterListener? = null
    var selection: Uri? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    init {
        FeatureConfiguration.featureComponent.inject(this)
        setHasStableIds(true)
    }

    @MainThread
    fun updateTracks(tracks: List<Uri>) {
        newTracks = tracks
        if (calculatingTracks == null) {
            calculatingTracks = newTracks
            newTracks = null
            executor.submit {
                calculatingTracks?.let {
                    val diffResult = DiffUtil.calculateDiff(TrackDiffer(displayedTracks, it))

                    onMainThread {
                        displayedTracks = it
                        diffResult.dispatchUpdatesTo(this)
                        calculatingTracks = null
                        newTracks?.let {
                            updateTracks(it)
                        }
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return displayedTracks.size
    }

    override fun getItemId(position: Int): Long {
        return displayedTracks[position].lastPathSegment.toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = DataBindingUtil.inflate<RowTrackBinding>(LayoutInflater.from(context), R.layout.row_track, parent, false)
        val holder = ViewHolder(binding)
        // Weirdly enough the 'clickable="false"' in the XML resource doesn't work
        holder.binding.rowTrackMap.isClickable = false
        holder.binding.adapter = this
        holder.binding.rowTrackMap.onCreate(null)

        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val viewModel = rowViewModelForUri(displayedTracks[position])
        if (holder.binding.viewModel != viewModel) {
            holder.binding.viewModel = viewModel
        }
        holder.binding.rowTrackCard.isActivated = (viewModel.uri == selection)
        willDisplayTrack(holder.itemView.context, viewModel)
    }

    override fun onAttachedToRecyclerView(recyclerView: androidx.recyclerview.widget.RecyclerView) {
        layoutManager = recyclerView.layoutManager
    }

    override fun onDetachedFromRecyclerView(recyclerView: androidx.recyclerview.widget.RecyclerView) {
        layoutManager = null
    }

    fun isDisplayed(position: Int): Boolean {
        val layoutManager = layoutManager ?: return false
        if (layoutManager is androidx.recyclerview.widget.LinearLayoutManager) {
            return position >= layoutManager.findFirstVisibleItemPosition()
                    && position <= layoutManager.findLastVisibleItemPosition()
        }

        return false
    }

    //region Row callbacks

    fun didSelectTrack(trackModel: TrackViewModel) {
        listener?.didSelectTrack(trackModel.uri, trackModel.name.get() ?: "")
        trackModel.editMode.set(false)
    }

    fun didShareTrack(trackModel: TrackViewModel) {
        listener?.didSelectExportTrack(trackModel.uri)
        trackModel.editMode.set(false)
    }

    fun didEditTrack(trackModel: TrackViewModel) {
        listener?.didEditTrack(trackModel.uri)
        trackModel.editMode.set(false)
    }

    fun didDeleteTrack(trackModel: TrackViewModel) {
        listener?.didDeleteTrack(trackModel.uri)
        trackModel.editMode.set(false)
    }

    fun didClickRowOptions(track: TrackViewModel) {
        val opposite = !track.editMode.get()
        track.editMode.set(opposite)
    }

    //endregion

    private fun rowViewModelForUri(uri: Uri): TrackViewModel {
        var viewModel = rowModels[uri]
        if (viewModel == null) {
            viewModel = TrackViewModel(uri)
            rowModels[uri] = viewModel
        }

        return viewModel
    }

    private fun willDisplayTrack(context: Context, viewModel: TrackViewModel) {
        summaryManager.collectSummaryInfo(viewModel.uri) {
            if (it.trackUri == viewModel.uri) {
                viewModel.completeBounds.set(it.bounds)
                val listOfLatLng = it.waypoints.map { it.map { it.mapLatLng() } }
                viewModel.waypoints.set(listOfLatLng)
                val trackPolylineProvider = TrackPolylineProvider(listOfLatLng)
                viewModel.polylines.set(trackPolylineProvider.lineOptions)
                viewModel.iconType.set(it.type.drawableId)
                viewModel.name.set(it.name)
                viewModel.startDay.set(statisticsFormatter.convertTimestampToStart(context, it.startTimestamp))
                var duration = context.getString(R.string.empty_dash)
                if (it.startTimestamp in 1..(it.stopTimestamp - 1)) {
                    duration = statisticsFormatter.convertSpanDescriptiveDuration(context, it.stopTimestamp - it.startTimestamp)
                }
                viewModel.duration.set(duration)
                var distance = context.getString(R.string.empty_dash)
                if (it.distance > 0) {
                    distance = statisticsFormatter.convertMetersToDistance(context, it.distance)
                }
                viewModel.distance.set(distance)
            }
        }
    }

    class ViewHolder(val binding: RowTrackBinding) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)

    inner class TrackDiffer(private val oldList: List<Uri>, private val newList: List<Uri>) : DiffUtil.Callback() {
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            if (!isDisplayed(oldItemPosition) && !isDisplayed(newItemPosition)) {
                // Offscreen rows we only care about the URI
                return oldItem == newItem
            }

            if (rowViewModelForUri(oldItem).name.get() != newItem.readName()) {
                return false
            }
            if (rowViewModelForUri(oldItem).iconType.get() != newItem.readTrackType().drawableId) {
                return false
            }

            val renderedWaypoints = rowViewModelForUri(oldItem).waypoints.get()
            val oldCount = renderedWaypoints?.count() ?: -1
            val newCount = newItem.append(WAYPOINTS).count(context.contentResolver)

            return oldCount == newCount
        }
    }
}

