/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.util.http.fileupload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

import org.apache.tomcat.util.http.fileupload.MultipartStream.ItemInputStream;
import org.apache.tomcat.util.http.fileupload.util.Closeable;
import org.apache.tomcat.util.http.fileupload.util.FileItemHeadersImpl;
import org.apache.tomcat.util.http.fileupload.util.LimitedInputStream;
import org.apache.tomcat.util.http.fileupload.util.Streams;


/**
 * <p>High level API for processing file uploads.</p>
 *
 * <p>This class handles multiple files per single HTML widget, sent using
 * <code>multipart/mixed</code> encoding type, as specified by
 * <a href="http://www.ietf.org/rfc/rfc1867.txt">RFC 1867</a>.  Use {@link
 * #parseRequest(RequestContext)} to acquire a list of {@link
 * org.apache.tomcat.util.http.fileupload.FileItem}s associated with a given HTML
 * widget.</p>
 *
 * <p>How the data for individual parts is stored is determined by the factory
 * used to create them; a given part may be in memory, on disk, or somewhere
 * else.</p>
 */
public abstract class FileUploadBase {

    // ---------------------------------------------------------- Class methods

    private static final Charset CHARSET_ISO_8859_1 =
        Charset.forName("ISO-8859-1");

    /**
     * <p>Utility method that determines whether the request contains multipart
     * content.</p>
     *
     * <p><strong>NOTE:</strong>This method will be moved to the
     * <code>ServletFileUpload</code> class after the FileUpload 1.1 release.
     * Unfortunately, since this method is static, it is not possible to
     * provide its replacement until this method is removed.</p>
     *
     * @param ctx The request context to be evaluated. Must be non-null.
     *
     * @return <code>true</code> if the request is multipart;
     *         <code>false</code> otherwise.
     */
    public static final boolean isMultipartContent(RequestContext ctx) {
        String contentType = ctx.getContentType();
        if (contentType == null) {
            return false;
        }
        if (contentType.toLowerCase(Locale.ENGLISH).startsWith(MULTIPART)) {
            return true;
        }
        return false;
    }

    // ----------------------------------------------------- Manifest constants

    /**
     * HTTP content type header name.
     */
    public static final String CONTENT_TYPE = "Content-type";

    /**
     * HTTP content disposition header name.
     */
    public static final String CONTENT_DISPOSITION = "Content-disposition";

    /**
     * HTTP content length header name.
     */
    public static final String CONTENT_LENGTH = "Content-length";

    /**
     * Content-disposition value for form data.
     */
    public static final String FORM_DATA = "form-data";

    /**
     * Content-disposition value for file attachment.
     */
    public static final String ATTACHMENT = "attachment";

    /**
     * Part of HTTP content type header.
     */
    public static final String MULTIPART = "multipart/";

    /**
     * HTTP content type header for multipart forms.
     */
    public static final String MULTIPART_FORM_DATA = "multipart/form-data";

    /**
     * HTTP content type header for multiple uploads.
     */
    public static final String MULTIPART_MIXED = "multipart/mixed";

    // ----------------------------------------------------------- Data members

    /**
     * The maximum size permitted for the complete request, as opposed to
     * {@link #fileSizeMax}. A value of -1 indicates no maximum.
     * <p>一个完整的请求允许的最大值，而不是{@link #fileSizeMax}。-1表示没有限制。
     */
    private long sizeMax = -1;

    /**
     * The maximum size permitted for a single uploaded file, as opposed
     * to {@link #sizeMax}. A value of -1 indicates no maximum.
     */
    private long fileSizeMax = -1;

    /**
     * The content encoding to use when reading part headers.
     */
    private String headerEncoding;

    /**
     * The progress listener.
     */
    private ProgressListener listener;

    // ----------------------------------------------------- Property accessors

    /**
     * Returns the factory class used when creating file items.
     *
     * @return The factory class for new file items.
     */
    public abstract FileItemFactory getFileItemFactory();

    /**
     * Sets the factory class to use when creating file items.
     *
     * @param factory The factory class for new file items.
     */
    public abstract void setFileItemFactory(FileItemFactory factory);

    /**
     * Returns the maximum allowed size of a complete request, as opposed
     * to {@link #getFileSizeMax()}.
     *
     * @return The maximum allowed size, in bytes. The default value of
     *   -1 indicates, that there is no limit.
     *
     * @see #setSizeMax(long)
     *
     */
    public long getSizeMax() {
        return sizeMax;
    }

