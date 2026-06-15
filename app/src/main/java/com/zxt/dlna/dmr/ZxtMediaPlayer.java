package com.zxt.dlna.dmr;

import java.net.URI;
import java.util.logging.Logger;

import org.fourthline.cling.model.ModelUtil;
import org.fourthline.cling.model.types.UnsignedIntegerFourBytes;
import org.fourthline.cling.support.avtransport.lastchange.AVTransportVariable;
import org.fourthline.cling.support.lastchange.LastChange;
import org.fourthline.cling.support.model.Channel;
import org.fourthline.cling.support.model.MediaInfo;
import org.fourthline.cling.support.model.PositionInfo;
import org.fourthline.cling.support.model.StorageMedium;
import org.fourthline.cling.support.model.TransportAction;
import org.fourthline.cling.support.model.TransportInfo;
import org.fourthline.cling.support.model.TransportState;
import org.fourthline.cling.support.renderingcontrol.lastchange.ChannelMute;
import org.fourthline.cling.support.renderingcontrol.lastchange.ChannelVolume;
import org.fourthline.cling.support.renderingcontrol.lastchange.RenderingControlVariable;

import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.util.Log;

/**
 * ZxtMediaPlayer acts as a UPnP command relay for CasterTV.
 * Manages transport state and delegates playback to CastCoordinatorService via PlaybackListener.
 */
public class ZxtMediaPlayer {

    final private static Logger log = Logger.getLogger(ZxtMediaPlayer.class.getName());
    private static final String TAG = "ZxtMediaPlayer";

    final private UnsignedIntegerFourBytes instanceId;
    final private LastChange avTransportLastChange;
    final private LastChange renderingControlLastChange;

    // We'll synchronize read/writes to these fields
    private volatile TransportInfo currentTransportInfo = new TransportInfo();
    private PositionInfo currentPositionInfo = new PositionInfo();
    private MediaInfo currentMediaInfo = new MediaInfo();
    private double storedVolume;

    private Context mContext;
    private String currentURI = "";

    private PlaybackListener playbackListener;

    /**
     * Interface for playback events
     */
    public interface PlaybackListener {
        void onPlay(Uri uri);
        void onPause();
        void onStop();
        void onSeek(long positionMs);
    }

    public ZxtMediaPlayer(UnsignedIntegerFourBytes instanceId, Context context,
                          LastChange avTransportLastChange,
                          LastChange renderingControlLastChange) {
        super();
        this.instanceId = instanceId;
        this.mContext = context;
        this.avTransportLastChange = avTransportLastChange;
        this.renderingControlLastChange = renderingControlLastChange;
    }

    public void setPlaybackListener(PlaybackListener listener) {
        this.playbackListener = listener;
    }

    public UnsignedIntegerFourBytes getInstanceId() {
        return instanceId;
    }

    public LastChange getAvTransportLastChange() {
        return avTransportLastChange;
    }

    public LastChange getRenderingControlLastChange() {
        return renderingControlLastChange;
    }

    synchronized public TransportInfo getCurrentTransportInfo() {
        return currentTransportInfo;
    }

    synchronized public PositionInfo getCurrentPositionInfo() {
        return currentPositionInfo;
    }

    synchronized public MediaInfo getCurrentMediaInfo() {
        return currentMediaInfo;
    }

    synchronized public void setURI(URI uri, String type, String name, String currentURIMetaData) {
        Log.i(TAG, "setURI " + uri);

        currentURI = uri.toString();
        currentMediaInfo = new MediaInfo(uri.toString(), currentURIMetaData);
        currentPositionInfo = new PositionInfo(1, "", uri.toString());

        getAvTransportLastChange().setEventedValue(getInstanceId(),
                new AVTransportVariable.AVTransportURI(uri),
                new AVTransportVariable.CurrentTrackURI(uri));

        transportStateChanged(TransportState.STOPPED);

        // Notify listener
        if (playbackListener != null) {
            playbackListener.onPlay(Uri.parse(uri.toString()));
        }

        Log.i(TAG, "URI set and playback initiated: " + uri);
    }

    synchronized public void setVolume(double volume) {
        Log.i(TAG, "setVolume " + volume);
        storedVolume = getVolume();

        // Update volume in Android audio system
        try {
            AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int targetVolume = (int) (volume * maxVolume);
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to set system volume", e);
        }

        ChannelMute switchedMute =
                (storedVolume == 0 && volume > 0) || (storedVolume > 0 && volume == 0)
                        ? new ChannelMute(Channel.Master, storedVolume > 0 && volume == 0)
                        : null;

        getRenderingControlLastChange().setEventedValue(
                getInstanceId(),
                new RenderingControlVariable.Volume(
                        new ChannelVolume(Channel.Master, (int) (volume * 100))
                ),
                switchedMute != null
                        ? new RenderingControlVariable.Mute(switchedMute)
                        : null
        );
    }

