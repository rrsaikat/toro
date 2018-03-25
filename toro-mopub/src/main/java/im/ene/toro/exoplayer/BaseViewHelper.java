/*
 * Copyright (c) 2018 Nam Nguyen, nam@ene.im
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.ene.toro.exoplayer;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import im.ene.toro.ToroPlayer;
import im.ene.toro.ToroUtil;
import im.ene.toro.helper.ToroPlayerHelper;
import im.ene.toro.media.PlaybackInfo;
import im.ene.toro.media.VolumeInfo;

/**
 * Common implementation for {@link Playable}.
 *
 * @author eneim (2018/03/20).
 * @since 3.4.2
 */
@SuppressWarnings("WeakerAccess") //
abstract class BaseViewHelper<VIEW extends View> extends ToroPlayerHelper {

  @NonNull protected final Playable<VIEW> playable;
  @NonNull protected final MyEventListeners listeners;

  BaseViewHelper(@NonNull ToroPlayer player, @NonNull Uri uri, String extension,
      @NonNull ExoCreator creator) {
    super(player);
    listeners = new MyEventListeners();
    playable = ToroUtil.checkNotNull(requirePlayable(creator, uri, extension));
  }

  @Override public void initialize(@Nullable PlaybackInfo playbackInfo) {
    playable.addEventListener(listeners);
    playable.prepare(false);
    //noinspection unchecked
    playable.setPlayerView((VIEW) player.getPlayerView());
    if (playbackInfo != null) playable.setPlaybackInfo(playbackInfo);
  }

  @Override public void release() {
    super.release();
    playable.setPlayerView(null);
    playable.removeEventListener(listeners);
    playable.release();
  }

  @Override public void play() {
    playable.play();
  }

  @Override public void pause() {
    playable.pause();
  }

  @Override public boolean isPlaying() {
    return playable.isPlaying();
  }

  @Override public void setVolume(float volume) {
    playable.setVolume(volume);
  }

  @Override public float getVolume() {
    return playable.getVolume();
  }

  @Override public void setVolumeInfo(@NonNull VolumeInfo volumeInfo) {
    boolean volumeChanged = playable.setVolumeInfo(volumeInfo);
    if (volumeChanged && volumeChangeListener != null) {
      volumeChangeListener.onVolumeChanged(volumeInfo);
    }
  }

  @Override @NonNull public VolumeInfo getVolumeInfo() {
    return playable.getVolumeInfo();
  }

  @NonNull @Override public PlaybackInfo getLatestPlaybackInfo() {
    return playable.getPlaybackInfo();
  }

  @SuppressWarnings("WeakerAccess") //
  public void addEventListener(@NonNull Playable.EventListener listener) {
    //noinspection ConstantConditions
    if (listener != null) this.listeners.add(listener);
  }

  @SuppressWarnings("WeakerAccess") //
  public void removeEventListener(Playable.EventListener listener) {
    this.listeners.remove(listener);
  }

  // A proxy, to also hook into ToroPlayerHelper's state change event.
  private class MyEventListeners extends Playable.EventListeners {

    MyEventListeners() {
    }

    @Override public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
      BaseViewHelper.super.onPlayerStateUpdated(playWhenReady, playbackState); // important
      super.onPlayerStateChanged(playWhenReady, playbackState);
    }
  }

  @NonNull
  abstract Playable<VIEW> requirePlayable(ExoCreator creator, @NonNull Uri uri, String extension);
}