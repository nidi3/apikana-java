package org.swisspush.apikana;

import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.*;
import org.eclipse.jetty.util.FutureCallback;
import org.eclipse.jetty.util.UrlEncoded;
import org.eclipse.jetty.util.resource.Resource;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.*;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

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
        final HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[]{
                new SourcesHandler(),
                new RootResourceHandler(),
                new ShutdownHandler("666", true, true),
                new DefaultHandler()});
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
            return path == null || !path.startsWith("/") ? null : Resource.newClassPathResource(path);
        }
    }

    static class SourcesHandler extends AbstractHandler {
        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
            if (baseRequest.isHandled()) {
                return;
            }

            if (target.length() == 1) {
                response.sendRedirect("/ui/index.html?url=" + (hasApi() ? "/model/openapi/api.yaml" : "/model"));
                baseRequest.setHandled(true);
            }

            if ("/model".equals(target)) {
                List<String> sources = sources(getClass().getClassLoader().getResource("model/ts"));
                response.getWriter().println("{definitions: {$ref: " + sources + "}}");
                response.flushBuffer();
                baseRequest.setHandled(true);
            }
        }

        private boolean hasApi() {
            return getClass().getClassLoader().getResource("model/openapi/api.yaml") != null;
        }

        private List<String> sources(URL url) throws IOException {
            if (url == null) {
                return new ArrayList<>();
            }
            String file = url.getFile();
            if (!file.startsWith("file:/")) {
                throw new RuntimeException("Expected to run inside jar.");
            }
            List<String> res = new ArrayList<>();
            int sep = file.indexOf("!");
            try (JarFile jar = new JarFile(file.substring(5, sep))) {
                String path = file.substring(sep + 2).replace("\\", "/");
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!entry.isDirectory() && name.startsWith(path) && !name.contains("/node_modules/")) {
                        res.add(name);
                    }
                }
                return res;
            }
        }
    }
}