    /**
     * Sets the maximum allowed size of a complete request, as opposed
     * to {@link #setFileSizeMax(long)}.
     *
     * @param sizeMax The maximum allowed size, in bytes. The default value of
     *   -1 indicates, that there is no limit.
     *
     * @see #getSizeMax()
     *
     */
    public void setSizeMax(long sizeMax) {
        this.sizeMax = sizeMax;
    }

    /**
     * Returns the maximum allowed size of a single uploaded file,
     * as opposed to {@link #getSizeMax()}.
     *
     * @see #setFileSizeMax(long)
     * @return Maximum size of a single uploaded file.
     */
    public long getFileSizeMax() {
        return fileSizeMax;
    }

    /**
     * Sets the maximum allowed size of a single uploaded file,
     * as opposed to {@link #getSizeMax()}.
     *
     * @see #getFileSizeMax()
     * @param fileSizeMax Maximum size of a single uploaded file.
     */
    public void setFileSizeMax(long fileSizeMax) {
        this.fileSizeMax = fileSizeMax;
    }

    /**
     * Retrieves the character encoding used when reading the headers of an
     * individual part. When not specified, or <code>null</code>, the request
     * encoding is used. If that is also not specified, or <code>null</code>,
     * the platform default encoding is used.
     *
     * @return The encoding used to read part headers.
     */
    public String getHeaderEncoding() {
        return headerEncoding;
    }

    /**
     * Specifies the character encoding to be used when reading the headers of
     * individual part. When not specified, or <code>null</code>, the request
     * encoding is used. If that is also not specified, or <code>null</code>,
     * the platform default encoding is used.
     *
     * @param encoding The encoding used to read part headers.
     */
    public void setHeaderEncoding(String encoding) {
        headerEncoding = encoding;
    }

    // --------------------------------------------------------- Public methods

    /**
     * Processes an <a href="http://www.ietf.org/rfc/rfc1867.txt">RFC 1867</a>
     * compliant <code>multipart/form-data</code> stream.
     *
     * @param ctx The context for the request to be parsed.
     *
     * @return An iterator to instances of <code>FileItemStream</code>
     *         parsed from the request, in the order that they were
     *         transmitted.
     *
     * @throws FileUploadException if there are problems reading/parsing
     *                             the request or storing files.
     * @throws IOException An I/O error occurred. This may be a network
     *   error while communicating with the client or a problem while
     *   storing the uploaded content.
     */
    public FileItemIterator getItemIterator(RequestContext ctx)
    throws FileUploadException, IOException {
        try {
            return new FileItemIteratorImpl(ctx);
        } catch (FileUploadIOException e) {
            // unwrap encapsulated SizeException
            throw (FileUploadException) e.getCause();
        }
    }

    /**
     * Processes an <a href="http://www.ietf.org/rfc/rfc1867.txt">RFC 1867</a>
     * compliant <code>multipart/form-data</code> stream.
     *
     * @param ctx The context for the request to be parsed.
     *
     * @return A list of <code>FileItem</code> instances parsed from the
     *         request, in the order that they were transmitted.
     *
     * @throws FileUploadException if there are problems reading/parsing
     *                             the request or storing files.
     */
    public List<FileItem> parseRequest(RequestContext ctx)
            throws FileUploadException {
        List<FileItem> items = new ArrayList<FileItem>();
        boolean successful = false;
        try {
            // 构建multipart迭代器
            FileItemIterator iter = getItemIterator(ctx);
            FileItemFactory fac = getFileItemFactory();
            if (fac == null) {
                throw new NullPointerException("No FileItemFactory has been set.");
            }
            while (iter.hasNext()) {
                final FileItemStream item = iter.next();
                // Don't use getName() here to prevent an InvalidFileNameException.
                final String fileName = ((FileItemIteratorImpl.FileItemStreamImpl) item).name;
                FileItem fileItem = fac.createItem(item.getFieldName(), item.getContentType(),
                                                   item.isFormField(), fileName);
                items.add(fileItem);
                try {
                    // 拷贝参数内容
                    Streams.copy(item.openStream(), fileItem.getOutputStream(), true);
                } catch (FileUploadIOException e) {
                    throw (FileUploadException) e.getCause();
                } catch (IOException e) {
                    throw new IOFileUploadException(String.format("Processing of %s request failed. %s",
                                                           MULTIPART_FORM_DATA, e.getMessage()), e);
                }
                final FileItemHeaders fih = item.getHeaders();
                fileItem.setHeaders(fih);
            }
            successful = true;
            return items;
        } catch (FileUploadIOException e) {
            throw (FileUploadException) e.getCause();
        } catch (IOException e) {
            throw new FileUploadException(e.getMessage(), e);
        } finally {
            if (!successful) {
                for (FileItem fileItem : items) {
                    try {
                        fileItem.delete();
                    } catch (Exception ignored) {
                        // ignored TODO perhaps add to tracker delete failure list somehow?
                    }
                }
            }
        }
    }

