package com.ridesmoto.dropboxexporter.processor;

import com.dropbox.core.DbxDownloader;
import com.dropbox.core.util.IOUtil;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;

public class DownloadTask implements Runnable{
    private static final Logger LOG = LoggerFactory.getLogger(DownloadTask.class);
    private final String path;
    private final String destinationPath;
    private final DbxClientV2 dropbox;
    private FileMetadata metadata;

    public DownloadTask(FileMetadata metadata, String path, String destinationPath, DbxClientV2 dropbox) {
        this.metadata = metadata;
        this.path = path;
        this.destinationPath = destinationPath;
        this.dropbox = dropbox;
    }

    @Override
    public void run() {
        LOG.info("Downloading {} to {}", path, destinationPath);
        try {
            try (DbxDownloader<FileMetadata> downloader = dropbox.files().download(path)){
                File destinationFile = new File(destinationPath);

                if(!destinationFile.getParentFile().exists()) {
                    destinationFile.getParentFile().mkdirs();
                }

                if(!destinationFile.exists()) {
                    destinationFile.createNewFile();
                }

                FileOutputStream out = new FileOutputStream(destinationFile);
                downloader.download(out, new DownloadProgressListener(path, metadata.getSize()));
            }

            LOG.info("Downloaded {}", path);
        }
        catch (Exception e) {
            LOG.error("Error downloading {}", path, e);
        }
    }
}
