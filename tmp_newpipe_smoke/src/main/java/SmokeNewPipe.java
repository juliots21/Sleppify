import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamExtractor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.RequestBody;

public final class SmokeNewPipe {

    private static final String VIDEO_ID = "jNQXAC9IVRw";

    public static void main(String[] args) {
        try {
            YoutubeParsingHelper.setConsentAccepted(true);
            NewPipe.init(new OkHttpDownloader(), Localization.DEFAULT);
            System.out.println("NewPipe initialized with downloader: " + NewPipe.getDownloader());

            List<String> urls = new ArrayList<>();
            urls.add("https://www.youtube.com/watch?v=" + VIDEO_ID);
            urls.add("https://www.youtube.com/embed/" + VIDEO_ID);

            for (String url : urls) {
                probeUrlSafely(url);
            }
        } catch (Exception e) {
            System.out.println("Smoke test failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace(System.out);
            System.exit(1);
        }
    }

    private static void probeUrlSafely(String url) {
        try {
            probeUrl(url);
        } catch (Exception e) {
            System.out.println("url=" + url);
            System.out.println("fetch_status=failed");
            System.out.println("error_type=" + e.getClass().getSimpleName());
            System.out.println("error_message=" + e.getMessage());
            System.out.println("-----");
        }
    }

    private static void probeUrl(String url) throws Exception {
        StreamExtractor extractor = ServiceList.YouTube.getStreamExtractor(url);
        System.out.println("url=" + url);
        System.out.println("resolved_id=" + extractor.getId());
        extractor.fetchPage();

        List<AudioStream> audioStreams = extractor.getAudioStreams();
        AudioStream bestM4a = audioStreams.stream()
                .filter(stream -> stream.getFormat() == MediaFormat.M4A)
                .max(Comparator.comparingInt(SmokeNewPipe::streamBitrate))
                .orElse(null);

        AudioStream selected = bestM4a;
        if (selected == null) {
            selected = audioStreams.stream()
                    .max(Comparator.comparingInt(SmokeNewPipe::streamBitrate))
                    .orElse(null);
        }

        if (selected == null) {
            throw new IllegalStateException("No audio streams found for URL: " + url);
        }

        String format = selected.getFormat() == null
                ? "unknown"
                : selected.getFormat().name().toLowerCase(Locale.US);

        System.out.println("title=" + extractor.getName());
        System.out.println("audio_streams_count=" + audioStreams.size());
        System.out.println("selected_format=" + format);
        System.out.println("selected_bitrate=" + streamBitrate(selected));
        System.out.println("selected_url=" + selected.getContent());
        System.out.println("-----");
    }

    private static int streamBitrate(AudioStream stream) {
        int bitrate = stream.getBitrate();
        if (bitrate > 0) {
            return bitrate;
        }

        bitrate = stream.getAverageBitrate();
        if (bitrate > 0) {
            return bitrate;
        }

        return -1;
    }

    private static final class OkHttpDownloader extends Downloader {
        private static final String USER_AGENT =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0";

        private final OkHttpClient client = new OkHttpClient.Builder().build();

        @Override
        public Response execute(Request request) throws IOException, ReCaptchaException {
            String method = request.httpMethod();
            byte[] dataToSend = request.dataToSend();

            RequestBody body = null;
            if (dataToSend != null) {
                body = RequestBody.create(dataToSend);
            } else if ("POST".equalsIgnoreCase(method)
                    || "PUT".equalsIgnoreCase(method)
                    || "PATCH".equalsIgnoreCase(method)) {
                body = RequestBody.create(new byte[0]);
            }

            okhttp3.Request.Builder builder = new okhttp3.Request.Builder()
                    .url(request.url())
                    .method(method, body)
                    .header("User-Agent", USER_AGENT);

            for (Map.Entry<String, List<String>> header : request.headers().entrySet()) {
                String name = header.getKey();
                List<String> values = header.getValue();
                if (name == null || values == null) {
                    continue;
                }

                builder.removeHeader(name);
                for (String value : values) {
                    if (value != null) {
                        builder.addHeader(name, value);
                    }
                }
            }

            try (okhttp3.Response httpResponse = client.newCall(builder.build()).execute()) {
                String responseBody = "";
                if (httpResponse.body() != null) {
                    responseBody = httpResponse.body().string();
                }

                return new Response(
                        httpResponse.code(),
                        httpResponse.message(),
                        httpResponse.headers().toMultimap(),
                        responseBody,
                        httpResponse.request().url().toString()
                );
            }
        }
    }
}