    /**
     * Processes an <a href="http://www.ietf.org/rfc/rfc1867.txt">RFC 1867</a>
     * compliant <code>multipart/form-data</code> stream.
     *
     * @param ctx The context for the request to be parsed.
     *
     * @return A map of <code>FileItem</code> instances parsed from the request.
     *
     * @throws FileUploadException if there are problems reading/parsing
     *                             the request or storing files.
     *
     * @since 1.3
     */
    public Map<String, List<FileItem>> parseParameterMap(RequestContext ctx)
            throws FileUploadException {
        final List<FileItem> items = parseRequest(ctx);
        final Map<String, List<FileItem>> itemsMap =
                new HashMap<String, List<FileItem>>(items.size());

        for (FileItem fileItem : items) {
            String fieldName = fileItem.getFieldName();
            List<FileItem> mappedItems = itemsMap.get(fieldName);

            if (mappedItems == null) {
                mappedItems = new ArrayList<FileItem>();
                itemsMap.put(fieldName, mappedItems);
            }

            mappedItems.add(fileItem);
        }

        return itemsMap;
    }

    // ------------------------------------------------------ Protected methods

    /**
     * Retrieves the boundary from the <code>Content-type</code> header.
     *
     * <p>从请求头<code>Content-type</code>中获取boundary。
     *
     * @param contentType The value of the content type header from which to
     *                    extract the boundary value.
     *
     * @return The boundary, as a byte array.
     */
    protected byte[] getBoundary(String contentType) {
        ParameterParser parser = new ParameterParser();
        parser.setLowerCaseNames(true);
        // Parameter parser can handle null input
        Map<String,String> params =
                parser.parse(contentType, new char[] {';', ','});
        String boundaryStr = params.get("boundary");

        if (boundaryStr == null) {
            return null;
        }
        byte[] boundary;
        boundary = boundaryStr.getBytes(CHARSET_ISO_8859_1);
        return boundary;
    }

    /**
     * Retrieves the file name from the <code>Content-disposition</code>
     * header.
     *
     * @param headers The HTTP headers object.
     *
     * @return The file name for the current <code>encapsulation</code>.
     */
    protected String getFileName(FileItemHeaders headers) {
        return getFileName(headers.getHeader(CONTENT_DISPOSITION));
    }

    /**
     * Returns the given content-disposition headers file name.
     * @param pContentDisposition The content-disposition headers value.
     * @return The file name
     */
    private String getFileName(String pContentDisposition) {
        String fileName = null;
        if (pContentDisposition != null) {
            String cdl = pContentDisposition.toLowerCase(Locale.ENGLISH);
            if (cdl.startsWith(FORM_DATA) || cdl.startsWith(ATTACHMENT)) {
                ParameterParser parser = new ParameterParser();
                parser.setLowerCaseNames(true);
                // Parameter parser can handle null input
                Map<String,String> params =
                    parser.parse(pContentDisposition, ';');
                if (params.containsKey("filename")) {
                    fileName = params.get("filename");
                    if (fileName != null) {
                        fileName = fileName.trim();
                    } else {
                        // Even if there is no value, the parameter is present,
                        // so we return an empty file name rather than no file
                        // name.
                        fileName = "";
                    }
                }
            }
        }
        return fileName;
    }

    /**
     * Retrieves the field name from the <code>Content-disposition</code>
     * header.
     * <p>从<code>Content-disposition</code>头中查找字段名称属性。
     *
     * @param headers A <code>Map</code> containing the HTTP request headers.
     *
     * @return The field name for the current <code>encapsulation</code>.
     */
    protected String getFieldName(FileItemHeaders headers) {
        return getFieldName(headers.getHeader(CONTENT_DISPOSITION));
    }

