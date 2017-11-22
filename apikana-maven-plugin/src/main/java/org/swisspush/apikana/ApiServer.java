package org.swisspush.apikana;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.handler.ShutdownHandler;
import org.eclipse.jetty.util.FutureCallback;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.UrlEncoded;
import org.eclipse.jetty.util.resource.Resource;

import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.file.Path;

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
        final ResourceHandler uiResource = new ResourceHandler();
        uiResource.setBaseResource(Resource.newClassPathResource("/ui"));
        final ResourceHandler srcResource = new PathResourceHandler("/sources/a/b/c/d", "/model/openapi");
        final ShutdownHandler shutdown = new ShutdownHandler("666", true, true);

        final HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[]{uiResource, srcResource, shutdown, new DefaultHandler()});
        return handlers;
    }

    private static void preloadClasses() throws ClassNotFoundException {
        //preload classes needed for shutdown, they can be unavailable when jar file has changed while server ran
        UrlEncoded.class.toString();
        FutureCallback.class.toString();
        Class.forName("org.eclipse.jetty.server.handler.ShutdownHandler$1");
        Class.forName("org.eclipse.jetty.io.ManagedSelector$CloseEndPoints");
    }

    static class PathResourceHandler extends ResourceHandler {
        private final Path prefix;
        private final String target;
        private final String minimal;

        public PathResourceHandler(String prefix, String target) {
            this.prefix = new File(prefix).toPath();
            this.target = target;
            minimal = prefix.substring(0, prefix.indexOf('/', 1) + 1);
        }

        @Override
        public Resource getResource(String path) {
            if (path == null || !path.startsWith(minimal)) {
                return null;
            }
            String resource = URIUtil.canonicalPath(target + "/" + prefix.relativize(new File(path).toPath()).toString().replace('\\', '/'));
            return Resource.newClassPathResource(resource);
        }
    }
}
