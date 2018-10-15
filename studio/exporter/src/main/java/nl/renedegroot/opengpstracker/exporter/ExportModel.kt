/*
 * Open GPS Tracker
 * Copyright (C) 2018  René de Groot
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package nl.renedegroot.opengpstracker.exporter

import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableInt

/**
 * View model for the export preparation fragment
 */
internal class ExportModel {
    val isDriveConnected = ObservableBoolean(false)
    val isTrackerConnected = ObservableBoolean(false)
    val isRunning = ObservableBoolean(false)
    val isFinished = ObservableBoolean(false)

    val completedTracks = ObservableInt(0)
    val totalTracks = ObservableInt(0)
    val totalWaypoints = ObservableInt(0)
    val completedWaypoints = ObservableInt(0)

    fun updateExportProgress(isRunning: Boolean?, isFinished: Boolean?, completedTracks: Int?, totalTracks: Int?, completedWaypoints: Int?, totalWaypoints: Int?) {
        this.isRunning.set(isRunning ?: this.isRunning.get())
        this.isFinished.set(isFinished ?: this.isFinished.get())
        this.completedTracks.set(completedTracks ?: this.completedTracks.get())
        this.totalTracks.set(totalTracks ?: this.totalTracks.get())
        this.completedWaypoints.set(completedWaypoints ?: this.completedWaypoints.get())
        this.totalWaypoints.set(totalWaypoints ?: this.totalWaypoints.get())
    }
}