    /**
     * Returns the field name, which is given by the content-disposition
     * header.
     * @param pContentDisposition The content-dispositions header value.
     * @return The field jake
     */
    private String getFieldName(String pContentDisposition) {
        String fieldName = null;
        // 必须以form-data开头，不论大小写
        if (pContentDisposition != null
                && pContentDisposition.toLowerCase(Locale.ENGLISH).startsWith(FORM_DATA)) {
            // 解析文件消息头的参数
            ParameterParser parser = new ParameterParser();
            parser.setLowerCaseNames(true);
            // Parameter parser can handle null input
            Map<String,String> params = parser.parse(pContentDisposition, ';');
            fieldName = params.get("name");
            if (fieldName != null) {
                fieldName = fieldName.trim();
            }
        }
        return fieldName;
    }

    /**
     * <p> Parses the <code>header-part</code> and returns as key/value
     * pairs.
     *
     * <p> If there are multiple headers of the same names, the name
     * will map to a comma-separated list containing the values.
     *
     * <p>解析<code>header-part</code>并以键/值对的形式返回。
     * 如果包含相同名称的header，则会映射到以逗号作为分隔符的列表中。
     *
     * @param headerPart The <code>header-part</code> of the current
     *                   <code>encapsulation</code>.
     *
     * @return A <code>Map</code> containing the parsed HTTP request headers.
     */
    protected FileItemHeaders getParsedHeaders(String headerPart) {
        final int len = headerPart.length();
        FileItemHeadersImpl headers = newFileItemHeaders();
        int start = 0;
        for (;;) {
            // 获取一行的结束下标，比如aa\r\nbb\r\n，则返回2
            int end = parseEndOfLine(headerPart, start);
            // 该行没有数据，跳过
            if (start == end) {
                break;
            }
            // 得到当前行的内容，不包含行分隔符
            StringBuilder header = new StringBuilder(headerPart.substring(start, end));
            start = end + 2;
            while (start < len) {
                int nonWs = start;
                // 跳过空格、tab符
                while (nonWs < len) {
                    char c = headerPart.charAt(nonWs);
                    if (c != ' '  &&  c != '\t') {
                        break;
                    }
                    ++nonWs;
                }
                if (nonWs == start) {
                    break;
                }
                // Continuation line found
                // 被空格或tab隔开的，则继续查找行，拼接为一行
                end = parseEndOfLine(headerPart, nonWs);
                header.append(" ").append(headerPart.substring(nonWs, end));
                start = end + 2;
            }
            parseHeaderLine(headers, header.toString());
        }
        return headers;
    }

    /**
     * Creates a new instance of {@link FileItemHeaders}.
     * @return The new instance.
     */
    protected FileItemHeadersImpl newFileItemHeaders() {
        return new FileItemHeadersImpl();
    }

    /**
     * Skips bytes until the end of the current line.
     * <p>获取一行，以\r\n作为一行的结束标志。返回\r的下标。
     * @param headerPart The headers, which are being parsed.
     * @param end Index of the last byte, which has yet been
     *   processed.
     * @return Index of the \r\n sequence, which indicates
     *   end of line.
     */
    private int parseEndOfLine(String headerPart, int end) {
        int index = end;
        for (;;) {
            int offset = headerPart.indexOf('\r', index);
            if (offset == -1  ||  offset + 1 >= headerPart.length()) {
                throw new IllegalStateException(
                    "Expected headers to be terminated by an empty line.");
            }
            if (headerPart.charAt(offset + 1) == '\n') {
                return offset;
            }
            index = offset + 1;
        }
    }

    /**
     * Reads the next header line.
     * <p>解析消息头行
     * @param headers String with all headers.
     * @param header Map where to store the current header.
     */
    private void parseHeaderLine(FileItemHeadersImpl headers, String header) {
        // name和value通过冒号分隔
        final int colonOffset = header.indexOf(':');
        if (colonOffset == -1) {
            // This header line is malformed, skip it.
            // 格式不正确，跳过
            return;
        }
        // 消息头name
        String headerName = header.substring(0, colonOffset).trim();
        // 消息头value
        String headerValue =
            header.substring(header.indexOf(':') + 1).trim();
        headers.addHeader(headerName, headerValue);
    }

    /**
     * The iterator, which is returned by
     * {@link FileUploadBase#getItemIterator(RequestContext)}.
     * <p>mutilpart/form-data请求的参数迭代器。
     */
    private class FileItemIteratorImpl implements FileItemIterator {

        /**
         * Default implementation of {@link FileItemStream}.
         */
        class FileItemStreamImpl implements FileItemStream {

