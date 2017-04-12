package org.swisspush.apikana;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.element;

/**
 * Generate JSON schemas and a user documentation in HTML from the given swagger and typescript models.
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE)
public class GenerateMojo extends AbstractGenerateMojo {
    private static class Version {
        static final String APIKANA = "0.1.9";
    }

    /**
     * The node version to be used.
     */
    @Parameter(defaultValue = "v7.5.0", property = "apikana.node-version")
    private String nodeVersion;

    /**
     * The npm version to be used.
     */
    @Parameter(defaultValue = "4.2.0", property = "apikana.npm-version")
    private String npmVersion;

    /**
     * The url to download npm and node from.
     */
    @Parameter(property = "apikana.download-root")
    private String downloadRoot;

    /**
     * The directory with the models and apis.
     */
    @Parameter(defaultValue = "src", property = "apikana.input")
    private String input;

    /**
     * The directory with the generated artifacts.
     */
    @Parameter(defaultValue = "target/api", property = "apikana.output")
    private String output;

    /**
     * The java package that should be used.
     */
    @Parameter(property = "apikana.java-package")
    private String javaPackage;

    /**
     * If the sources should be copied into the output directory.
     */
    @Parameter(defaultValue = "false", property = "apikana.deploy")
    private boolean deploy;

    /**
     * The port of the HTTP server.
     */
    @Parameter(defaultValue = "8333", property = "apikana.port")
    private int port;

    /**
     * If the API should be published via HTTP.
     */
    @Parameter(defaultValue = "true", property = "apikana.serve")
    private boolean serve;

    /**
     * If the browser should be opened to show the API.
     */
    @Parameter(defaultValue = "true", property = "apikana.open-browser")
    private boolean openBrowser;

    /**
     * If the globally installed apikana node package should be used.
     */
    @Parameter(defaultValue = "true", property = "apikana.global")
    private boolean global;

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            if (!handlePomPackaging()) {
                unpackStyleDependencies(mavenProject.getParent());
                unpackModelDependencies();
                writeProjectProps();
                if (global) {
                    checkNodeInstalled();
                } else {
                    installNode();
                    generatePackageJson(Version.APIKANA);
                    installApikana();
                }
                deleteGeneratedClasses();
                runApikana();
                mavenProject.addCompileSourceRoot(file(output + "/model/java").getAbsolutePath());
                projectHelper.addResource(mavenProject, file(input).getAbsolutePath(), Arrays.asList("model/**/*.ts"), null);
                projectHelper.addResource(mavenProject, file(output).getAbsolutePath(), Arrays.asList("model/**/*.json"), null);

                projectHelper.attachArtifact(mavenProject, createApiJar(input, output), "api");
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Problem running apikana", e);
        }
    }

    private void deleteGeneratedClasses() throws IOException {
        //for some reason, jsonschema2pojo does not generate .java when .class already exists
        final File outDir = new File(mavenProject.getBuild().getOutputDirectory() + "/" + javaPackage().replace('.', '/'));
        if (outDir.exists()) {
            FileUtils.cleanDirectory(outDir);
        }
    }

    private void installNode() throws MojoExecutionException {
        executeFrontend("install-node-and-npm", configuration(
                element("downloadRoot", downloadRoot),
                element("nodeVersion", nodeVersion),
                element("npmVersion", npmVersion)
        ));
    }

    private void installApikana() throws IOException, MojoExecutionException {
        final File apikanaPackage = working("node_modules/apikana/package.json");
        if (apikanaPackage.exists()) {
            Map pack = new ObjectMapper().readValue(apikanaPackage, Map.class);
            final String version = (String) pack.get("version");
            if (Version.APIKANA.equals(version)) {
                getLog().info("apikana " + Version.APIKANA + " already installed.");
                return;
            }
        }
        executeFrontend("npm", configuration(element("arguments", "install")));
    }

    private void runApikana() throws Exception {
        final List<String> cmd = Arrays.asList("apikana start",
                relative(working(""), file(input)),
                relative(working(""), file(output)),
                global ? "" : "--",
                "--javaPackage=" + javaPackage(),
                "--deploy=" + deploy,
                "--port=" + port,
                "--serve=" + serve,
                "--openBrowser=" + openBrowser,
                "--config=properties.json",
                "--dependencyPath=" + relative(working(""), apiDependencies("")));
        final String cmdLine = cmd.stream().collect(Collectors.joining(" "));
        if (global) {
            final Process apikana = shellCommand(working(""), cmdLine).inheritIO().start();
            if (apikana.waitFor() != 0) {
                throw new IOException();
            }
        } else {
            executeFrontend("npm", configuration(element("arguments", "run " + cmdLine)));
        }
    }

    private String javaPackage() {
        String artifactId = mavenProject.getArtifactId();
        int point = artifactId.indexOf('-');
        if (point > 0) {
            artifactId = artifactId.substring(point + 1);
        }
        artifactId = artifactId.replace("-", ".");
        return javaPackage != null ? javaPackage : (mavenProject.getGroupId() + "." + artifactId);
    }

}
