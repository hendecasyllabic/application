package org.collectionspace.chain.csp.persistence.services;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import javax.activation.DataSource;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

public class StreamDataSource implements DataSource {
	private String mime_type;
	private byte[] data;
	
	public StreamDataSource(InputStream source,String mime_type) throws IOException {
		this.mime_type=mime_type;
		data=IOUtils.toByteArray(source);
	}
	
	public String getContentType() { return mime_type; }

	public InputStream getInputStream() throws IOException { return new ByteArrayInputStream(data); }

	public String getName() { return "[a stream]"; }

	public OutputStream getOutputStream() throws IOException {
		throw new IOException(getClass().getCanonicalName()+" is readonly");
	}

}