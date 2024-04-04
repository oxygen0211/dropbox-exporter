package com.ridesmoto.dropboxexporter.processor;

import com.dropbox.core.DbxDownloader;
import com.dropbox.core.DbxException;
import com.dropbox.core.InvalidAccessTokenException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class DownloadTask implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(DownloadTask.class);
    private static final String DOWNLOAD_TIMER_NAME = "dropbox_file_download_duration";
    private final String path;
    private final String destinationPath;
    private final DbxClientV2 dropbox;
    private final FileMetadata metadata;

    public DownloadTask(FileMetadata metadata, String path, String destinationPath, DbxClientV2 dropbox) {
        this.metadata = metadata;
        this.path = path;
        this.destinationPath = destinationPath;
        this.dropbox = dropbox;
    }

    @Override
    public void run() {
        Timer timer = Metrics.globalRegistry.timer(DOWNLOAD_TIMER_NAME, "path", path);
        try {
            timer.record(() -> {
                try {
                    load();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            LOG.error("Error downloading {}", path, e);
        }
    }

    private void load() throws DbxException, IOException {
        Metrics.globalRegistry.timer(DOWNLOAD_TIMER_NAME);
        try {
            try (DbxDownloader<FileMetadata> downloader = dropbox.files().download(path)) {
                File destinationFile = new File(destinationPath);

                if (!destinationFile.getParentFile().exists()) {
                    destinationFile.getParentFile().mkdirs();
                }

                if (!destinationFile.exists()) {
                    destinationFile.createNewFile();
                } else if (metadata.getSize() == destinationFile.length()) {
                    LOG.info("{} already seems to be downloaded to {}. Skipping", path, destinationPath);
                    return;
                }

                FileOutputStream out = new FileOutputStream(destinationFile);

                LOG.info("Downloading {} to {}", path, destinationPath);
                downloader.download(out, new DownloadProgressListener(path, metadata.getSize()));
            }
            LOG.info("Downloaded {}", path);
        } catch (InvalidAccessTokenException e) {
            LOG.info("Got invalid token, refreshing and retrying download...");
            dropbox.refreshAccessToken();
            load();
        }
    }
}