            /**
             * The file items content type.
             */
            private final String contentType;

            /**
             * The file items field name.
             */
            private final String fieldName;

            /**
             * The file items file name.
             */
            private final String name;

            /**
             * Whether the file item is a form field.
             */
            private final boolean formField;

            /**
             * The file items input stream.
             */
            private final InputStream stream;

            /**
             * Whether the file item was already opened.
             */
            private boolean opened;

            /**
             * The headers, if any.
             * <p>文件消息头
             */
            private FileItemHeaders headers;

            /**
             * Creates a new instance.
             *
             * @param pName The items file name, or null.
             * @param pFieldName The items field name.
             * @param pContentType The items content type, or null.
             * @param pFormField Whether the item is a form field.
             * @param pContentLength The items content length, if known, or -1
             * @throws IOException Creating the file item failed.
             */
            FileItemStreamImpl(String pName, String pFieldName,
                    String pContentType, boolean pFormField,
                    long pContentLength) throws IOException {
                // 文件名称
                name = pName;
                // 字段名称
                fieldName = pFieldName;
                // part的Context-Type属性
                contentType = pContentType;
                // 是否表单字段
                formField = pFormField;
                // 新建一个part流
                final ItemInputStream itemStream = multi.newInputStream();
                InputStream istream = itemStream;
                // 如果限制了上传文件的最大值，则构建限制流LimitedInputStream
                if (fileSizeMax != -1) {
                    if (pContentLength != -1
                            &&  pContentLength > fileSizeMax) {
                        FileSizeLimitExceededException e =
                            new FileSizeLimitExceededException(
                                String.format("The field %s exceeds its maximum permitted size of %s bytes.",
                                        fieldName, Long.valueOf(fileSizeMax)),
                                pContentLength, fileSizeMax);
                        e.setFileName(pName);
                        e.setFieldName(pFieldName);
                        throw new FileUploadIOException(e);
                    }
                    istream = new LimitedInputStream(istream, fileSizeMax) {
                        @Override
                        protected void raiseError(long pSizeMax, long pCount)
                                throws IOException {
                            itemStream.close(true);
                            FileSizeLimitExceededException e =
                                new FileSizeLimitExceededException(
                                    String.format("The field %s exceeds its maximum permitted size of %s bytes.",
                                           fieldName, Long.valueOf(pSizeMax)),
                                    pCount, pSizeMax);
                            e.setFieldName(fieldName);
                            e.setFileName(name);
                            throw new FileUploadIOException(e);
                        }
                    };
                }
                stream = istream;
            }

            /**
             * Returns the items content type, or null.
             *
             * @return Content type, if known, or null.
             */
            @Override
            public String getContentType() {
                return contentType;
            }

            /**
             * Returns the items field name.
             *
             * @return Field name.
             */
            @Override
            public String getFieldName() {
                return fieldName;
            }

            /**
             * Returns the items file name.
             *
             * @return File name, if known, or null.
             * @throws InvalidFileNameException The file name contains a NUL character,
             *   which might be an indicator of a security attack. If you intend to
             *   use the file name anyways, catch the exception and use
             *   InvalidFileNameException#getName().
             */
            @Override
            public String getName() {
                return Streams.checkFileName(name);
            }

            /**
             * Returns, whether this is a form field.
             *
             * @return True, if the item is a form field,
             *   otherwise false.
             */
            @Override
            public boolean isFormField() {
                return formField;
            }

            /**
             * Returns an input stream, which may be used to
             * read the items contents.
             *
             * @return Opened input stream.
             * @throws IOException An I/O error occurred.
             */
            @Override
            public InputStream openStream() throws IOException {
                if (opened) {
                    throw new IllegalStateException(
                            "The stream was already opened.");
                }
                if (((Closeable) stream).isClosed()) {
                    throw new FileItemStream.ItemSkippedException();
                }
                return stream;
            }

            /**
             * Closes the file item.
             *
             * @throws IOException An I/O error occurred.
             */
            void close() throws IOException {
                stream.close();
            }

            /**
             * Returns the file item headers.
             *
             * @return The items header object
             */
            @Override
            public FileItemHeaders getHeaders() {
                return headers;
            }

            /**
             * Sets the file item headers.
             *
             * @param pHeaders The items header object
             */
            @Override
            public void setHeaders(FileItemHeaders pHeaders) {
                headers = pHeaders;
            }

        }

