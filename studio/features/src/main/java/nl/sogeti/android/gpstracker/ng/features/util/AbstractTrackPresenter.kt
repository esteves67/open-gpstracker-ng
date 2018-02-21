package nl.sogeti.android.gpstracker.ng.features.util

import android.arch.lifecycle.ViewModel
import android.net.Uri
import android.support.annotation.CallSuper
import nl.sogeti.android.gpstracker.ng.base.common.controllers.content.ContentController
import nl.sogeti.android.gpstracker.ng.base.common.controllers.content.ContentControllerFactory
import nl.sogeti.android.gpstracker.ng.base.model.TrackSelection
import nl.sogeti.android.gpstracker.ng.features.FeatureConfiguration
import javax.inject.Inject

abstract class AbstractTrackPresenter : ViewModel(), TrackSelection.Listener, ContentController.Listener {
    @Inject
    lateinit var trackSelection: TrackSelection

    @Inject
    lateinit var contentControllerFactory: ContentControllerFactory
    private var started = false

    private var dirty = false
    private lateinit var trackUri: Uri
    private lateinit var trackName: String
    private val contentController: ContentController
    init {
        FeatureConfiguration.featureComponent.inject(this)
        trackSelection.addListener(this)
        contentController = contentControllerFactory.createContentController(this)

        val selectedTrack = trackSelection.trackUri
        selectedTrack?.let {
            onTrackSelection(selectedTrack, trackSelection.trackName)
        }
    }

    fun start() {
        started = true
        onStart()
        checkUpdate()
    }

    fun stop() {
        started = false
        onStop()
    }

    @CallSuper
    override fun onCleared() {
        contentController.unregisterObserver()
        trackSelection.removeListener(this)
        super.onCleared()
    }

    final override fun onTrackSelection(trackUri: Uri, trackName: String) {
        this.trackUri = trackUri
        this.trackName = trackName
        contentController.registerObserver(trackUri)
        dirty = true
        checkUpdate()
    }

    override fun onChangeUriContent(contentUri: Uri, changesUri: Uri) {
        dirty = true
        checkUpdate()
    }

    private fun checkUpdate() {
        if (dirty && started) {
            dirty = false
            onTrackUpdate(trackUri, trackName)
        }
    }

    abstract fun onTrackUpdate(trackUri: Uri, name: String)

    abstract fun onStart()
    abstract fun onStop()
}
