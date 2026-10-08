package org.thoughtcrime.securesms.gcm;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobs.FcmRefreshJob;
import org.thoughtcrime.securesms.jobs.SubmitRateLimitPushChallengeJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.registration.fcm.PushChallengeRequest;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.NetworkUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.Locale;

public class FcmReceiveService extends FirebaseMessagingService {

  private static final String TAG = Log.tag(FcmReceiveService.class);

  @Override
  public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
    if (KeyCachingService.isLocked()) {
      if (remoteMessage.getPriority() == RemoteMessage.PRIORITY_HIGH &&
          TextSecurePreferences.isPassphraseLockNotificationsEnabled(this)) {
        Log.d(TAG, "New urgent message received while app is locked.");
        FcmFetchManager.postMayHaveMessagesNotification(this);
      }
      return;
    }

    Log.i(TAG, String.format(Locale.US,
                             "onMessageReceived() ID: %s, Delay: %d (Server offset: %d), Priority: %d, Original Priority: %d, Network: %s",
                             remoteMessage.getMessageId(),
                             (System.currentTimeMillis() - remoteMessage.getSentTime()),
                             SignalStore.misc().getLastKnownServerTimeOffset(),
                             remoteMessage.getPriority(),
                             remoteMessage.getOriginalPriority(),
                             NetworkUtil.getNetworkStatus(this)));

    String registrationChallenge   = remoteMessage.getData().get("challenge");
    String rateLimitChallenge      = remoteMessage.getData().get("rateLimitChallenge");
    String verificationCodeRequest = remoteMessage.getData().get("verificationCodeRequested");

    if (registrationChallenge != null) {
      handleRegistrationPushChallenge(registrationChallenge);
    } else if (rateLimitChallenge != null) {
      handleRateLimitPushChallenge(rateLimitChallenge);
    } else if (verificationCodeRequest != null && SignalStore.account().isPrimaryDevice()) {
      handleVerificationCodeRequested(verificationCodeRequest, remoteMessage.getSentTime());
    } else {
      FcmFetchManager.onPushReceived(AppDependencies.getApplication(),
                                     remoteMessage.getPriority() == RemoteMessage.PRIORITY_HIGH);
    }
  }

  @Override
  public void onDeletedMessages() {
    if (KeyCachingService.isLocked()) {
      return;
    }

    Log.w(TAG, "onDeleteMessages() -- Messages may have been dropped. Doing a normal message fetch.");
    FcmFetchManager.onPushReceived(AppDependencies.getApplication(), false);
  }

  @Override
  public void onNewToken(@NonNull String token) {
    Log.i(TAG, "onNewToken()");

    if (KeyCachingService.isLocked()) {
      TextSecurePreferences.setShouldRefreshFcmToken(AppDependencies.getApplication(), true);
      return;
    }

    if (!SignalStore.account().isRegistered()) {
      Log.i(TAG, "Got a new FCM token, but the user isn't registered.");
      return;
    }

    AppDependencies.getJobManager().add(new FcmRefreshJob());
  }

  @Override
  public void onMessageSent(@NonNull String s) {
    Log.i(TAG, "onMessageSent()" + s);
  }

  @Override
  public void onSendError(@NonNull String s, @NonNull Exception e) {
    Log.w(TAG, "onSendError()", e);
  }

  private static void handleRegistrationPushChallenge(@NonNull String challenge) {
    Log.d(TAG, "Got a registration push challenge.");
    PushChallengeRequest.postChallengeResponse(challenge);
  }

  private static void handleRateLimitPushChallenge(@NonNull String challenge) {
    Log.d(TAG, "Got a rate limit push challenge.");
    AppDependencies.getJobManager().add(new SubmitRateLimitPushChallengeJob(challenge));
  }

  private static void handleVerificationCodeRequested(String verificationCodeRequestJson, long sentTime) {
    Log.i(TAG, "Got a verification code requested push.");

    VerificationCodeRequestedPush verificationRequestedPush = VerificationCodeRequestedPush.fromJson(verificationCodeRequestJson);

    long requestedAt;
    if (verificationRequestedPush != null && verificationRequestedPush.getTimestamp() != null) {
      requestedAt = verificationRequestedPush.getTimestamp();
    } else {
      Log.w(TAG, "Unable to parse requested at timestamp from server, using sent time instead");
      requestedAt = sentTime;
    }

    SignalStore.account().setVerificationCodeRequestedAtMs(requestedAt);
  }
}