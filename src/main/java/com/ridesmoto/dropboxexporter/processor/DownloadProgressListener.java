package com.ridesmoto.dropboxexporter.processor;

import com.dropbox.core.util.IOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DownloadProgressListener implements IOUtil.ProgressListener {
    private static final Logger LOG = LoggerFactory.getLogger(DownloadTask.class);
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
        if(percentage - lastPercentage >= 5) {
            LOG.info("{} - Download Progress: {}%", path, percentage);
            lastPercentage = percentage;
        }
    }
}