    synchronized public void setMute(boolean desiredMute) {
        if (desiredMute && getVolume() > 0) {
            log.fine("Switching mute ON");
            setVolume(0);
        } else if (!desiredMute && getVolume() == 0) {
            log.fine("Switching mute OFF, restoring: " + storedVolume);
            setVolume(storedVolume);
        }
    }

    synchronized public TransportAction[] getCurrentTransportActions() {
        TransportState state = currentTransportInfo.getCurrentTransportState();
        TransportAction[] actions;

        switch (state) {
            case STOPPED:
            case NO_MEDIA_PRESENT:
                actions = new TransportAction[]{
                        TransportAction.Play
                };
                break;
            case PLAYING:
                actions = new TransportAction[]{
                        TransportAction.Stop,
                        TransportAction.Pause,
                        TransportAction.Seek
                };
                break;
            case PAUSED_PLAYBACK:
                actions = new TransportAction[]{
                        TransportAction.Stop,
                        TransportAction.Pause,
                        TransportAction.Seek,
                        TransportAction.Play
                };
                break;
            case TRANSITIONING:
                actions = new TransportAction[]{
                        TransportAction.Stop
                };
                break;
            default:
                actions = null;
        }
        return actions;
    }

    synchronized protected void transportStateChanged(TransportState newState) {
        TransportState currentTransportState = currentTransportInfo.getCurrentTransportState();
        log.fine("Current state is: " + currentTransportState + ", changing to new state: " + newState);
        currentTransportInfo = new TransportInfo(newState);

        getAvTransportLastChange().setEventedValue(
                getInstanceId(),
                new AVTransportVariable.TransportState(newState),
                new AVTransportVariable.CurrentTransportActions(getCurrentTransportActions())
        );
    }

    public class GstMediaListener {
        public void pause() {
            transportStateChanged(TransportState.PAUSED_PLAYBACK);
        }

        public void start() {
            transportStateChanged(TransportState.PLAYING);
        }

        public void stop() {
            transportStateChanged(TransportState.STOPPED);
        }

        public void endOfMedia() {
            log.fine("End Of Media event received");
            transportStateChanged(TransportState.NO_MEDIA_PRESENT);
        }

        public void positionChanged(int position) {
            log.fine("Position Changed event received: " + position);
            synchronized (ZxtMediaPlayer.this) {
                currentPositionInfo = new PositionInfo(1, currentMediaInfo.getMediaDuration(),
                        currentMediaInfo.getCurrentURI(), ModelUtil.toTimeString(position/1000),
                        ModelUtil.toTimeString(position/1000));
            }
        }

        public void durationChanged(int duration) {
            log.fine("Duration Changed event received: " + duration);
            synchronized (ZxtMediaPlayer.this) {
                String newValue = ModelUtil.toTimeString(duration/1000);
                currentMediaInfo = new MediaInfo(currentMediaInfo.getCurrentURI(), "",
                        new UnsignedIntegerFourBytes(1), newValue, StorageMedium.NETWORK);

                getAvTransportLastChange().setEventedValue(getInstanceId(),
                        new AVTransportVariable.CurrentTrackDuration(newValue),
                        new AVTransportVariable.CurrentMediaDuration(newValue));
            }
        }
    }

    public double getVolume() {
        try {
            AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                double v = (double) audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        / audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                Log.i(TAG, "getVolume " + v);
                return v;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get volume", e);
        }
        return 1.0;
    }

    public void play() {
        Log.i(TAG, "play");

        transportStateChanged(TransportState.PLAYING);

        // Notify listener
        if (playbackListener != null) {
            playbackListener.onPlay(Uri.parse(currentURI));
        }
    }

    public void pause() {
        Log.i(TAG, "pause");

        transportStateChanged(TransportState.PAUSED_PLAYBACK);

        // Notify listener
        if (playbackListener != null) {
            playbackListener.onPause();
        }
    }

    public void stop() {
        Log.i(TAG, "stop");

        transportStateChanged(TransportState.STOPPED);

        // Notify listener
        if (playbackListener != null) {
            playbackListener.onStop();
        }
    }

    public void seek(int position) {
        Log.i(TAG, "seek " + position);

        // Update position info
        synchronized (this) {
            currentPositionInfo = new PositionInfo(1, currentMediaInfo.getMediaDuration(),
                    currentURI, ModelUtil.toTimeString(position/1000),
                    ModelUtil.toTimeString(position/1000));
        }

        // Notify listener
        if (playbackListener != null) {
            playbackListener.onSeek(position);
        }
    }

    public String getCurrentURI() {
        return currentURI;
    }
}