        /**
         * The multi part stream to process.
         */
        private final MultipartStream multi;

        /**
         * The notifier, which used for triggering the
         * {@link ProgressListener}.
         */
        private final MultipartStream.ProgressNotifier notifier;

        /**
         * The boundary, which separates the various parts.
         * <p>请求头Content-Type中的boundary的值。
         */
        private final byte[] boundary;

        /**
         * The item, which we currently process.
         * <p>当前正在处理的part
         */
        private FileItemStreamImpl currentItem;

        /**
         * The current items field name.
         * <p>当前part的字段名称
         */
        private String currentFieldName;

        /**
         * Whether we are currently skipping the preamble.
         * <p>是否跳过开头
         */
        private boolean skipPreamble;

        /**
         * Whether the current item may still be read.
         * <p>当前part是否有效（可读）
         */
        private boolean itemValid;

        /**
         * Whether we have seen the end of the file.
         * <p>是否遇到文件结束符：eof。
         */
        private boolean eof;

        /**
         * Creates a new instance.
         *
         * @param ctx The request context.
         * @throws FileUploadException An error occurred while
         *   parsing the request.
         * @throws IOException An I/O error occurred.
         */
        FileItemIteratorImpl(RequestContext ctx)
                throws FileUploadException, IOException {
            if (ctx == null) {
                throw new NullPointerException("ctx parameter");
            }

            // 校验请求头Content-Type
            String contentType = ctx.getContentType();
            if ((null == contentType)
                    || (!contentType.toLowerCase(Locale.ENGLISH).startsWith(MULTIPART))) {
                throw new InvalidContentTypeException(String.format(
                        "the request doesn't contain a %s or %s stream, content type header is %s",
                        MULTIPART_FORM_DATA, MULTIPART_MIXED, contentType));
            }


            // 请求内容长度
            final long requestSize = ((UploadContext) ctx).contentLength();

            InputStream input; // N.B. this is eventually closed in MultipartStream processing
            if (sizeMax >= 0) {
                if (requestSize != -1 && requestSize > sizeMax) {
                    throw new SizeLimitExceededException(String.format(
                            "the request was rejected because its size (%s) exceeds the configured maximum (%s)",
                            Long.valueOf(requestSize), Long.valueOf(sizeMax)),
                            requestSize, sizeMax);
                }
                // N.B. this is eventually closed in MultipartStream processing
                input = new LimitedInputStream(ctx.getInputStream(), sizeMax) {
                    @Override
                    protected void raiseError(long pSizeMax, long pCount)
                            throws IOException {
                        FileUploadException ex = new SizeLimitExceededException(
                        String.format("the request was rejected because its size (%s) exceeds the configured maximum (%s)",
                               Long.valueOf(pCount), Long.valueOf(pSizeMax)),
                               pCount, pSizeMax);
                        throw new FileUploadIOException(ex);
                    }
                };
            } else {
                input = ctx.getInputStream();
            }

            String charEncoding = headerEncoding;
            if (charEncoding == null) {
                charEncoding = ctx.getCharacterEncoding();
            }

            // 获取边界分隔符
            boundary = getBoundary(contentType);
            if (boundary == null) {
                IOUtils.closeQuietly(input); // avoid possible resource leak
                throw new FileUploadException("the request was rejected because no multipart boundary was found");
            }

            notifier = new MultipartStream.ProgressNotifier(listener, requestSize);
            try {
                multi = new MultipartStream(input, boundary, notifier);
            } catch (IllegalArgumentException iae) {
                IOUtils.closeQuietly(input); // avoid possible resource leak
                throw new InvalidContentTypeException(
                        String.format("The boundary specified in the %s header is too long", CONTENT_TYPE), iae);
            }
            multi.setHeaderEncoding(charEncoding);

            // 跳过开头
            skipPreamble = true;
            // 获取首个part
            findNextItem();
        }

