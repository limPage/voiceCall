//package com.hj.webserver.service;
//
//import com.android.internal.telephony.ITelephony;
//import com.hj.webserver.WebServer;
//import com.hj.webserver.util.CallSetting;
//import com.hj.webserver.util.LogUtil;
//import com.hj.webserver.util.ImageUtil;
//import com.hj.webserver.util.WavUtil;
//import com.hj.webserver.service.CallSettingServer;
//import android.provider.Settings;
//import android.content.Context;
//
//import java.io.BufferedInputStream;
//import java.io.BufferedReader;
//import java.io.ByteArrayInputStream;
//import java.io.ByteArrayOutputStream;
//import java.io.File;
//import java.io.FileInputStream;
//import java.io.FileNotFoundException;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.InputStreamReader;
//import java.io.OutputStream;
//import java.io.PrintStream;
//import java.io.PrintWriter;
//import java.net.Inet4Address;
//import java.net.InetAddress;
//import java.net.ServerSocket;
//import java.net.Socket;
//import java.net.URLEncoder;
//import java.util.Date;
//import java.util.Enumeration;
//import java.util.Vector;
//import java.util.Hashtable;
//import java.util.Locale;
//import java.util.Properties;
//import java.util.StringTokenizer;
//import java.util.TimeZone;
//import java.io.ByteArrayOutputStream;
//import java.io.FileOutputStream;
//
//import javax.security.cert.CertificateException;
//import javax.security.cert.CertificateExpiredException;
//import javax.security.cert.CertificateNotYetValidException;
//import javax.security.cert.X509Certificate;
//import javax.xml.parsers.DocumentBuilder;
//import javax.xml.parsers.DocumentBuilderFactory;
//
//import org.xmlpull.v1.XmlPullParser;
//import org.xmlpull.v1.XmlPullParserFactory;
//import org.w3c.dom.Document;
//import org.w3c.dom.Node;
//import org.w3c.dom.NodeList;
//import org.xml.sax.InputSource;
//
//import android.os.RemoteException;
//import android.os.ServiceManager;
//import android.os.SystemProperties;
//import android.provider.Telephony;
//import android.util.Log;
//
//
//import javax.net.ssl.X509TrustManager;
//import javax.net.ssl.TrustManager;
//import java.security.KeyStore;
//import javax.net.ssl.KeyManager;
//import javax.net.ssl.KeyManagerFactory;
//import javax.net.ssl.SSLContext;
//import javax.net.ssl.SSLServerSocket;
//import javax.net.ssl.SSLServerSocketFactory;
//import javax.net.ssl.TrustManagerFactory;
//import java.io.*;
//import java.security.SecureRandom;
//import java.security.PrivateKey;
//import java.security.KeyFactory;
//import java.security.cert.Certificate;
//import java.security.cert.CertificateFactory;
//import java.security.spec.PKCS8EncodedKeySpec;
//import java.util.Base64;
//import java.nio.charset.Charset;
//
//public class NanoHTTPD {
//    private static final String TAG = "NanoHTTPD";
//    private static final boolean DEBUG = false;
//    public static boolean isFwUploadSuccess = false;
//    boolean isReceivedNextBuf = false;
//    byte[] temp;
//    int temp_rlen;
//    Context mContext;
//
//
//    public Response serve(String uri, String method, Properties header, Properties parms, Properties files) {
//        //if(DEBUG) Log.d(TAG, method + " '" + uri + "' " );
//
//        Enumeration e = header.propertyNames();
//        while (e.hasMoreElements()) {
//            String value = (String) e.nextElement();
//        }
//
//        e = parms.propertyNames();
//        while (e.hasMoreElements()) {
//            String value = (String) e.nextElement();
//        }
//
//        e = files.propertyNames();
//        while (e.hasMoreElements()) {
//            String value = (String) e.nextElement();
//        }
//        return serveFile(uri, header, myRootDir, true);
//    }
//
//    /**
//     * HTTP response.
//     * Return one of these from serve().
//     */
//    public class Response {
//        /**
//         * Default constructor: response = HTTP_OK, data = mime = 'null'
//         */
//        public Response() {
//            this.status = HTTP_OK;
//        }
//
//        /**
//         * Basic constructor.
//         */
//        public Response(String status, String mimeType, InputStream data) {
//            this.status = status;
//            this.mimeType = mimeType;
//            this.data = data;
//        }
//
//        /**
//         * Convenience method that makes an InputStream out of
//         * given text.
//         */
//        public Response(String status, String mimeType, String txt) {
//            this.status = status;
//            this.mimeType = mimeType;
//            try {
//                this.data = new ByteArrayInputStream(txt.getBytes("UTF-8"));
//            } catch (java.io.UnsupportedEncodingException uee) {
//                uee.printStackTrace();
//            }
//        }
//
//        /**
//         * Adds given line to the header.
//         */
//        public void addHeader(String name, String value) {
//            header.put(name, value);
//        }
//
//        /**
//         * HTTP status code after processing, e.g. "200 OK", HTTP_OK
//         */
//        public String status;
//
//        /**
//         * MIME type of content, e.g. "text/html"
//         */
//        public String mimeType;
//
//        /**
//         * Data of the response, may be null.
//         */
//        public InputStream data;
//
//        /**
//         * Headers for the HTTP response. Use addHeader()
//         * to add lines.
//         */
//        public Properties header = new Properties();
//    }
//
//    /**
//     * Some HTTP response status codes
//     */
//    public static final String HTTP_OK = "200 OK", HTTP_PARTIALCONTENT = "206 Partial Content",
//            HTTP_RANGE_NOT_SATISFIABLE = "416 Requested Range Not Satisfiable", HTTP_REDIRECT = "301 Moved Permanently",
//            HTTP_NOTMODIFIED = "304 Not Modified", HTTP_FORBIDDEN = "403 Forbidden", HTTP_NOTFOUND = "404 Not Found",
//            HTTP_BADREQUEST = "400 Bad Request", HTTP_INTERNALERROR = "500 Internal Server Error",
//            HTTP_NOTIMPLEMENTED = "501 Not Implemented";
//
//    /**
//     * Common mime types for dynamic content
//     */
//    public static final String MIME_PLAINTEXT = "text/plain", MIME_HTML = "text/html",
//            MIME_DEFAULT_BINARY = "application/octet-stream", MIME_XML = "text/xml", MIME_IMG_PNG = "image/png";
//
//    // ==================================================
//    // Socket & server code
//    // ==================================================
//
//    /**
//     * Starts a HTTP server to given port.<p>
//     * Throws an IOException if the socket is already in use
//     */
//    // lch start 2024-11-26 Set available port values
//    public NanoHTTPD(int port, File wwwroot) throws IOException {
//        //if(DEBUG) Log.v(TAG, "NanoHTTPD : " , new RuntimeException());
//        myTcpPort = port;
//        this.myRootDir = wwwroot;
//        if (myTcpPort < 1024 || myTcpPort > 65535 ){
//            Log.d(TAG, "The port number must be a value between 1024 and 65535..." + myTcpPort+ " -> 8888");
//            myTcpPort = 8888;
//        }
//        // lch end 2024-11-26 Set available port values
//        myServerSocket = new ServerSocket(myTcpPort);
//        myThread = new Thread(new Runnable() {
//            public void run() {
//                try {
//                    while (true) {
//                        new HTTPSession(myServerSocket.accept());
//                    }
//                } catch (IOException e) {
//                    //e.printStackTrace();
//
//                }
//            }
//        });
//
//        myThread.setDaemon(true);
//        myThread.start();
//    }
//
//    // lch 2024-10-29 add ssl
//    public NanoHTTPD(Context context, int port, File wwwroot, SSLServerSocketFactory ssf ) throws IOException {
//        //if(DEBUG) Log.v(TAG, "NanoHTTPD : " , new RuntimeException());
//        myTcpPort = port;
//        this.myRootDir = wwwroot;
//        this.mContext = context;
//        sslServerSocketFactory = ssf;
//        // myServerSocket = sslServerSocketFactory.createServerSocket(myTcpPort);
//        SSLServerSocket sslServerSocket = (SSLServerSocket) sslServerSocketFactory.createServerSocket(myTcpPort);
//
//        // 여기서 프로토콜 제한
//        sslServerSocket.setEnabledProtocols(new String[] { "TLSv1.2"});
//
//        myServerSocket = sslServerSocket;
//        /*InetAddress temp = (Inet4Address) InetAddress.getByName("192.168.10.118");
//        Inet4Address temp = (Inet4Address)Inet4Address.getByName("192.168.10.67");
//        myServerSocket = new ServerSocket(port, 1, temp);*/
//        myThread = new Thread(new Runnable() {
//            public void run() {
//                try {
//                    while (true) {
//                        new HTTPSession(myServerSocket.accept());
//                    }
//                } catch (IOException e) {
//                    //e.printStackTrace();
//                }
//            }
//        });
//
//        myThread.setDaemon(true);
//        myThread.start();
//    }
//
//    /**
//     * Stops the server.
//     */
//    public void stop() {
//        try {
//            myServerSocket.close();
//            myThread.join();
//        } catch (IOException ioe) {
//        } catch (InterruptedException e) {
//        }
//    }
//
//    /**
//     * Starts as a standalone file server and waits for Enter.
//     */
//    public static void main(String[] args) {
//        // Defaults
//        if (DEBUG)
//            Log.v(TAG, "main : ", new RuntimeException());
//        int port = 8888;
//        File wwwroot = new File(".").getAbsoluteFile();
//
//        try {
//            new NanoHTTPD(port, wwwroot);
//        } catch (IOException ioe) {
//            System.exit(-1);
//        }
//
//        try {
//            System.in.read();
//        } catch (Throwable t) {
//        }
//    }
//
//    /**
//     * Handles one session, i.e. parses the HTTP request
//     * and returns the response.
//     */
//
//    private class HTTPSession implements Runnable {
//        private Socket mySocket;
//
//        public HTTPSession(Socket s) {
//            mySocket = s;
//            Thread t = new Thread(this);
//            t.setDaemon(true);
//            t.start();
//        }
//
//        public void run() {
//            try {
//                InputStream is = mySocket.getInputStream();
//                if (is == null)
//                    return;
//
//                // lch start 2024-10-17 ip check
//                if (WebServer.IS_TTA_CONNECTION){
//                    if ( !SystemProperties.get("persist.sys.default_client", "0").equals("1") ) {
//                        int isAirPlaneMode = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0);
//                        String clientIP = mySocket.getInetAddress().getHostAddress();
//                        final String ALLOWED_CLIENT_IP = CallSetting.DEFAULT_IP;
//                        Log.d(TAG, "===== not_default_client ===== [ "+ SystemProperties.get("persist.sys.default_client", "0") + " ] ");
//                        Log.d(TAG, "===== clientIP =====> [ "+ clientIP + " ] ");
//                        if (DEBUG){
//                            Log.d(TAG, "[" + isAirPlaneMode + "]");
//                        }
//                        if (isAirPlaneMode == 1){
//                            Log.d(TAG, "Bypass into admin mode...");
//                        } else if (!ALLOWED_CLIENT_IP.equals(clientIP)) {
//                            Log.d(TAG, "Not default clientIP... [ " + ALLOWED_CLIENT_IP + " ] ");
//                            mySocket.close();
//                            return;
//                        }
//                    }
//                }
//                // lch end   2024-10-17 ip check
//
//                // Read the first 8192 bytes.
//                // The full header should fit in here.
//                // Apache's default header limit is 8KB.
//                // Do NOT assume that a single read will get the entire header at once!
//                final int bufsize = 8192;
//                byte[] buf = new byte[bufsize];
//                int splitbyte = 0;
//                int rlen = 0;
//                {
//                    int read = is.read(buf, 0, bufsize);
//                    while (read > 0) {
//                        rlen += read;
//                        splitbyte = findHeaderEnd(buf, rlen);
//                        if (splitbyte > 0)
//                            break;
//                        read = is.read(buf, rlen, bufsize - rlen);
//                    }
//                }
//
//                // Create a BufferedReader for parsing the header.
//                ByteArrayInputStream hbis = new ByteArrayInputStream(buf, 0, rlen);
//                BufferedReader hin = new BufferedReader(new InputStreamReader(hbis));
//                Properties pre = new Properties();
//                Properties parms = new Properties();
//                Properties header = new Properties();
//                Properties files = new Properties();
//
//                // Decode the header into parms and header java properties
//                decodeHeader(hin, pre, parms, header);
//                //if(DEBUG) Log.d(TAG, "IPAddress : " + mySocket.getInetAddress().getHostAddress());
//                String method = pre.getProperty("method");
//                String uri = pre.getProperty("uri");
//
//                long size = 0x7FFFFFFFFFFFFFFFl;
//                String contentLength = header.getProperty("content-length");
//                if (contentLength != null) {
//                    try {
//                        size = Integer.parseInt(contentLength);
//                    } catch (NumberFormatException ex) {
//                    }
//                }
//
//                /*// We are looking for the byte separating header from body.
//                // It must be the last byte of the first two sequential new lines.
//                int splitbyte = 0;
//                boolean sbfound = false;
//                while (splitbyte < rlen) {
//                    if (buf[splitbyte] == '\r' && buf[++splitbyte] == '\n' && buf[++splitbyte] == '\r' && buf[++splitbyte] == '\n') {
//                        sbfound = true;
//                        break;
//                    }
//                    splitbyte++;
//                }
//                splitbyte++;*/
//
//                /* by jsh 2014-09-26 , Add system update
//                // Write the part of body already read to ByteArrayOutputStream f
//                ByteArrayOutputStream f = new ByteArrayOutputStream();
//
//                if (splitbyte < rlen)
//                    f.write(buf, splitbyte, rlen - splitbyte);
//                // end by jsh 2014-09-26 , Add system update */
//
//                // While Firefox sends on the first read all the data fitting
//                // our buffer, Chrome and Opera sends only the headers even if
//                // there is data for the body. So we do some magic here to find
//                // out whether we have already consumed part of body, if we
//                // have reached the end of the data to be sent or we should
//                // expect the first byte of the body at the next read.
//                if (splitbyte < rlen) {
//                    size -= rlen - splitbyte + 1;
//                } else if (splitbyte == 0 || size == 0x7FFFFFFFFFFFFFFFl) {
//                    size = 0;
//                }
//
////by jsh 2014-09-22 , Add system update
//                if(size > 7500000) {
//                    //Check firmware
//                    firmwareUpdate(is, size);
//                    if( isFwUploadSuccess ) {
//                        try {
//                            Response r = serve(uri, method, header, parms, files);
//                            if (r == null) {
//                                sendError(HTTP_INTERNALERROR, "SERVER INTERNAL ERROR: Serve() returned a null response.");
//                            } else {
//                                sendResponse(r.status, r.mimeType, r.header, r.data);
//                            }
//                        } catch (Exception e) {
//                            e.printStackTrace();
//                        }
//                    }
//
//                    return;
//                }
//                // Write the part of body already read to ByteArrayOutputStream f
//                ByteArrayOutputStream f = new ByteArrayOutputStream();
//
//                if (splitbyte < rlen) {
//                    f.write(buf, splitbyte, rlen - splitbyte);
//                }
////end by jsh 2014-09-22 , Add system update
//
//                // Now read all the body and write it to f
//                buf = new byte[512];
//                while (rlen >= 0 && size > 0) {
//                    rlen = is.read(buf, 0, 512);
//                    size -= rlen;
//                    if (rlen > 0) {
//                        f.write(buf, 0, rlen);
//                    }
//                }
//
//                // Get the raw body as a byte []
//                byte[] fbuf = f.toByteArray();
//
//                // Create a BufferedReader for easily reading it as string.
//                ByteArrayInputStream bin = new ByteArrayInputStream(fbuf);
//                BufferedReader in = new BufferedReader(new InputStreamReader(bin));
//
//                // If the method is POST, there may be parameters
//                // in data section, too, read it:
//                if (method != null && method.equalsIgnoreCase("POST")) {
//                    String contentType = "";
//                    String contentTypeHeader = header.getProperty("content-type");
//                    StringTokenizer st = new StringTokenizer(contentTypeHeader, "; ");
//                    if (st.hasMoreTokens()) {
//                        contentType = st.nextToken();
//                    }
//
//                    if (contentType.equalsIgnoreCase("multipart/form-data")) {
//                        // Handle multipart/form-data
//                        if (!st.hasMoreTokens()) {
//                            sendError(HTTP_BADREQUEST,
//                                    "BAD REQUEST: Content type is multipart/form-data but boundary missing. Usage: GET /example/file.html");
//                        }
//                        String boundaryExp = st.nextToken();
//                        st = new StringTokenizer(boundaryExp, "=");
//                        if (st.countTokens() != 2) {
//                            sendError(HTTP_BADREQUEST,
//                                    "BAD REQUEST: Content type is multipart/form-data but boundary syntax error. Usage: GET /example/file.html");
//                        }
//                        st.nextToken();
//                        String boundary = st.nextToken();
//                        decodeMultipartData(boundary, fbuf, in, parms, files);
//                    } else {
//                        // Handle application/x-www-form-urlencoded
//                        String postLine = "";
//                        char pbuf[] = new char[512];
//                        int read = in.read(pbuf);
//
//                        while (read >= 0 && !postLine.endsWith("\r\n")) {
//                            postLine += String.valueOf(pbuf, 0, read);
//                            read = in.read(pbuf);
//                        }
//                        postLine = postLine.trim();
//                        decodeParms(postLine, parms);
//                    }
//                }
//
//                if (method != null && method.equalsIgnoreCase("PUT")) {
//                    files.put("content", saveTmpFile(fbuf, 0, f.size(), "unknown"));
//                }
//
//                // Ok, now do the serve()
//                Response r = serve(uri, method, header, parms, files);
//                if (r == null) {
//                    sendError(HTTP_INTERNALERROR, "SERVER INTERNAL ERROR: Serve() returned a null response.");
//                } else {
//                    sendResponse(r.status, r.mimeType, r.header, r.data);
//                }
//
//                in.close();
//                is.close();
//            } catch (IOException ioe) {
//                try {
//                    sendError(HTTP_INTERNALERROR, "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
//                } catch (Throwable t) {
//                }
//            } catch (InterruptedException ie) {
//                // Thrown by sendError, ignore and exit the thread.
//            }
//        }
//
//        // by jsh 2023-11-27 , Add firmware update
//        private void firmwareUpdate(InputStream is, long size) {
//            File file = new File("/sdcard/", "update.zip");
//            OutputStream os = null;
//
//            try {
//                if (!WebServer.IS_SUPER_ADMIN) {
//                    Log.i(TAG, "You need SUPER ADMIN");
//                    SystemProperties.set("tcc.webserver.fwprogress", "mismatch");
//                    sendError(HTTP_BADREQUEST, "관리자 비밀번호로 로그인하세요");
//                    if (is != null) {
//                        is.close();
//                    }
//                    return;
//                }
//
//                // For H400N
////                if (file.exists()) {
////                    file.delete();
////                }
//
//                Log.i(TAG, "firmwareUpdate - Packet size : " +size);
//
//                byte[] buf = new byte[512];
//                int rlen;
//                isFwUploadSuccess = false;
//                final long FILE_SIZE = size;    // 전체 패킷의 사이즈, 실제 펌웨어 파일 사이즈 보다 큼
//                long lastProgress = -1;         // 웹 화면의 업로드 % 보여주기 위한 용도
//
//                // Get start point ('compressed..')
//                boolean isFoundStartPoint = false;
//                int startPoint = 0;
//                byte[] spBuf = new byte[1024];   // Start point buffer
//                rlen = is.read(spBuf, 0, 1024);
//                for( int i = 0 ; i < rlen ; i++ ) {
//                    if( spBuf[i] == 'c' && spBuf[i+1] == 'o' && spBuf[i+2] == 'm' && spBuf[i+3] == 'p' && spBuf[i+4] == 'r' &&
//                            spBuf[i+5] == 'e' && spBuf[i+6] == 's' && spBuf[i+7] == 's' && spBuf[i+8] == 'e' && spBuf[i+9] == 'd') {
//                        startPoint = i + 14;
//                        isFoundStartPoint = true;
//                        Log.i(TAG, "Get start point - Success , Start point : " +startPoint);
//                        break;
//                    }
//                }
//                if( !isFoundStartPoint ) {
//                    sendError(HTTP_BADREQUEST, "BAD REQUEST: Update file upload fail");
//                    return;
//                }
//
//                //Write data
//                os = new FileOutputStream(file);
//                os.write(spBuf, startPoint, rlen - startPoint);     // start point 부터 나머지 버퍼 읽어옴
//                size -= rlen;
//
//                while (rlen >= 0 && size > 0) {
//                    // Read & Write
//                    rlen = is.read(buf, 0, 512);
//                    size -= rlen;
//                    os.write(buf, 0, rlen);
//
//                    // Get upload progress
//                    long progress = ((FILE_SIZE - size) * 100) / FILE_SIZE;
//                    if (lastProgress != progress) {
//                        lastProgress = progress;
//                        SystemProperties.set("tcc.webserver.fwprogress", String.valueOf(progress));
//                        Log.i(TAG, "F/W file Upload progress : " + progress);
//                    }
//
//                    // Get end point  "..-----" (0D 0A 2D 2D 2D 2D 2D 2D)
//                    if( size < 2000 ) {
//                        Log.i(TAG, "Get end point");
//                        byte[] epBuf = new byte[2048];   // End point buffer
//                        rlen = is.read(epBuf, 0, 2048);
//                        size -= rlen;
//                        Log.i(TAG, "Get end point - Read length : " +rlen +", Size : " +size);
//
//                        for( int i = 0 ; i < rlen ; i++ ) {
//                            if(epBuf[i] == 0x0D && epBuf[i+1] == 0x0A && epBuf[i+2] == 0x2D && epBuf[i+3] == 0x2D &&
//                                    epBuf[i+4] == 0x2D && epBuf[i+5] == 0x2D && epBuf[i+5] == 0x2D && epBuf[i+5] == 0x2D) {
//                                os.write(epBuf, 0, i);
//                                Log.i(TAG, "Get end point - Success , End point : " +i);
//                                break;
//                            }
//                        }
//                    }
//                }
//            } catch (Exception e1) {
//                e1.printStackTrace();
//                try {
//                    sendError(HTTP_BADREQUEST, "BAD REQUEST: Update file upload fail");
//                } catch(Exception e2) {
//                    e2.printStackTrace();
//                }
//            } finally {
//                if (file.exists()) {
//                    isFwUploadSuccess = true;
//                    SystemProperties.set("tcc.webserver.fwprogress", "100");
//                    Log.i(TAG, "firmwareUpdate - Firmware file upload success ..");
//                    Log.i(TAG, "firmwareUpdate - Firmware file size : " + file.length());
//                }
//
//                try {
//                    if (os != null) {
//                        os.close();
//                    }
//                } catch(Exception e) {
//                    e.printStackTrace();
//                }
//
//                try {
//                    if (is != null) {
//                        is.close();
//                    }
//                } catch(Exception e) {
//                    e.printStackTrace();
//                }
//            }
//        }
//        // end by jsh 2023-11-27 , Add firmware update
//
//        /**
//         * Decodes the sent headers and loads the data into
//         * java Properties' key - value pairs
//         **/
//        private void decodeHeader(BufferedReader in, Properties pre, Properties parms, Properties header) throws InterruptedException {
//            try {
//                // Read the request line
//                String inLine = in.readLine();
//                if (inLine == null)
//                    return;
//                StringTokenizer st = new StringTokenizer(inLine);
//                if (!st.hasMoreTokens()) {
//                    sendError(HTTP_BADREQUEST, "BAD REQUEST: Syntax error. Usage: GET /example/file.html");
//                }
//
//                String method = st.nextToken();
//                pre.put("method", method);
//
//                if (!st.hasMoreTokens()) {
//                    sendError(HTTP_BADREQUEST, "BAD REQUEST: Missing URI. Usage: GET /example/file.html");
//                }
//
//                String uri = st.nextToken();
//
//                // Decode parameters from the URI
//                int qmi = uri.indexOf('?');
//                if (qmi >= 0) {
//                    decodeParms(uri.substring(qmi + 1), parms);
//                    uri = decodePercent(uri.substring(0, qmi));
//                } else {
//                    uri = decodePercent(uri);
//                }
//
//                // If there's another token, it's protocol version,
//                // followed by HTTP headers. Ignore version but parse headers.
//                // NOTE: this now forces header names lowercase since they are
//                // case insensitive and vary by client.
//                if (st.hasMoreTokens()) {
//                    String line = in.readLine();
//                    while (line != null && line.trim().length() > 0) {
//                        int p = line.indexOf(':');
//                        if (p >= 0) {
//                            header.put(line.substring(0, p).trim().toLowerCase(), line.substring(p + 1).trim());
//                            //if(DEBUG) Log.d(TAG, "1 : " + line.substring(0,p).trim().toLowerCase() + " 2 : " + line.substring(p+1).trim());
//                        }
//                        line = in.readLine();
//                    }
//                }
//                pre.put("uri", uri);
//            } catch (IOException ioe) {
//                sendError(HTTP_INTERNALERROR, "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
//            }
//        }
//
//        private void decodeMultipartData(String boundary, byte[] fbuf, BufferedReader in, Properties parms, Properties files) throws InterruptedException {
//            try {
//                int[] bpositions = getBoundaryPositions(fbuf, boundary.getBytes());
//                int boundarycount = 1;
//                String logName = "";
//                String ImageName = "";	// TEIAS_20200713 - ghost call check
//                String wavName = "";	// by jsh 2021-11-25 , Add selfcheck sound record
//                String mpline = in.readLine();
//                while (mpline != null) {
//                    if (mpline.indexOf(boundary) == -1)
//                        sendError(HTTP_BADREQUEST,
//                                "BAD REQUEST: Content type is multipart/form-data but next chunk does not start with boundary. Usage: GET /example/file.html");
//                    boundarycount++;
//                    Properties item = new Properties();
//                    mpline = in.readLine();
//                    while (mpline != null && mpline.trim().length() > 0) {
//                        int p = mpline.indexOf(':');
//                        if (p != -1)
//                            item.put(mpline.substring(0, p).trim().toLowerCase(), mpline.substring(p + 1).trim());
//                        mpline = in.readLine();
//                    }
//
//                    if (mpline != null) {
//                        String contentDisposition = item.getProperty("content-disposition");
//                        if (contentDisposition == null) {
//                            sendError(HTTP_BADREQUEST,
//                                    "BAD REQUEST: Content type is multipart/form-data but no content-disposition info found. Usage: GET /example/file.html");
//                        }
//                        StringTokenizer st = new StringTokenizer(contentDisposition, "; ");
//                        Properties disposition = new Properties();
//                        while (st.hasMoreTokens()) {
//                            String token = st.nextToken();
//                            int p = token.indexOf('=');
//                            if (p != -1)
//                                disposition.put(token.substring(0, p).trim().toLowerCase(), token.substring(p + 1).trim());
//                        }
//                        String pname = disposition.getProperty("name");
//                        pname = pname.substring(1, pname.length() - 1);
//
//                        // by jsh 2014-12-09 , Add log download
//                        if(pname.equals("logSelect")) {
//                            logName = in.readLine();
//                            Log.i(TAG,"GET LOG NAME : " +logName);
//                        } else if(pname.equals("logDownload") && !"".equals(logName) && !"None".equals(logName)) {
//                            try {
////                                FileInputStream f = new FileInputStream(new File("/data/" +logName));
//                                FileInputStream f = new FileInputStream(LogUtil.getLog(logName));
//                                Response r = new Response(HTTP_OK, MIME_DEFAULT_BINARY, f);
//                                sendResponse(r.status, r.mimeType, r.header, r.data);
//                            } catch (Exception e) {
//                                e.printStackTrace();
//                            }
//                            return;
//                        } else if(pname.equals("logDelete") && !"".equals(logName) && !"None".equals(logName)) {
//                            boolean res = LogUtil.deleteLog(logName);
//                            Log.i(TAG,"DELETE LOG : " +(res ? "SUCCESS" : "FAIL"));
//                            return;
//                            //by jsh 2015-03-12 , Add broadcast sound upload
//                        } else if(pname.equals("korDefault") || pname.equals("engDefault") || pname.equals("thaiDefault") ||
//                                pname.equals("endDefault") || pname.equals("errorDefault") || pname.equals("dooropenDefault")) {
//                            setDefaultWav(pname);
//                            return;
//                        }
//                        // TEIAS_20200713 - ghost call check
//                        else if(pname.equals("ImgaeSelect")) {
//                            ImageName = in.readLine();
//                            Log.i(TAG,"GET IMAGE NAME : " +ImageName);
//                        } else if(pname.equals("ImageDownload") && !"".equals(ImageName) && !"None".equals(ImageName)) {
//                            try {
//                                FileInputStream f2 = new FileInputStream(ImageUtil.getImage(ImageName));
//                                Response r2 = new Response(HTTP_OK, MIME_DEFAULT_BINARY, f2);
//                                sendResponse(r2.status, r2.mimeType, r2.header, r2.data);
//                            } catch (Exception e) {
//                                e.printStackTrace();
//                            }
//                            return;
//                        } else if(pname.equals("ImageDelete") && !"".equals(ImageName) && !"None".equals(ImageName)) {
//                            boolean res = ImageUtil.deleteImage(ImageName);
//                            Log.i(TAG,"DELETE IMAGE : " +(res ? "SUCCESS" : "FAIL"));
//                            return;
//                            // by jsh 2021-11-25 , Add selfcheck sound record
//                        } else if(pname.equals("wavSelect")) {
//                            wavName = in.readLine();
//                            Log.i(TAG,"GET WAV NAME : " +wavName);
//                        } else if(pname.equals("wavDownload") && !"".equals(wavName) && !"None".equals(wavName)) {
//                            Log.i(TAG,"wavDownload - GET WAV NAME : " +wavName);
//                            try {
//                                FileInputStream f = new FileInputStream(WavUtil.getWav(wavName));
//                                Response r = new Response(HTTP_OK, MIME_DEFAULT_BINARY, f);
//                                sendResponse(r.status, r.mimeType, r.header, r.data);
//                            } catch (Exception e) {
//                                e.printStackTrace();
//                            }
//                            return;
//                        } else if(pname.equals("wavDelete") && !"".equals(wavName) && !"None".equals(wavName)) {
//                            boolean res = WavUtil.deleteWav(wavName);
//                            Log.i(TAG,"DELETE WAV : " +(res ? "SUCCESS" : "FAIL"));
//                            return;
//                            // end by jsh 2021-11-25 , Add selfcheck sound record
//                            // by jsh 2020-11-10 , Add Fm switch list download & delete
//                        } else if(pname.equals("fmSwitchListDownload")) {
//                            Log.i(TAG,"FM SWITCH LIST DOWNLOAD");
//                            try {
//                                FileInputStream f = new FileInputStream(new File("/data/FmSwitchList.txt"));
//                                Response r = new Response(HTTP_OK, MIME_DEFAULT_BINARY, f);
//                                sendResponse(r.status, r.mimeType, r.header, r.data);
//                            } catch (Exception e) {
//                                e.printStackTrace();
//                            }
//                            return;
//                        } else if(pname.equals("fmSwitchListDelete")) {
//                            boolean res = false;
//                            try {
//                                File file = new File("/data/FmSwitchList.txt");
//                                if( file.exists() ) {
//                                    res = file.delete();
//                                }
//                            } catch (Exception e) {
//                                e.printStackTrace();
//                            }
//                            Log.i(TAG,"DELETE FM SWITCH LIST : " +(res ? "SUCCESS" : "FAIL"));
//                            return;
//                            // end by jsh 2020-11-10 , Add Fm switch list download & delete
//                        }
//                        // END
//                        // end by jsh 2015-03-12 , Add broadcast sound upload
//                        // end by jsh 2014-12-09 , Add log download
//
//                        String value = "";
//                        if (item.getProperty("content-type") == null) {
//                            while (mpline != null && mpline.indexOf(boundary) == -1) {
//                                mpline = in.readLine();
//                                if (mpline != null) {
//                                    int d = mpline.indexOf(boundary);
//                                    if (d == -1)
//                                        value += mpline;
//                                    else
//                                        value += mpline.substring(0, d - 2);
//                                }
//                            }
//                        } else {
//                            if (boundarycount > bpositions.length)
//                                sendError(HTTP_INTERNALERROR, "Error processing request");
//                            int offset = stripMultipartHeaders(fbuf, bpositions[boundarycount - 2]);
//
//                            if (DEBUG)
//                                Log.d(TAG, "NANOHTTPD decodeMultipartData pname2" + pname);
//                            if (DEBUG)
//                                Log.d(TAG, "NANOHTTPD item.getProperty('content-type')" + item.getProperty("content-type"));
//
//                            String fname = "";
//                            Log.i(TAG,"========================================================================= pname : " +pname);
//                            int type = -1;      //by jsh 2014-06-19 , Add upload certificate
//                            if ("rootCertificate".equals(pname)) {
//                                fname = "hjict_Rootcertificate.pem";
//                                type = 0;       //by jsh 2014-06-19 , Add upload certificate
//                            } else if ("clientKey".equals(pname)) {
//                                fname = "hjict_Client_key.pem";
//                                type = 1;       //by jsh 2014-06-19 , Add upload certificate
//                            } else if ("clientCertificate".equals(pname)) {
//                                fname = "hjict_Client_certificate.pem";
//                                type = 2;       //by jsh 2014-06-19 , Add upload certificate
//                                //by jsh 2015-03-12 , Add broadcast sound upload
//                            } else if ("korUpload".equals(pname)) {
//                                fname = "CCTV_KOR.wav";
//                                type = 3;
//                            } else if ("engUpload".equals(pname)) {
//                                fname = "CCTV_ENG.wav";
//                                type = 4;
//                            } else if ("thaiUpload".equals(pname)) {
//                                fname = "CCTV_THAI.wav";
//                                type = 5;
//                            } else if ("endUpload".equals(pname)) {
//                                fname = "END_MESSAGE.wav";
//                                type = 6;
//                            } else if ("errorUpload".equals(pname)) {
//                                fname = "ERROR_MESSAGE.wav";
//                                type = 7;
//                            } else if ("dooropenUpload".equals(pname)) {
//                                fname = "DOOROPEN_MESSAGE.wav";
//                                type = 8;
//                                //end by jsh 2015-03-12 , Add broadcast sound upload
//                            }
//
//                            String path = saveTmpFile(fbuf, offset, bpositions[boundarycount - 1] - offset - 4, fname);
//
//                            //by jsh 2014-06-19 , Add upload certificate
//                            if(type != -1) {
//                                byte[] buffer = fileToByteArray(path);
//
//                                if(type == 1) {
//                                    String temp = new String(buffer, "UTF-8");
//                                    temp = temp.replace("\n", "");
//                                    temp = temp.replace("\r", "");
//                                    buffer = temp.getBytes("UTF-8");
//                                }
//
//                                if(type >= 3 && type <= 8) {
//                                    CallSetting.isNeedReboot = true;
//                                }
//                            }
//                            //end by jsh 2014-06-19 , Add upload certificate
//
//                            if (DEBUG)
//                                Log.d(TAG, "NANOHTTPD path" + path);
//
//                            files.put(pname, path);
//                            value = disposition.getProperty("filename");
//                            if (value != null) {
//                                value = value.substring(1, value.length() - 1);
//                            }
//                            do {
//                                mpline = in.readLine();
//                            } while (mpline != null && mpline.indexOf(boundary) == -1);
//                        }
//
//                        if (value != null) {
//                            parms.put(pname, value);
//                        }
//                    }
//                }
//            } catch (IOException ioe) {
//                sendError(HTTP_INTERNALERROR, "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
//            }
//        }
//
//        /**
//         * Find byte index separating header from body.
//         * It must be the last byte of the first two sequential new lines.
//         **/
//        private int findHeaderEnd(final byte[] buf, int rlen) {
//            int splitbyte = 0;
//            while (splitbyte + 3 < rlen) {
//                if (buf[splitbyte] == '\r' && buf[splitbyte + 1] == '\n' && buf[splitbyte + 2] == '\r'
//                        && buf[splitbyte + 3] == '\n')
//                    return splitbyte + 4;
//                splitbyte++;
//            }
//            return 0;
//        }
//
//        /**
//         * Find the byte positions where multipart boundaries start.
//         **/
//        public int[] getBoundaryPositions(byte[] b, byte[] boundary) {
//            int matchcount = 0;
//            int matchbyte = -1;
//            Vector matchbytes = new Vector();
//            for (int i = 0; i < b.length; i++) {
//                if (b[i] == boundary[matchcount]) {
//                    if (matchcount == 0) {
//                        matchbyte = i;
//                    }
//                    matchcount++;
//                    if (matchcount == boundary.length) {
//                        matchbytes.addElement(new Integer(matchbyte));
//                        matchcount = 0;
//                        matchbyte = -1;
//                    }
//                } else {
//                    i -= matchcount;
//                    matchcount = 0;
//                    matchbyte = -1;
//                }
//            }
//
//            int[] ret = new int[matchbytes.size()];
//            for (int i = 0; i < ret.length; i++) {
//                ret[i] = ((Integer) matchbytes.elementAt(i)).intValue();
//            }
//            return ret;
//        }
//
//        /**
//         * Retrieves the content of a sent file and saves it
//         * to a temporary file.
//         * The full path to the saved file is returned.
//         **/
//        private String saveTmpFile(byte[] b, int offset, int len, String fname) {
//            File file = new File(CallSetting.TEMP_FILE_DIR);
//            if (!file.exists()) {
//                file.mkdirs();
//            }
//
//            String path = "";
//            if (len > 0) {
//                //String tmpdir = System.getProperty("java.io.tmpdir");
//                try {
//                    File temp = new File(new File(CallSetting.TEMP_FILE_DIR), fname);
//                    OutputStream os = new FileOutputStream(temp);
//                    os.write(b, offset, len);
//                    os.close();
//                    path = temp.getAbsolutePath();
//                } catch (Exception e) { // Catch exception if any
//                    e.printStackTrace();
//                }
//            }
//            return path;
//        }
//
//        /**
//         * It returns the offset separating multipart file headers
//         * from the file's data.
//         **/
//        private int stripMultipartHeaders(byte[] b, int offset) {
//            int i = 0;
//            for (i = offset; i < b.length; i++) {
//                if (b[i] == '\r' && b[++i] == '\n' && b[++i] == '\r' && b[++i] == '\n') {
//                    break;
//                }
//            }
//            return i + 1;
//        }
//
//        /**
//         * Decodes the percent encoding scheme. <br/>
//         * For example: "an+example%20string" -> "an example string"
//         */
//        private String decodePercent(String str) throws InterruptedException {
//            try {
//                StringBuffer sb = new StringBuffer();
//                for (int i = 0; i < str.length(); i++) {
//                    char c = str.charAt(i);
//                    switch (c) {
//                        case '+':
//                            sb.append(' ');
//                            break;
//                        case '%':
//                            sb.append((char) Integer.parseInt(str.substring(i + 1, i + 3), 16));
//                            i += 2;
//                            break;
//                        default:
//                            sb.append(c);
//                            break;
//                    }
//                }
//                return sb.toString();
//            } catch (Exception e) {
//                sendError(HTTP_BADREQUEST, "BAD REQUEST: Bad percent-encoding.");
//                return null;
//            }
//        }
//
//        /**
//         * Decodes parameters in percent-encoded URI-format
//         * ( e.g. "name=Jack%20Daniels&pass=Single%20Malt" ) and
//         * adds them to given Properties. NOTE: this doesn't support multiple
//         * identical keys due to the simplicity of Properties -- if you need multiples,
//         * you might want to replace the Properties with a Hashtable of Vectors or such.
//         */
//        private void decodeParms(String parms, Properties p) throws InterruptedException {
//            if (parms == null) {
//                return;
//            }
//
//            StringTokenizer st = new StringTokenizer(parms, "&");
//            while (st.hasMoreTokens()) {
//                String e = st.nextToken();
//                int sep = e.indexOf('=');
//                if (sep >= 0) {
//                    p.put(decodePercent(e.substring(0, sep)).trim(), decodePercent(e.substring(sep + 1)));
//                }
//            }
//        }
//
//        /**
//         * Returns an error message as a HTTP response and
//         * throws InterruptedException to stop further request processing.
//         */
//        private void sendError(String status, String msg) throws InterruptedException {
//            sendResponse(status, MIME_PLAINTEXT, null, new ByteArrayInputStream(msg.getBytes()));
//            throw new InterruptedException();
//        }
//
//        /**
//         * Sends given response to the socket.
//         */
//        private void sendResponse(String status, String mime, Properties header, InputStream data) {
//            //if (DEBUG) Log.d(TAG, "sendResponse status : " + status + " mime : " + mime + " header : " + header + " data : " + data);
//            try {
//                if (status == null) {
//                    throw new Error("sendResponse(): Status can't be null.");
//                }
//
//                OutputStream out = mySocket.getOutputStream();
//                PrintWriter pw = new PrintWriter(out);
//                pw.print("HTTP/1.0 " + status + " \r\n");
//
//                if (mime != null) {
//                    pw.print("Content-Type: " + mime + "\r\n");
//                }
//                if (header == null || header.getProperty("Date") == null) {
//                    pw.print("Date: " + gmtFrmt.format(new Date()) + "\r\n");
//                }
//                if (header != null) {
//                    Enumeration e = header.keys();
//                    while (e.hasMoreElements()) {
//                        String key = (String) e.nextElement();
//                        String value = header.getProperty(key);
//                        pw.print(key + ": " + value + "\r\n");
//                    }
//                }
//
//                pw.print("\r\n");
//                pw.flush();
//
//                if (data != null) {
//                    int pending = data.available(); // This is to support partial sends, see serveFile()
//                    byte[] buff = new byte[theBufferSize];
//                    while (pending > 0) {
//                        int read = data.read(buff, 0, ((pending > theBufferSize) ? theBufferSize : pending));
//                        if (read <= 0) {
//                            break;
//                        }
//                        out.write(buff, 0, read);
//                        pending -= read;
//                    }
//                }
//
//                out.flush();
//                out.close();
//
//                if (data != null) {
//                    data.close();
//                }
//            } catch (IOException ioe) {
//                // Couldn't write? No can do.
//                try {
//                    mySocket.close();
//                } catch (Throwable t) {
//                }
//            }
//        }
//
//    }
//
//    /**
//     * URL-encodes everything between "/"-characters.
//     * Encodes spaces as '%20' instead of '+'.
//     */
//    private String encodeUri(String uri) {
//        String newUri = "";
//        StringTokenizer st = new StringTokenizer(uri, "/ ", true);
//        while (st.hasMoreTokens()) {
//            String tok = st.nextToken();
//            if (tok.equals("/")) {
//                newUri += "/";
//            } else if (tok.equals(" ")) {
//                newUri += "%20";
//            } else {
//                newUri += URLEncoder.encode(tok);
//                // For Java 1.4 you'll want to use this instead:
//                // try { newUri += URLEncoder.encode( tok, "UTF-8" ); } catch ( java.io.UnsupportedEncodingException uee ) {}
//            }
//        }
//        return newUri;
//    }
//
//    private SSLServerSocketFactory sslServerSocketFactory;
//    private int myTcpPort;
//    private final ServerSocket myServerSocket;
//    private Thread myThread;
//    private File myRootDir;
//
//    // ==================================================
//    // File server code
//    // ==================================================
//
//    /**
//     * Serves file from homeDir and its' subdirectories (only).
//     * Uses only URI, ignores all headers and HTTP parameters.
//     */
//    public Response serveFile(String uri, Properties header, File homeDir, boolean allowDirectoryListing) {
//        if (DEBUG)
//            Log.d(TAG, "serveFile - start");
//        Response res = null;
//
//        // Make sure we won't die of an exception later
//        if (!homeDir.isDirectory()) {
//            if (DEBUG)
//                Log.d(TAG, "serveFile - 1 ");
//            res = new Response(HTTP_INTERNALERROR, MIME_PLAINTEXT,
//                    "INTERNAL ERRROR: serveFile(): given homeDir is not a directory.");
//        }
//
//        if (res == null) {
//            if (DEBUG)
//                Log.d(TAG, "serveFile - 2 ");
//            // Remove URL arguments
//            uri = uri.trim().replace(File.separatorChar, '/');
//            if (uri.indexOf('?') >= 0) {
//                uri = uri.substring(0, uri.indexOf('?'));
//            }
//            // Prohibit getting out of current directory
//            if (uri.startsWith("..") || uri.endsWith("..") || uri.indexOf("../") >= 0) {
//                res = new Response(HTTP_FORBIDDEN, MIME_PLAINTEXT, "FORBIDDEN: Won't serve ../ for security reasons.");
//            }
//        }
//
//        if (DEBUG)
//            Log.d(TAG, "serveFile uri : " + uri + " homeDir : " + homeDir + " res : " + res);
//
//        File f = new File(homeDir, uri);
//        if (res == null && !f.exists()) {
//            if (DEBUG)
//                Log.d(TAG, "serveFile - 4 ");
//            res = new Response(HTTP_NOTFOUND, MIME_PLAINTEXT, "Error 404, file not found.");
//        }
//
//        // List the directory, if necessary
//        if (res == null && f.isDirectory()) {
//            if (DEBUG)
//                Log.d(TAG, "serveFile - 5 ");
//            // Browsers get confused without '/' after the
//            // directory, send a redirect.
//            if (!uri.endsWith("/")) {
//                uri += "/";
//                res = new Response(HTTP_REDIRECT, MIME_HTML, "<html><body>Redirected: <a href=\"" + uri + "\">" + uri
//                        + "</a></body></html>");
//                res.addHeader("Location", uri);
//            }
//            if (res == null) {
//                // First try index.html and index.htm
//                if (new File(f, "index.html").exists()) {
//                    f = new File(homeDir, uri + "/index.html");
//                } else if (new File(f, "index.htm").exists()) {
//                    f = new File(homeDir, uri + "/index.htm");
//                } else if (allowDirectoryListing && f.canRead()) { // No index file, list the directory if it is readable
//                    String[] files = f.list();
//                    String msg = "<html><body><h1>Directory " + uri + "</h1><br/>";
//                    if (uri.length() > 1) {
//                        String u = uri.substring(0, uri.length() - 1);
//                        int slash = u.lastIndexOf('/');
//                        if (slash >= 0 && slash < u.length()) {
//                            msg += "<b><a href=\"" + uri.substring(0, slash + 1) + "\">..</a></b><br/>";
//                        }
//                    }
//                    if (files != null) {
//                        for (int i = 0; i < files.length; ++i) {
//                            File curFile = new File(f, files[i]);
//                            boolean dir = curFile.isDirectory();
//                            if (dir) {
//                                msg += "<b>";
//                                files[i] += "/";
//                            }
//                            msg += "<a href=\"" + encodeUri(uri + files[i]) + "\">" + files[i] + "</a>";
//
//                            // Show file size
//                            if (curFile.isFile()) {
//                                long len = curFile.length();
//                                msg += " &nbsp;<font size=2>(";
//                                if (len < 1024) {
//                                    msg += len + " bytes";
//                                } else if (len < 1024 * 1024) {
//                                    msg += len / 1024 + "." + (len % 1024 / 10 % 100) + " KB";
//                                } else {
//                                    msg += len / (1024 * 1024) + "." + len % (1024 * 1024) / 10 % 100 + " MB";
//                                }
//                                msg += ")</font>";
//                            }
//                            msg += "<br/>";
//                            if (dir) {
//                                msg += "</b>";
//                            }
//                        }
//                    }
//                    msg += "</body></html>";
//                    res = new Response(HTTP_OK, MIME_HTML, msg);
//                } else {
//                    res = new Response(HTTP_FORBIDDEN, MIME_PLAINTEXT, "FORBIDDEN: No directory listing.");
//                }
//            }
//        }
//
//        try {
//            if (res == null) {
//                // Get MIME type from file name extension, if possible
//                String mime = null;
//                int dot = f.getCanonicalPath().lastIndexOf('.');
//                if (dot >= 0) {
//                    mime = (String) theMimeTypes.get(f.getCanonicalPath().substring(dot + 1).toLowerCase());
//                }
//                if (mime == null) {
//                    mime = MIME_DEFAULT_BINARY;
//                }
//
//                // Calculate etag
//                String etag = Integer.toHexString((f.getAbsolutePath() + f.lastModified() + "" + f.length()).hashCode());
//
//                // Support (simple) skipping:
//                long startFrom = 0;
//                long endAt = -1;
//                String range = header.getProperty("range");
//                if (range != null) {
//                    if (range.startsWith("bytes=")) {
//                        range = range.substring("bytes=".length());
//                        int minus = range.indexOf('-');
//                        try {
//                            if (minus > 0) {
//                                startFrom = Long.parseLong(range.substring(0, minus));
//                                endAt = Long.parseLong(range.substring(minus + 1));
//                            }
//                        } catch (NumberFormatException nfe) {
//                        }
//                    }
//                }
//
//                // Change return code and add Content-Range header when skipping is requested
//                long fileLen = f.length();
//                if (range != null && startFrom >= 0) {
//                    if (startFrom >= fileLen) {
//                        res = new Response(HTTP_RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "");
//                        res.addHeader("Content-Range", "bytes 0-0/" + fileLen);
//                        res.addHeader("ETag", etag);
//                    } else {
//                        if (endAt < 0) {
//                            endAt = fileLen - 1;
//                        }
//                        long newLen = endAt - startFrom + 1;
//                        if (newLen < 0) {
//                            newLen = 0;
//                        }
//                        final long dataLen = newLen;
//                        FileInputStream fis = new FileInputStream(f) {
//                            public int available() throws IOException {
//                                return (int) dataLen;
//                            }
//                        };
//                        fis.skip(startFrom);
//
//                        res = new Response(HTTP_PARTIALCONTENT, mime, fis);
//                        res.addHeader("Content-Length", "" + dataLen);
//                        res.addHeader("Content-Range", "bytes " + startFrom + "-" + endAt + "/" + fileLen);
//                        res.addHeader("ETag", etag);
//                    }
//                } else {
//                    if (etag.equals(header.getProperty("if-none-match"))) {
//                        res = new Response(HTTP_NOTMODIFIED, mime, "");
//                    } else {
//                        res = new Response(HTTP_OK, mime, new FileInputStream(f));
//                        res.addHeader("Content-Length", "" + fileLen);
//                        res.addHeader("ETag", etag);
//                    }
//                }
//            }
//        } catch (IOException ioe) {
//            res = new Response(HTTP_FORBIDDEN, MIME_PLAINTEXT, "FORBIDDEN: Reading file failed.");
//        }
//
//        res.addHeader("Accept-Ranges", "bytes"); // Announce that the file server accepts partial content requestes
//        return res;
//    }
//
//    /**
//     * Hashtable mapping (String)FILENAME_EXTENSION -> (String)MIME_TYPE
//     */
//    public static Hashtable theMimeTypes = new Hashtable();
//    static {
//        StringTokenizer st = new StringTokenizer("css       text/css " + "htm       text/html " + "html     text/html " + "xml      text/xml "
//                + "txt      text/plain " + "asc     text/plain " + "gif     image/gif " + "jpg      image/jpeg " + "jpeg        image/jpeg "
//                + "png      image/png " + "mp3      audio/mpeg " + "m3u     audio/mpeg-url " + "mp4     video/mp4 " + "ogv      video/ogg "
//                + "flv      video/x-flv " + "mov        video/quicktime " + "swf        application/x-shockwave-flash "
//                + "js           application/javascript " + "pdf     application/pdf " + "doc        application/msword "
//                + "ogg      application/x-ogg " + "zip      application/octet-stream " + "exe       application/octet-stream "
//                + "class        application/octet-stream ");
//        while (st.hasMoreTokens()) {
//            theMimeTypes.put(st.nextToken(), st.nextToken());
//        }
//    }
//
//    private static int theBufferSize = 16 * 1024;
//
//    // Change these if you want to log to somewhere else than stdout
//    protected static PrintStream myOut = System.out;
//    protected static PrintStream myErr = System.err;
//
//    /**
//     * GMT date formatter
//     */
//    private static java.text.SimpleDateFormat gmtFrmt;
//    static {
//        gmtFrmt = new java.text.SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
//        gmtFrmt.setTimeZone(TimeZone.getTimeZone("GMT"));
//    }
//
//    //by jsh 2014-06-19 , Add upload certificate
//    private byte[] fileToByteArray(String path) {
//        File file = new File(path);
//        int size = (int) file.length();
//        byte[] bytes = new byte[size];
//        try {
//            BufferedInputStream buf = new BufferedInputStream(new FileInputStream(file));
//            buf.read(bytes, 0, bytes.length);
//            buf.close();
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//        return bytes;
//    }
//    //end by jsh 2014-06-19 , Add upload certificate
//
//    private void setDefaultWav(String name) {
//        String fileName = "";
//        if( name.equals("korDefault") ) {
//            fileName = "CCTV_KOR.wav";
//        } else if( name.equals("engDefault") ) {
//            fileName = "CCTV_ENG.wav";
//        } else if( name.equals("thaiDefault") ) {
//            fileName = "CCTV_THAI.wav";
//        } else if( name.equals("endDefault") ) {
//            fileName = "";
//        } else if( name.equals("errorDefault") ) {
//            fileName = "";
//        } else if( name.equals("dooropenDefault") ) {
//            fileName = "DOOROPEN_MESSAGE.wav";
//        }
//
//        File file = new File(CallSetting.TEMP_FILE_DIR + fileName);
//        if ( file.exists() ) {
//            file.delete();
//        }
//    }
//
//
//
//    // lch start 2024-10-29 add ssl
//    public static SSLServerSocketFactory createSSLServerSocketFactory(KeyStore keyStore, String password) {
//        try {
//            // KeyManager 설정
//            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
//            kmf.init(keyStore, password.toCharArray());
//
//            // TrustManager 설정
//            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
//            tmf.init(keyStore);
//
//            // SSL 컨텍스트 생성 및 초기화
//            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
//            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
//
//            // SSL 서버 소켓 팩토리 생성
//            SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();
//
//            return ssf;
//        } catch (Exception e) {
//            Log.e("createSSLServerSocketFactory", "SSL server socket factory creation failed", e);
//            return null;
//        }
//    }
//
//    public static SSLServerSocketFactory createServerSocketFactory(KeyStore keyStore, String password) throws IOException {
//        SSLServerSocketFactory ssf = createSSLServerSocketFactory(keyStore, password);
//        if (ssf == null) {
//            throw new IOException("Failed to create SSL server socket factory");
//        }
//        return ssf;
//    }
//
//// lch end 2024-10-29 add ssl
//
//    // lch std 2024-04-07 add server
//    public static SSLServerSocketFactory createServerSocketFactoryFromPEM(Context context) throws Exception {
//        // Load certificate
//        InputStream certStream = context.getAssets().open("hjict_server_cert.pem");
//        CertificateFactory cf = CertificateFactory.getInstance("X.509");
//        Certificate cert = cf.generateCertificate(certStream);
//
//        // Load and parse private key
//        InputStream keyStream = context.getAssets().open("hjict_server_key.pem");
//        StringBuilder keyBuilder = new StringBuilder();
//        try (BufferedReader br = new BufferedReader(new InputStreamReader(keyStream, Charset.forName("UTF-8")))) {
//            String line;
//            while ((line = br.readLine()) != null) {
//                if (line.contains("BEGIN") || line.contains("END")) continue;
//                keyBuilder.append(line.trim());
//            }
//        }
//
//        String keyPemCleaned = keyBuilder.toString();
//        byte[] decoded = Base64.getDecoder().decode(keyPemCleaned);
//        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
//        KeyFactory kf = KeyFactory.getInstance("RSA");
//        PrivateKey key = kf.generatePrivate(spec);
//
//        // // Android에서 지원되는 PKCS12 키스토어 사용
//        KeyStore ks = KeyStore.getInstance("PKCS12");
//        ks.load(null, null);
//        ks.setKeyEntry("server", key, "password".toCharArray(), new Certificate[]{cert});
//
//        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
//        kmf.init(ks, "password".toCharArray());
//
//        // // SSLContext sslContext = SSLContext.getInstance("TLS");
//        // SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
//        // sslContext.init(kmf.getKeyManagers(), null, new SecureRandom());
//
//        // return sslContext.getServerSocketFactory();
//        // 클라이언트 인증서 신뢰용 키스토어 로드 (client CA 인증서)
//        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
//        InputStream caInput = context.getAssets().open("hjict_client_ca.pem");
//        Certificate caCert = cf.generateCertificate(caInput);
//        trustStore.load(null, null);
//        trustStore.setCertificateEntry("client-ca", caCert);
//
//        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
//        tmf.init(trustStore);
//
//        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
//        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
//
//        // SSLServerSocketFactory 생성 후, Client Auth 설정
//        SSLServerSocketFactory factory = sslContext.getServerSocketFactory();
//
//        // 이 factory는 SSLServerSocket을 리턴하게 되므로, 나중에 setNeedClientAuth 해줘야 함
//        return new SSLServerSocketFactoryWrapper(factory);
//    }
//// lch end 2024-04-07 add server
//
//
//    public static class SSLServerSocketFactoryWrapper extends SSLServerSocketFactory {
//        private final SSLServerSocketFactory wrapped;
//
//        public SSLServerSocketFactoryWrapper(SSLServerSocketFactory wrapped) {
//            this.wrapped = wrapped;
//        }
//
//        @Override
//        public ServerSocket createServerSocket(int port) throws IOException {
//            SSLServerSocket socket = (SSLServerSocket) wrapped.createServerSocket(port);
//            socket.setNeedClientAuth(true);  // 클라이언트 인증 필수
//            return socket;
//        }
//
//        @Override
//        public String[] getDefaultCipherSuites() {
//            return wrapped.getDefaultCipherSuites();
//        }
//
//        @Override
//        public String[] getSupportedCipherSuites() {
//            return wrapped.getSupportedCipherSuites();
//        }
//
//        @Override
//        public ServerSocket createServerSocket(int port, int backlog) throws IOException {
//            SSLServerSocket socket = (SSLServerSocket) wrapped.createServerSocket(port, backlog);
//            socket.setNeedClientAuth(true);
//            return socket;
//        }
//
//        @Override
//        public ServerSocket createServerSocket(int port, int backlog, InetAddress ifAddress) throws IOException {
//            SSLServerSocket socket = (SSLServerSocket) wrapped.createServerSocket(port, backlog, ifAddress);
//            socket.setNeedClientAuth(true);
//            return socket;
//        }
//    }
//
//
//
//
//}