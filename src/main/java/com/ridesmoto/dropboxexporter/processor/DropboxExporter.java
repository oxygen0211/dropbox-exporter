package com.ridesmoto.dropboxexporter.processor;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.*;
import java.sql.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Service
public class DropboxExporter {
    private static final Logger LOG = LoggerFactory.getLogger(DropboxExporter.class);

    @Value("${dropbox.source}")
    private String sourceDir;

    @Value("${dropbox.destination}")
    private String destDir;

    private DbxClientV2 dropbox;
    private ExecutorService downloadExecutor;

    public DropboxExporter( @Value("${dropbox.identifier}") String identifier, @Value("${dropbox.accesstoken}") String accesstoken, @Value("${dropbox.download.threads}") int threads) {
        DbxRequestConfig config = DbxRequestConfig.newBuilder(identifier).build();
        dropbox = new DbxClientV2(config, accesstoken);
        downloadExecutor = Executors.newFixedThreadPool(threads);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void exportFromConfig() throws DbxException, IOException, InterruptedException, ExecutionException {
        LOG.info("Exporting directory {} of account {} to {} as specified in application config...", sourceDir, dropbox.users().getCurrentAccount().getName().getDisplayName(), destDir);

        List<FileMetadata> downloadableFiles = listFilesInDir(sourceDir);

        LOG.info("Found {} downloadable files in {}", downloadableFiles.size(), sourceDir);

        List<Future> downloadFutures = new ArrayList<>(downloadableFiles.size());
        for(FileMetadata meta : downloadableFiles) {
            downloadFutures.add(downloadFile(meta));
        }

        boolean finished = false;
        while(!finished) {
            List<Future> downloadedFiles  = downloadFutures.stream().filter(Future::isDone).toList();
            LOG.info("{}/{} Files Downloaded", downloadedFiles.size(), downloadFutures.size());
            finished = downloadedFiles.size() >= downloadFutures.size();

            Thread.sleep(5000);
        }
    }

    private List<FileMetadata> listFilesInDir(String directory) throws DbxException {
        List<FileMetadata> files = new ArrayList<>();
        List<String> subDirs = new ArrayList<>();
        ListFolderResult result = dropbox.files().listFolder(directory);

        while(true) {
            for (Metadata meta : result.getEntries()) {
                String path = meta.getPathLower();

                if (meta instanceof FolderMetadata) {
                    subDirs.add(path);
                }

                else if (meta instanceof FileMetadata) {
                    files.add((FileMetadata) meta);
                }
            }

            if(!result.getHasMore()) {
                break;
            }
            result = dropbox.files().listFolderContinue(result.getCursor());
        }
        LOG.info("Found {} files and {} folders in {}", files.size(), subDirs.size(), directory);

        for(String folder : subDirs) {
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