        /**
         * Called for finding the next item, if any.
         * <p>找到下一个part
         *
         * @return True, if an next item was found, otherwise false.
         * @throws IOException An I/O error occurred.
         */
        private boolean findNextItem() throws IOException {
            if (eof) {
                return false;
            }
            if (currentItem != null) {
                currentItem.close();
                currentItem = null;
            }
            for (;;) {
                boolean nextPart;
                // 读取第一个part
                if (skipPreamble) {
                    nextPart = multi.skipPreamble();
                } else {
                    nextPart = multi.readBoundary();
                }
                if (!nextPart) {
                    if (currentFieldName == null) {
                        // Outer multipart terminated -> No more data
                        eof = true;
                        return false;
                    }
                    // Inner multipart terminated -> Return to parsing the outer
                    multi.setBoundary(boundary);
                    currentFieldName = null;
                    continue;
                }
                // 解析请求头，形如 Content-Disposition: form-data; name="fileName"
                FileItemHeaders headers = getParsedHeaders(multi.readHeaders());
                if (currentFieldName == null) {
                    // We're parsing the outer multipart
                    // 解析外部的multipart消息内容
                    // 字段名称
                    String fieldName = getFieldName(headers);
                    if (fieldName != null) {
                        // 内部Content-Type
                        String subContentType = headers.getHeader(CONTENT_TYPE);
                        if (subContentType != null
                                &&  subContentType.toLowerCase(Locale.ENGLISH)
                                        .startsWith(MULTIPART_MIXED)) {
                            currentFieldName = fieldName;
                            // Multiple files associated with this field name
                            byte[] subBoundary = getBoundary(subContentType);
                            multi.setBoundary(subBoundary);
                            skipPreamble = true;
                            continue;
                        }
                        // fileName属性
                        String fileName = getFileName(headers);
                        // 设置当前multipart消息实例
                        currentItem = new FileItemStreamImpl(fileName,
                                fieldName, headers.getHeader(CONTENT_TYPE),
                                fileName == null, getContentLength(headers));
                        currentItem.setHeaders(headers);
                        notifier.noteItem();
                        itemValid = true;
                        return true;
                    }
                } else {
                    String fileName = getFileName(headers);
                    if (fileName != null) {
                        currentItem = new FileItemStreamImpl(fileName,
                                currentFieldName,
                                headers.getHeader(CONTENT_TYPE),
                                false, getContentLength(headers));
                        currentItem.setHeaders(headers);
                        notifier.noteItem();
                        itemValid = true;
                        return true;
                    }
                }
                multi.discardBodyData();
            }
        }

        private long getContentLength(FileItemHeaders pHeaders) {
            try {
                return Long.parseLong(pHeaders.getHeader(CONTENT_LENGTH));
            } catch (Exception e) {
                return -1;
            }
        }

        /**
         * Returns, whether another instance of {@link FileItemStream}
         * is available.
         *
         * @throws FileUploadException Parsing or processing the
         *   file item failed.
         * @throws IOException Reading the file item failed.
         * @return True, if one or more additional file items
         *   are available, otherwise false.
         */
        @Override
        public boolean hasNext() throws FileUploadException, IOException {
            if (eof) {
                return false;
            }
            if (itemValid) {
                return true;
            }
            try {
                return findNextItem();
            } catch (FileUploadIOException e) {
                // unwrap encapsulated SizeException
                throw (FileUploadException) e.getCause();
            }
        }

        /**
         * Returns the next available {@link FileItemStream}.
         *
         * @throws java.util.NoSuchElementException No more items are
         *   available. Use {@link #hasNext()} to prevent this exception.
         * @throws FileUploadException Parsing or processing the
         *   file item failed.
         * @throws IOException Reading the file item failed.
         * @return FileItemStream instance, which provides
         *   access to the next file item.
         */
        @Override
        public FileItemStream next() throws FileUploadException, IOException {
            if (eof  ||  (!itemValid && !hasNext())) {
                throw new NoSuchElementException();
            }
            itemValid = false;
            return currentItem;
        }

    }

    /**
     * This exception is thrown for hiding an inner
     * {@link FileUploadException} in an {@link IOException}.
     */
    public static class FileUploadIOException extends IOException {

        private static final long serialVersionUID = -3082868232248803474L;

        public FileUploadIOException() {
            super();
        }

        public FileUploadIOException(String message, Throwable cause) {
            super(message, cause);
        }

        public FileUploadIOException(String message) {
            super(message);
        }

