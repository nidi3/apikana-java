package org.swisspush.apikana;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Stop a possibly running HTTP server.
 */
@Mojo(name = "stop-server", defaultPhase = LifecyclePhase.PRE_CLEAN, requiresProject = false)
public class StopServerMojo extends AbstractMojo {
    /**
     * The port of the HTTP server.
     */
    @Parameter(defaultValue = "8333", property = "apikana.port")
    private int port;

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            final URL url = new URL("http://127.0.0.1:" + port + "/close");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.getInputStream();
            con.disconnect();
        } catch (IOException e) {
            //ignore
        }
    }
}