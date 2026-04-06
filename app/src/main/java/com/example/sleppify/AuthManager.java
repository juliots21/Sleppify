package com.example.sleppify;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public final class AuthManager {

    public interface AuthCallback {
        void onSuccess(@NonNull FirebaseUser user);
        void onError(@NonNull String message);
    }

    public interface SimpleCallback {
        void onComplete(boolean success, @Nullable String message);
    }

    private static volatile AuthManager instance;

    private final Context appContext;
    private final FirebaseAuth firebaseAuth;
    private final CredentialManager credentialManager;

    private AuthManager(@NonNull Context context) {
        appContext = context.getApplicationContext();
        firebaseAuth = FirebaseAuth.getInstance();
        credentialManager = CredentialManager.create(appContext);
    }

    @NonNull
    public static AuthManager getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (AuthManager.class) {
                if (instance == null) {
                    instance = new AuthManager(context);
                }
            }
        }
        return instance;
    }

    public boolean isSignedIn() {
        return firebaseAuth.getCurrentUser() != null;
    }

    @Nullable
    public FirebaseUser getCurrentUser() {
        return firebaseAuth.getCurrentUser();
    }

    @Nullable
    public String getUserId() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    @NonNull
    public String getDisplayName() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null || TextUtils.isEmpty(user.getDisplayName())) {
            return "Invitado";
        }
        return user.getDisplayName();
    }

    @NonNull
    public String getEmail() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null || TextUtils.isEmpty(user.getEmail())) {
            return "Sin correo";
        }
        return user.getEmail();
    }

    @Nullable
    public Uri getPhotoUrl() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        return user != null ? user.getPhotoUrl() : null;
    }

    public void signIn(@NonNull Activity activity, @NonNull AuthCallback callback) {
        FirebaseUser alreadySigned = firebaseAuth.getCurrentUser();
        if (alreadySigned != null) {
            callback.onSuccess(alreadySigned);
            return;
        }

        String serverClientId;
        try {
            int resId = activity.getResources().getIdentifier(
                    "default_web_client_id",
                    "string",
                    activity.getPackageName()
            );
            serverClientId = resId == 0 ? "" : activity.getString(resId);
        } catch (Exception ignored) {
            callback.onError("No se encontro default_web_client_id. Verifica google-services.json.");
            return;
        }

        if (TextUtils.isEmpty(serverClientId)) {
            callback.onError("default_web_client_id esta vacio. Revisa Firebase y google-services.json.");
            return;
        }

        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(true)
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        credentialManager.getCredentialAsync(
                activity,
                request,
                null,
            ContextCompat.getMainExecutor(activity),
                new androidx.credentials.CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        handleCredentialResponse(activity, result, callback);
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        callback.onError("No fue posible iniciar sesion con Google: " + e.getMessage());
                    }
                }
        );
    }

    private void handleCredentialResponse(
            @NonNull Activity activity,
            @NonNull GetCredentialResponse response,
            @NonNull AuthCallback callback
    ) {
        Credential credential = response.getCredential();
        if (!(credential instanceof CustomCredential)) {
            callback.onError("No se recibio una credencial de Google valida.");
            return;
        }

        CustomCredential customCredential = (CustomCredential) credential;
        if (!GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(customCredential.getType())) {
            callback.onError("Tipo de credencial no soportado para Google Sign-In.");
            return;
        }

        GoogleIdTokenCredential googleIdTokenCredential;
        try {
            googleIdTokenCredential = GoogleIdTokenCredential.createFrom(customCredential.getData());
        } catch (Exception e) {
            callback.onError("No se pudo leer el token de Google.");
            return;
        }

        AuthCredential firebaseCredential = GoogleAuthProvider.getCredential(
                googleIdTokenCredential.getIdToken(),
                null
        );

        firebaseAuth.signInWithCredential(firebaseCredential)
                .addOnCompleteListener(activity, task -> {
                    if (!task.isSuccessful()) {
                        Exception exception = task.getException();
                        String reason = exception != null ? exception.getMessage() : "error desconocido";
                        callback.onError("Firebase rechazo el inicio de sesion: " + reason);
                        return;
                    }

                    FirebaseUser signedUser = firebaseAuth.getCurrentUser();
                    if (signedUser == null) {
                        callback.onError("Firebase no devolvio un usuario activo.");
                        return;
                    }

                    callback.onSuccess(signedUser);
                });
    }

    public void signOut(@NonNull SimpleCallback callback) {
        firebaseAuth.signOut();

        clearCredentialState(callback);
    }

    public void deleteCurrentUser(@NonNull SimpleCallback callback) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            callback.onComplete(false, "No hay sesion activa para eliminar.");
            return;
        }

        user.delete()
                .addOnSuccessListener(unused -> {
                    firebaseAuth.signOut();
                    clearCredentialState(callback);
                })
                .addOnFailureListener(e -> {
                    if (e instanceof FirebaseAuthRecentLoginRequiredException) {
                        callback.onComplete(false, "Por seguridad, vuelve a iniciar sesion y repite la eliminacion.");
                        return;
                    }

                    String message = e != null && !TextUtils.isEmpty(e.getMessage())
                            ? e.getMessage()
                            : "No se pudo eliminar la cuenta.";
                    callback.onComplete(false, message);
                });
    }

    private void clearCredentialState(@NonNull SimpleCallback callback) {

        try {
            credentialManager.clearCredentialStateAsync(
                    new ClearCredentialStateRequest(),
                    null,
                    ContextCompat.getMainExecutor(appContext),
                    new androidx.credentials.CredentialManagerCallback<Void, ClearCredentialException>() {
                        @Override
                        public void onResult(Void result) {
                            callback.onComplete(true, null);
                        }

                        @Override
                        public void onError(@NonNull ClearCredentialException e) {
                            callback.onComplete(true, null);
                        }
                    }
            );
        } catch (Throwable ignored) {
            callback.onComplete(true, null);
        }
    }
}
