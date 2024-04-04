package com.ridesmoto.dropboxexporter.processor;

import com.dropbox.core.util.IOUtil;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

public class DownloadProgressListener implements IOUtil.ProgressListener {
    private static final Logger LOG = LoggerFactory.getLogger(DownloadTask.class);
    private static final String PERCENTAGE_GAUGE_NAME = "dropbox_download_percentage";
    private static final String LOADED_GAUGE_NAME = "dropbox_download_bytes";

    private final long fileSize;
    private final String path;

    private int lastPercentage = 0;

    public DownloadProgressListener(String path, long fileSize) {
        this.fileSize = fileSize;
        this.path = path;
    }
    @Override
    public void onProgress(long l) {
        int percentage = (int) (((double) l / (double) fileSize) * 100.0);
        float fileMB = fileSize /1000f / 1000f;
        float loadedMB = l /1000f / 1000f;

        Iterable<Tag> tags = List.of(new ImmutableTag("path", path));

        Metrics.globalRegistry.gauge(PERCENTAGE_GAUGE_NAME, tags, percentage);
        Metrics.globalRegistry.gauge(LOADED_GAUGE_NAME, tags, l);
        if(percentage - lastPercentage >= 5) {
            LOG.info("{} - Download Progress: {}% ({} of {} MB)", path, percentage, loadedMB, fileMB);
            lastPercentage = percentage;
        }
    }
}
