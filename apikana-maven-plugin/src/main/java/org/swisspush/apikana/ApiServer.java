package org.swisspush.apikana;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.handler.ShutdownHandler;
import org.eclipse.jetty.util.FutureCallback;
import org.eclipse.jetty.util.UrlEncoded;
import org.eclipse.jetty.util.resource.Resource;

import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;

public class ApiServer {
    private static final int PORT = 8334;
    private static PrintWriter out;

    public static void main(String[] args) throws Exception {
        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File("log.txt"))))) {
            out = w;
            try {
                preloadClasses();

                final Server server = new Server(PORT);
                server.setHandler(createHandlers());
                server.start();
                Desktop.getDesktop().browse(new URI("http://localhost:" + PORT));
                server.join();
            } catch (Throwable e) {
                e.printStackTrace(out);
            }
        }
    }

    private static HandlerList createHandlers() {
        final ResourceHandler resource = new RootResourceHandler();
        final ShutdownHandler shutdown = new ShutdownHandler("666", true, true);

        final HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[]{resource, shutdown, new DefaultHandler()});
        return handlers;
    }

    private static void preloadClasses() throws ClassNotFoundException {
        //preload classes needed for shutdown, they can be unavailable when jar file has changed while server ran
        UrlEncoded.class.toString();
        FutureCallback.class.toString();
        Class.forName("org.eclipse.jetty.server.handler.ShutdownHandler$1");
        Class.forName("org.eclipse.jetty.io.ManagedSelector$CloseEndPoints");
    }

    static class RootResourceHandler extends ResourceHandler {
        @Override
        public Resource getResource(String path) {
            if (path == null || !path.startsWith("/")) {
                return null;
            }
            if (path.length() == 1) {
                path = "/index.html";
            }
            return Resource.newClassPathResource(path);
        }
    }
}
