package com.ridesmoto.dropboxexporter;

import com.ridesmoto.dropboxexporter.processor.DropboxExporter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DropboxExporterApplication {

	public static void main(String[] args) {
		SpringApplication.run(DropboxExporterApplication.class, args);
	}

}
