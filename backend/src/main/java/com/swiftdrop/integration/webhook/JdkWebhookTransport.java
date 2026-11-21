package com.swiftdrop.integration.webhook;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import javax.net.ssl.*;

/** HTTP/1.1 callback transport. Connects to a validated IP, preserving TLS hostname verification. */
public final class JdkWebhookTransport implements WebhookTransport {
    private final int connectTimeoutMs;
    public JdkWebhookTransport(Duration connectTimeout) { this.connectTimeoutMs=Math.toIntExact(connectTimeout.toMillis()); }
    @Override public Response post(URI endpoint,Map<String,String> headers,byte[] body) throws IOException {
        String host=endpoint.getHost();
        boolean tls="https".equalsIgnoreCase(endpoint.getScheme());
        boolean local=!tls && Set.of("localhost","127.0.0.1","[::1]","::1").contains(host.toLowerCase(Locale.ROOT));
        var addresses=InetAddress.getAllByName(host);
        if(addresses.length==0) throw new IOException("DESTINATION_UNAVAILABLE");
        // Reject mixed public/private DNS answers, and pin the connection to this validated answer.
        for(var address:addresses) if(local?!address.isLoopbackAddress():!isPublicAddress(address)) throw new BlockedWebhookDestinationException();
        int port=endpoint.getPort()>0?endpoint.getPort():tls?443:80;
        Socket connection=new Socket();
        try {
            connection.connect(new InetSocketAddress(addresses[0],port),connectTimeoutMs);
            connection.setSoTimeout(10000);
            if(tls) {
                var secure=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(connection,host,port,true);
                var parameters=secure.getSSLParameters(); parameters.setEndpointIdentificationAlgorithm("HTTPS");
                parameters.setServerNames(List.of(new SNIHostName(host))); secure.setSSLParameters(parameters); secure.setSoTimeout(10000);
                secure.startHandshake(); connection=secure;
            } else if(!local) throw new BlockedWebhookDestinationException();
            var path=endpoint.getRawPath(); if(path==null||path.isEmpty()) path="/";
            if(endpoint.getRawQuery()!=null) path+="?"+endpoint.getRawQuery();
            StringBuilder request=new StringBuilder("POST "+path+" HTTP/1.1\r\nHost: "+host+(endpoint.getPort()>0?":"+port:"")+"\r\nConnection: close\r\nContent-Length: "+body.length+"\r\n");
            for(var header:headers.entrySet()) {
                if(!header.getKey().matches("[A-Za-z0-9-]+")||header.getValue().contains("\r")||header.getValue().contains("\n")) throw new IOException("INVALID_HEADER");
                request.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
            }
            var output=connection.getOutputStream(); output.write(request.append("\r\n").toString().getBytes(StandardCharsets.US_ASCII)); output.write(body); output.flush();
            var input=connection.getInputStream(); long deadline=System.nanoTime()+Duration.ofSeconds(10).toNanos();
            for(int interim=0;interim<4;interim++) {
                int[] count={0}; String status=line(input,connection,deadline,count);
                if(!status.matches("HTTP/1\\.[01] [1-5][0-9]{2}(?: .*)?")) throw new IOException("INVALID_HTTP_RESPONSE");
                int code=Integer.parseInt(status.substring(9,12));
                while(!line(input,connection,deadline,count).isEmpty()) { }
                if(code>=200) return new Response(code,new byte[0]); // Status is sufficient; never consume an untrusted response body.
            }
            throw new IOException("INVALID_HTTP_RESPONSE");
        } finally { connection.close(); }
    }
    private static String line(InputStream input,Socket connection,long deadline,int[] count) throws IOException {
        var bytes=new ByteArrayOutputStream();
        while(true) {
            long remaining=deadline-System.nanoTime(); if(remaining<=0) throw new SocketTimeoutException("RESPONSE_TIMEOUT");
            connection.setSoTimeout((int)Math.max(1,Math.min(10000,remaining/1000000)));
            int value=input.read(); if(value<0) throw new EOFException("INCOMPLETE_RESPONSE");
            if(++count[0]>16384) throw new IOException("RESPONSE_HEADERS_TOO_LARGE");
            if(value=='\n') break; bytes.write(value);
        }
        byte[] value=bytes.toByteArray(); if(value.length==0||value[value.length-1]!='\r') throw new IOException("INVALID_HTTP_RESPONSE");
        return new String(value,0,value.length-1,StandardCharsets.US_ASCII);
    }
    public static boolean isPublicAddress(InetAddress address) {
        if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress()||address.isMulticastAddress()) return false;
        byte[] bytes=address.getAddress();
        if(bytes.length==16) return (bytes[0]&0xe0)==0x20; // Global IPv6 unicast only (2000::/3).
        int first=bytes[0]&255,second=bytes[1]&255;
        return first!=0&&first!=10&&first!=127&&first<224&&!(first==100&&second>=64&&second<=127)
            &&!(first==169&&second==254)&&!(first==172&&second>=16&&second<=31)&&!(first==192&&(second==168||second==0))
            &&!(first==198&&(second==18||second==19));
    }
}
