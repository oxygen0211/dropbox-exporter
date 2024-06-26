package com.ridesmoto.dropboxexporter.processor;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.InvalidAccessTokenException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import io.micrometer.core.instrument.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class DropboxExporter {
    private static final Logger LOG = LoggerFactory.getLogger(DropboxExporter.class);
    private static final String FILES_GAUGE_NAME = "dropbox_files_to_load";
    private static final String LOADED_GAUGE_NAME = "dropbox_files_loaded";

    @Value("${dropbox.source}")
    private String sourceDir;

    @Value("${dropbox.destination}")
    private String destDir;

    private final DbxClientV2 dropbox;
    private final ExecutorService downloadExecutor;

    public DropboxExporter(@Value("${dropbox.identifier}") String identifier, @Value("${dropbox.accesstoken}") String accesstoken, @Value("${dropbox.download.threads}") int threads) {
        DbxRequestConfig config = DbxRequestConfig.newBuilder(identifier).build();
        dropbox = new DbxClientV2(config, accesstoken);
        downloadExecutor = Executors.newFixedThreadPool(threads);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void exportFromConfig() throws DbxException, IOException, InterruptedException, ExecutionException {

        downloadExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    String username = "anonymous";
                    try {
                        username = dropbox.users().getCurrentAccount().getName().getDisplayName();
                    } catch (InvalidAccessTokenException e) {
                        LOG.info("Access token has expired. Try refreshing and then fetch username again...");
                        dropbox.refreshAccessToken();
                        username = dropbox.users().getCurrentAccount().getName().getDisplayName();
                    }
                    LOG.info("Exporting directory {} of account {} to {} as specified in application config...", sourceDir, username, destDir);

                    List<FileMetadata> downloadableFiles = listFilesInDir(sourceDir);

                    Metrics.globalRegistry.gauge(FILES_GAUGE_NAME, downloadableFiles.size());
                    LOG.info("Found {} downloadable files in {}", downloadableFiles.size(), sourceDir);

                    List<Future> downloadFutures = new ArrayList<>(downloadableFiles.size());
                    for (FileMetadata meta : downloadableFiles) {
                        downloadFutures.add(downloadFile(meta));
                    }

                    boolean finished = false;
                    while (!finished) {
                        List<Future> downloadedFiles = downloadFutures.stream().filter(Future::isDone).toList();
                        LOG.info("{}/{} Files Downloaded", downloadedFiles.size(), downloadFutures.size());
                        Metrics.globalRegistry.gauge(LOADED_GAUGE_NAME, downloadedFiles.size());
                        finished = downloadedFiles.size() >= downloadFutures.size();

                        Thread.sleep(5000);
                    }
                } catch (Exception e) {
                    LOG.info("Error during background download of files", e);
                }
            }
        });
    }

    private List<FileMetadata> listFilesInDir(String directory) throws DbxException {
        List<FileMetadata> files = new ArrayList<>();
        List<String> subDirs = new ArrayList<>();
        ListFolderResult result;
        try {
            result = dropbox.files().listFolder(directory);
        } catch (InvalidAccessTokenException e) {
            LOG.info("Access token has expired. Try refreshing and then fetch again...");
            dropbox.refreshAccessToken();
            result = dropbox.files().listFolder(directory);
        }


        while (true) {
            for (Metadata meta : result.getEntries()) {
                String path = meta.getPathLower();

                if (meta instanceof FolderMetadata) {
                    subDirs.add(path);
                } else if (meta instanceof FileMetadata) {
                    files.add((FileMetadata) meta);
                }
            }

            if (!result.getHasMore()) {
                break;
            }

            try {
                result = dropbox.files().listFolderContinue(result.getCursor());
            } catch (InvalidAccessTokenException e) {
                LOG.info("Access token has expired. Try refreshing and then fetch again...");
                dropbox.refreshAccessToken();
                result = dropbox.files().listFolderContinue(result.getCursor());
            }
        }
        LOG.info("Found {} files and {} folders in {}", files.size(), subDirs.size(), directory);

        for (String folder : subDirs) {
            LOG.info("Fetching content information in {}...", folder);
            files.addAll(listFilesInDir(folder));
        }
        return files;
    }

    private Future<?> downloadFile(FileMetadata metadata) throws DbxException, IOException {
        String path = metadata.getPathLower();
        String subPath = path.substring(sourceDir.length());
        String destinationPath = destDir + subPath;

        return downloadExecutor.submit(new DownloadTask(metadata, path, destinationPath, dropbox));
    }

}
