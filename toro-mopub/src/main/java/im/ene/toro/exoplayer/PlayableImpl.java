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
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import im.ene.toro.media.PlaybackInfo;
import im.ene.toro.media.VolumeInfo;

import static im.ene.toro.ToroUtil.checkNotNull;
import static im.ene.toro.exoplayer.ToroExo.with;
import static im.ene.toro.media.PlaybackInfo.INDEX_UNSET;
import static im.ene.toro.media.PlaybackInfo.TIME_UNSET;

/**
 * [20180225]
 *
 * Instance of {@link Playable} should be reusable. Retaining instance of Playable across config
 * change must guarantee that all {@link EventListener} are cleaned up on config change.
 *
 * @author eneim (2018/02/25).
 */
@SuppressWarnings("WeakerAccess") class PlayableImpl implements Playable<SimpleExoPlayerView> {

  private final PlaybackInfo playbackInfo = new PlaybackInfo(); // never expose to outside.
  private final VolumeInfo volumeInfo = new VolumeInfo(false, 1); // init value.
  private final EventListeners listeners = new EventListeners();  // original listener.

  protected final Uri mediaUri; // immutable, parcelable
  protected final String fileExt;
  protected final ExoCreator creator; // required, cached

  protected SimpleExoPlayer player; // on-demand, cached
  protected MediaSource mediaSource;  // on-demand
  protected SimpleExoPlayerView playerView; // on-demand, not always required.

  private boolean listenerApplied = false;

  PlayableImpl(ExoCreator creator, Uri uri, String fileExt) {
    this.creator = creator;
    this.mediaUri = uri;
    this.fileExt = fileExt;
  }

  @CallSuper @Override public void prepare(boolean prepareSource) {
    if (player == null) {
      player = with(checkNotNull(creator.getContext(), "ExoCreator has no Context")) //
          .requestPlayer(creator);
    }

    if (!listenerApplied) {
      player.addListener(listeners);
      player.setVideoListener(listeners);
      player.setTextOutput(listeners);
      player.setMetadataOutput(listeners);
      listenerApplied = true;
    }

    if (playerView != null && playerView.getPlayer() != player) playerView.setPlayer(player);
    boolean haveResumePosition = playbackInfo.getResumeWindow() != C.INDEX_UNSET;
    if (haveResumePosition) {
      player.seekTo(playbackInfo.getResumeWindow(), playbackInfo.getResumePosition());
    }

    if (prepareSource) {
      if (mediaSource == null) {  // Only actually prepare the source when play() is called.
        mediaSource = creator.createMediaSource(mediaUri, fileExt);
        player.prepare(mediaSource, playbackInfo.getResumeWindow() == C.INDEX_UNSET, false);
      }
    }
  }

  @CallSuper @Override public void setPlayerView(@Nullable SimpleExoPlayerView playerView) {
    if (this.playerView == playerView) return;
    if (playerView == null) {
      this.playerView.setPlayer(null);
    } else {
      // playerView is non-null, we requires a non-null player too.
      if (this.player == null) {
        throw new IllegalStateException("Player is null, prepare it first.");
      }
      SimpleExoPlayerView.switchTargetView(this.player, this.playerView, playerView);
    }

    this.playerView = playerView;
  }

  @Override public final SimpleExoPlayerView getPlayerView() {
    return this.playerView;
  }

  @CallSuper @Override public void play() {
    checkNotNull(player, "Playable#play(): Player is null!");
    if (mediaSource == null) {  // Only actually prepare the source when play() is called.
      mediaSource = creator.createMediaSource(mediaUri, fileExt);
      player.prepare(mediaSource, playbackInfo.getResumeWindow() == C.INDEX_UNSET, false);
    }
    player.setPlayWhenReady(true);
  }

  @CallSuper @Override public void pause() {
    checkNotNull(player, "Playable#pause(): Player is null!").setPlayWhenReady(false);
  }

  @CallSuper @Override public void reset() {
    this.playbackInfo.reset();
    if (player != null) player.stop();
    this.mediaSource = null; // so it will be re-prepared when play() is called.
  }

  @CallSuper @Override public void release() {
    this.setPlayerView(null);
    if (this.player != null) {
      this.player.stop();
      if (listenerApplied) {
        player.removeListener(listeners);
        player.setVideoListener(null);
        player.setTextOutput(null);
        player.setMetadataOutput(null);
        listenerApplied = false;
      }
      with(checkNotNull(creator.getContext(), "ExoCreator has no Context")) //
          .releasePlayer(this.creator, this.player);
    }
    this.player = null;
    this.mediaSource = null;
  }

  @CallSuper @NonNull @Override public PlaybackInfo getPlaybackInfo() {
    updatePlaybackInfo();
    return new PlaybackInfo(playbackInfo.getResumeWindow(), playbackInfo.getResumePosition());
  }

  @CallSuper @Override public void setPlaybackInfo(@NonNull PlaybackInfo playbackInfo) {
    this.playbackInfo.setResumeWindow(playbackInfo.getResumeWindow());
    this.playbackInfo.setResumePosition(playbackInfo.getResumePosition());

    if (player != null) {
      boolean haveResumePosition = this.playbackInfo.getResumeWindow() != INDEX_UNSET;
      if (haveResumePosition) {
        player.seekTo(this.playbackInfo.getResumeWindow(), this.playbackInfo.getResumePosition());
      }
    }
  }

  @Override public final void addEventListener(@NonNull EventListener listener) {
    //noinspection ConstantConditions
    if (listener != null) this.listeners.add(listener);
  }

  @Override public final void removeEventListener(EventListener listener) {
    this.listeners.remove(listener);
  }

  @CallSuper @Override public void setVolume(float volume) {
    checkNotNull(player, "Playable#setVolume(): Player is null!").setVolume(volume);
  }

  @CallSuper @Override public float getVolume() {
    return checkNotNull(player, "Playable#getVolume(): Player is null!").getVolume();
  }

  @Override public boolean setVolumeInfo(@NonNull VolumeInfo volumeInfo) {
    boolean changed = !this.volumeInfo.equals(checkNotNull(volumeInfo));
    if (changed) {
      this.volumeInfo.setTo(volumeInfo.isMute(), volumeInfo.getVolume());
      if (this.volumeInfo.isMute()) {
        this.setVolume(0.f);
      } else {
        this.setVolume(this.volumeInfo.getVolume());
      }
    }
    return changed;
  }

  @NonNull @Override public VolumeInfo getVolumeInfo() {
    return this.volumeInfo;
  }

  @Override public void setParameters(@Nullable PlaybackParameters parameters) {
    checkNotNull(player, "Playable#setParameters(PlaybackParameters): Player is null") //
        .setPlaybackParameters(parameters);
  }

  @Override public PlaybackParameters getParameters() {
    return checkNotNull(player,
        "Playable#getParameters(): Player is null").getPlaybackParameters();
  }

  @Override public boolean isPlaying() {
    return player != null && player.getPlayWhenReady();
  }

  final void updatePlaybackInfo() {
    if (player == null || player.getPlaybackState() == ExoPlayer.STATE_IDLE) return;
    playbackInfo.setResumeWindow(player.getCurrentWindowIndex());
    playbackInfo.setResumePosition(player.isCurrentWindowSeekable() ? //
        Math.max(0, player.getCurrentPosition()) : TIME_UNSET);
  }
}
