package com.example.sleppify;

import android.app.Application;
import android.util.Log;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor;

public final class SleppifyApp extends Application {

    private static final String TAG = "SleppifyApp";

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            YoutubeParsingHelper.setConsentAccepted(true);
            NewPipe.init(NewPipeHttpDownloader.getInstance(), Localization.DEFAULT);
            YoutubeStreamExtractor.setFetchIosClient(true);
            Log.d(TAG, "NewPipe extractor initialized");
        } catch (Exception e) {
            Log.e(TAG, "NewPipe init failed: " + e.getMessage(), e);
        }
    }
}
