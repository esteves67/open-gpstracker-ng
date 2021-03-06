/*------------------------------------------------------------------------------
 **     Ident: Sogeti Smart Mobile Solutions
 **    Author: René de Groot
 ** Copyright: (c) 2016 Sogeti Nederland B.V. All Rights Reserved.
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
package nl.sogeti.android.gpstracker.ng.features.control

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.WorkerThread
import nl.sogeti.android.gpstracker.ng.base.dagger.DiskIO
import nl.sogeti.android.gpstracker.ng.features.FeatureConfiguration
import nl.sogeti.android.gpstracker.ng.features.model.TrackSelection
import nl.sogeti.android.gpstracker.ng.features.trackedit.NameGenerator
import nl.sogeti.android.gpstracker.ng.features.util.AbstractPresenter
import nl.sogeti.android.gpstracker.ng.features.util.LoggingStateController
import nl.sogeti.android.gpstracker.ng.features.util.LoggingStateListener
import nl.sogeti.android.gpstracker.service.integration.ServiceCommanderInterface
import nl.sogeti.android.gpstracker.service.integration.ServiceConstants
import nl.sogeti.android.gpstracker.service.integration.ServiceConstants.*
import nl.sogeti.android.gpstracker.service.util.trackUri
import nl.sogeti.android.gpstracker.service.util.updateName
import nl.sogeti.android.gpstracker.service.util.waypointsUri
import nl.sogeti.android.gpstracker.utils.contentprovider.runQuery
import java.util.*
import java.util.concurrent.Executor
import javax.inject.Inject

class ControlPresenter : AbstractPresenter(), LoggingStateListener {

    internal val viewModel = ControlViewModel()
    private val handler = Handler(Looper.getMainLooper())
    private val enableRunnable = { enableButtons() }
    @Inject
    lateinit var nameGenerator: NameGenerator
    @Inject
    @field:DiskIO
    lateinit var executor: Executor
    @Inject
    lateinit var loggingStateController: LoggingStateController
    @Inject
    lateinit var serviceCommander: ServiceCommanderInterface
    @Inject
    lateinit var trackSelection: TrackSelection
    @Inject
    lateinit var contentResolver: ContentResolver

    init {
        FeatureConfiguration.featureComponent.inject(this)
    }

    override fun onFirstStart() {
        super.onFirstStart()
        loggingStateController.connect(this)
    }

    @WorkerThread
    override fun onChange() {
        val state = loggingStateController.loggingState
        viewModel.state.set(state)
        if (state != ServiceConstants.STATE_UNKNOWN) {
            enableButtons()
        }

        loggingStateController.trackUri?.let {
            if (state == STATE_LOGGING) {
                executor.execute {
                    if (serviceCommander.hasForInitialName(it)) {
                        val generatedName = nameGenerator.generateName(Calendar.getInstance())
                        it.updateName(generatedName)
                    }
                }
            }
            trackSelection.selection.postValue(it)
        }
    }

    override fun onCleared() {
        loggingStateController.disconnect()
        super.onCleared()
    }

    //region Service connection

    override fun didConnectToService(context: Context, loggingState: Int, trackUri: Uri?) {
        didChangeLoggingState(context, loggingState, trackUri)
    }

    override fun didChangeLoggingState(context: Context, loggingState: Int, trackUri: Uri?) {
        markDirty()
    }

    //endregion

    //region View callback

    fun onClickLeft() {
        disableUntilChange(200)
        if (viewModel.state.get() == STATE_LOGGING) {
            stopLogging()
        } else if (viewModel.state.get() == STATE_PAUSED) {
            stopLogging()
        }
    }

    fun onClickRight() {
        disableUntilChange(200)
        when {
            viewModel.state.get() == STATE_STOPPED -> startLogging()
            viewModel.state.get() == STATE_LOGGING -> pauseLogging()
            viewModel.state.get() == STATE_PAUSED -> resumeLogging()
        }
    }

    //endregion

    private fun disableUntilChange(timeout: Long) {
        viewModel.enabled.set(false)
        handler.postDelayed(enableRunnable, timeout)
    }

    private fun enableButtons() {
        handler.removeCallbacks { enableRunnable }
        viewModel.enabled.set(true)
    }

    fun startLogging() {
        serviceCommander.startGPSLogging()
    }

    fun stopLogging() {
        serviceCommander.stopGPSLogging()
        deleteEmptyTrack()
    }

    fun pauseLogging() {
        serviceCommander.pauseGPSLogging()
    }

    fun resumeLogging() {
        serviceCommander.resumeGPSLogging()
    }

    private fun deleteEmptyTrack() {
        val trackId = loggingStateController.trackUri?.lastPathSegment?.toLongOrNull() ?: -1
        if (trackId <= 0) {
            return
        }

        val waypointsUri = waypointsUri(trackId)
        val firstWaypointId = waypointsUri.runQuery(contentResolver) { it.getLong(0) } ?: -1L
        if (firstWaypointId == -1L) {
            contentResolver.delete(trackUri(trackId), null, null)
        }
    }
}