        public FileUploadIOException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * Thrown to indicate that the request is not a multipart request.
     */
    public static class InvalidContentTypeException
            extends FileUploadException {

        /**
         * The exceptions UID, for serializing an instance.
         */
        private static final long serialVersionUID = -9073026332015646668L;

        /**
         * Constructs a <code>InvalidContentTypeException</code> with no
         * detail message.
         */
        public InvalidContentTypeException() {
            super();
        }

        /**
         * Constructs an <code>InvalidContentTypeException</code> with
         * the specified detail message.
         *
         * @param message The detail message.
         */
        public InvalidContentTypeException(String message) {
            super(message);
        }

        /**
         * Constructs an <code>InvalidContentTypeException</code> with
         * the specified detail message and cause.
         *
         * @param msg The detail message.
         * @param cause the original cause
         *
         * @since 1.3.1
         */
        public InvalidContentTypeException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    /**
     * Thrown to indicate an IOException.
     */
    public static class IOFileUploadException extends FileUploadException {

        private static final long serialVersionUID = -5858565745868986701L;

        public IOFileUploadException() {
            super();
        }

        public IOFileUploadException(String message, Throwable cause) {
            super(message, cause);
        }

        public IOFileUploadException(String message) {
            super(message);
        }

        public IOFileUploadException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * This exception is thrown, if a requests permitted size
     * is exceeded.
     */
    public abstract static class SizeException extends FileUploadException {

        /**
         * Serial version UID, being used, if serialized.
         */
        private static final long serialVersionUID = -8776225574705254126L;

        /**
         * The actual size of the request.
         */
        private final long actual;

        /**
         * The maximum permitted size of the request.
         */
        private final long permitted;

        /**
         * Creates a new instance.
         *
         * @param message The detail message.
         * @param actual The actual number of bytes in the request.
         * @param permitted The requests size limit, in bytes.
         */
        protected SizeException(String message, long actual, long permitted) {
            super(message);
            this.actual = actual;
            this.permitted = permitted;
        }

        /**
         * Retrieves the actual size of the request.
         *
         * @return The actual size of the request.
         * @since 1.3
         */
        public long getActualSize() {
            return actual;
        }

        /**
         * Retrieves the permitted size of the request.
         *
         * @return The permitted size of the request.
         * @since 1.3
         */
        public long getPermittedSize() {
            return permitted;
        }

    }

    /**
     * Thrown to indicate that the request size exceeds the configured maximum.
     */
    public static class SizeLimitExceededException
            extends SizeException {

        /**
         * The exceptions UID, for serializing an instance.
         */
        private static final long serialVersionUID = -2474893167098052828L;

        /**
         * Constructs a <code>SizeExceededException</code> with
         * the specified detail message, and actual and permitted sizes.
         *
         * @param message   The detail message.
         * @param actual    The actual request size.
         * @param permitted The maximum permitted request size.
         */
        public SizeLimitExceededException(String message, long actual,
                long permitted) {
            super(message, actual, permitted);
        }

    }

    /**
     * Thrown to indicate that A files size exceeds the configured maximum.
     */
    public static class FileSizeLimitExceededException
            extends SizeException {

        /**
         * The exceptions UID, for serializing an instance.
         */
        private static final long serialVersionUID = 8150776562029630058L;

        /**
         * File name of the item, which caused the exception.
         */
        private String fileName;

        /**
         * Field name of the item, which caused the exception.
         */
        private String fieldName;

        /**
         * Constructs a <code>SizeExceededException</code> with
         * the specified detail message, and actual and permitted sizes.
         *
         * @param message   The detail message.
         * @param actual    The actual request size.
         * @param permitted The maximum permitted request size.
         */
        public FileSizeLimitExceededException(String message, long actual,
                long permitted) {
            super(message, actual, permitted);
        }

        /**
         * Returns the file name of the item, which caused the
         * exception.
         *
         * @return File name, if known, or null.
         */
        public String getFileName() {
            return fileName;
        }

        /**
         * Sets the file name of the item, which caused the
         * exception.
         *
         * @param pFileName the file name of the item, which caused the exception.
         */
        public void setFileName(String pFileName) {
            fileName = pFileName;
        }

        /**
         * Returns the field name of the item, which caused the
         * exception.
         *
         * @return Field name, if known, or null.
         */
        public String getFieldName() {
            return fieldName;
        }

        /**
         * Sets the field name of the item, which caused the
         * exception.
         *
         * @param pFieldName the field name of the item,
         *        which caused the exception.
         */
        public void setFieldName(String pFieldName) {
            fieldName = pFieldName;
        }

    }

    /**
     * Returns the progress listener.
     *
     * @return The progress listener, if any, or null.
     */
    public ProgressListener getProgressListener() {
        return listener;
    }

    /**
     * Sets the progress listener.
     *
     * @param pListener The progress listener, if any. Defaults to null.
     */
    public void setProgressListener(ProgressListener pListener) {
        listener = pListener;
    }

}
