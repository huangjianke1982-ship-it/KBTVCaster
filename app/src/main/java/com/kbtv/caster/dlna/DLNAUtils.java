package com.kbtv.caster.dlna;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import com.zxt.dlna.dmr.ZxtMediaPlayer;
import com.zxt.dlna.dmr.ZxtMediaRenderer;

import org.fourthline.cling.android.AndroidUpnpService;
import org.fourthline.cling.android.AndroidUpnpServiceImpl;

import com.kbtv.caster.R;

public class DLNAUtils {
    private static String TAG = "DLNAUtils";
    private static ZxtMediaRenderer mMediaRenderer = null;
    private static ZxtMediaPlayer.PlaybackListener mPlaybackListener = null;

    public static void setDLNANameSuffix(Context context, String nameSuffix){
        SharedPreferences sharedPreferences = context.getApplicationContext().getSharedPreferences("dlna_settings", 0);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("nameSuffix", nameSuffix);
        editor.commit();
        startDLNAService(context);
    }

    public static String getDLNANameSuffix(Context context){
        SharedPreferences sharedPreferences = context.getApplicationContext().getSharedPreferences("dlna_settings", 0);
        return sharedPreferences.getString("nameSuffix", "");
    }

    /**
     * Set playback listener for media playback control
     */
    public static void setPlaybackListener(ZxtMediaPlayer.PlaybackListener listener) {
        mPlaybackListener = listener;
        if (mMediaRenderer != null) {
            // Set listener on all players
            for (ZxtMediaPlayer player : mMediaRenderer.getMediaPlayers().values()) {
                player.setPlaybackListener(listener);
            }
        }
    }

    public static void startDLNAService(final Context context){
        ServiceConnection serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                AndroidUpnpService upnpService = (AndroidUpnpService) service;
                Log.i(TAG, "DLNA: onServiceConnected");
                String dlnaName = context.getString(R.string.app_name);
                String dlnaNameSuffix = getDLNANameSuffix(context);
                if(!TextUtils.isEmpty(dlnaNameSuffix)) dlnaName += "(" + dlnaNameSuffix + ")";
                if(mMediaRenderer != null){
                    mMediaRenderer.stopAllMediaPlayers();
                    upnpService.getRegistry().removeAllLocalDevices();
                    mMediaRenderer = null;
                }
                mMediaRenderer = new ZxtMediaRenderer(1, dlnaName , context);
                upnpService.getRegistry().addDevice(mMediaRenderer.getDevice());

                // Set playback listener if available
                if (mPlaybackListener != null) {
                    for (ZxtMediaPlayer player : mMediaRenderer.getMediaPlayers().values()) {
                        player.setPlaybackListener(mPlaybackListener);
                    }
                }

                Log.i(TAG, context.getString(R.string.app_name) + " DLNA服务已启动");
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.i(TAG, "DLNA: onServiceDisconnected");
            }
        };
        context.bindService(
                new Intent(context, AndroidUpnpServiceImpl.class),
                serviceConnection, Context.BIND_AUTO_CREATE);
    }

    public static void stopDLNAService(){
        if(mMediaRenderer != null){
            mMediaRenderer.stopAllMediaPlayers();
        }
    }
}
