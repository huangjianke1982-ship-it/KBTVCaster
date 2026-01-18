
package com.zxt.dlna.dmr;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.fourthline.cling.model.types.UnsignedIntegerFourBytes;
import org.fourthline.cling.support.lastchange.LastChange;
import org.fourthline.cling.support.model.TransportState;

import android.content.Context;
import android.net.Uri;

/**
 * @author offbye
 */
public class ZxtMediaPlayers extends ConcurrentHashMap<UnsignedIntegerFourBytes, ZxtMediaPlayer> {

    final private static Logger log = Logger.getLogger(ZxtMediaPlayers.class.getName());

    final protected LastChange avTransportLastChange;
    final protected LastChange renderingControlLastChange;
    private Context mContext;
    private ZxtMediaPlayer.PlaybackListener playbackListener;


    public ZxtMediaPlayers(int numberOfPlayers,
                           Context context,
                           LastChange avTransportLastChange,
                           LastChange renderingControlLastChange) {
        super(numberOfPlayers);
        this.mContext = context;
        this.avTransportLastChange = avTransportLastChange;
        this.renderingControlLastChange = renderingControlLastChange;

        for (int i = 0; i < numberOfPlayers; i++) {

            ZxtMediaPlayer player =
                    new ZxtMediaPlayer(
                            new UnsignedIntegerFourBytes(i),
                            mContext,
                            avTransportLastChange,
                            renderingControlLastChange
                    ) {
                        @Override
                        protected void transportStateChanged(TransportState newState) {
                            super.transportStateChanged(newState);
                            if (newState.equals(TransportState.PLAYING)) {
                                onPlay(this);
                            } else if (newState.equals(TransportState.STOPPED)) {
                                onStop(this);
                            }
                        }
                    };
            // Set playback listener if available
            if (playbackListener != null) {
                player.setPlaybackListener(playbackListener);
            }
            put(player.getInstanceId(), player);
        }
    }

    /**
     * Set playback listener for all players
     */
    public void setPlaybackListener(ZxtMediaPlayer.PlaybackListener listener) {
        this.playbackListener = listener;
        for (ZxtMediaPlayer player : values()) {
            player.setPlaybackListener(listener);
        }
    }

    protected void onPlay(ZxtMediaPlayer player) {
        log.fine("Player is playing: " + player.getInstanceId());
    }

    protected void onStop(ZxtMediaPlayer player) {
        log.fine("Player is stopping: " + player.getInstanceId());
    }
}
